package com.lkd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.lkd.common.VMSystem;
import com.lkd.config.TopicConfig;
import com.lkd.contract.*;
import com.lkd.contract.server.SupplyTask;
import com.lkd.dao.VendingMachineDao;
import com.lkd.dto.VmInfoDTO;
import com.lkd.emq.MqttProducer;
import com.lkd.entity.*;
import com.lkd.exception.LogicException;
import com.lkd.feignService.UserService;
import com.lkd.http.viewModel.CreateVMReq;
import com.lkd.service.*;
import com.lkd.utils.JsonUtil;
import com.lkd.viewmodel.Pager;
import com.lkd.viewmodel.SkuViewModel;
import com.lkd.viewmodel.VMDistance;
import com.lkd.viewmodel.VmSearch;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.statement.select.Select;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.geo.GeoDistance;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.GeoDistanceQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.GeoDistanceSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VendingMachineServiceImpl extends ServiceImpl<VendingMachineDao, VendingMachineEntity> implements VendingMachineService {
    @Autowired
    private RestHighLevelClient esClient;
    @Autowired
    private VendoutRunningService vendoutRunningService;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private VmCfgVersionService versionService;

    @Autowired
    private UserService userService;

    @Autowired
    private SkuService skuService;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private VmTypeService vmTypeService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private MqttProducer mqttProducer;

//    @Autowired
//    private Sender sender;


    @Override
    public List<VmInfoDTO> search(VmSearch vmSearch) {

        List<VmInfoDTO> list = Lists.newArrayList();

        //指定索引
        SearchRequest searchRequest = new SearchRequest("vm");
        SearchSourceBuilder searchRequestBuilder = new SearchSourceBuilder();

        //中心点及半径
        GeoDistanceQueryBuilder geoDistanceQueryBuilder = new GeoDistanceQueryBuilder("location");
        geoDistanceQueryBuilder.distance(vmSearch.getDistance(), DistanceUnit.KILOMETERS);
        geoDistanceQueryBuilder.point(vmSearch.getLat(), vmSearch.getLon());

        //由远到近规则排序
        GeoDistanceSortBuilder distanceSortBuilder = new GeoDistanceSortBuilder("location", vmSearch.getLat(), vmSearch.getLon());
        distanceSortBuilder.unit(DistanceUnit.KILOMETERS);
        distanceSortBuilder.order(SortOrder.ASC);
        distanceSortBuilder.geoDistance(GeoDistance.ARC);

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();

        boolQueryBuilder.must(geoDistanceQueryBuilder);
        searchRequestBuilder.sort(distanceSortBuilder);
        searchRequestBuilder.query(boolQueryBuilder);
        searchRequest.source(searchRequestBuilder);

        try {
            SearchResponse search = esClient.search(searchRequest, RequestOptions.DEFAULT);
            SearchHits hits = search.getHits();
            for (SearchHit hit : hits.getHits()) {
                VmInfoDTO vmInfoDTO = JsonUtil.getByJson(hit.getSourceAsString(), VmInfoDTO.class);
                Object[] sortValues = hit.getSortValues();
                for (Object sortValue : sortValues) {
                    System.out.println(sortValue);
                }
                //将千米转换为米
                BigDecimal geo = new BigDecimal((double) (hit.getSortValues()[0]) * 1000);
                vmInfoDTO.setDistance(geo.intValue());
                list.add(vmInfoDTO);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        return list;
    }

    @Override
    public boolean hasCapacity(String innerCode, Long skuId) {
        LambdaQueryWrapper<ChannelEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(ChannelEntity::getInnerCode, innerCode)
                .eq(ChannelEntity::getSkuId, skuId)
                .gt(ChannelEntity::getCurrentCapacity, 0);


        return channelService.count(qw) > 0;
    }

    @Override
    public void sendSupplyTask(VendingMachineEntity vendingMachine) {
        //查询售货机货道的货到列表
        QueryWrapper<ChannelEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("vm_id", vendingMachine.getId())
                .ne("sku_id", 0)
                .groupBy("vm_id", "channel_code", "max_capacity", "channel_id", "current_capacity", "sku_id")
                .select("MAX(last_supply_time) as last_supply_time",
                        "vm_id", "channel_code", "max_capacity", "current_capacity", "sku_id", "channel_id")
                .ge("last_supply_time", vendingMachine.getLastSupplyTime())
        ;

        //货道
        List<ChannelEntity> channelList = channelService.list(queryWrapper);

        //补货列表
        List<SupplyChannel> collect = channelList.stream().map(c -> {
            SupplyChannel supplyChannel = new SupplyChannel();
            //货道编号
            supplyChannel.setChannelId(c.getChannelCode());
            supplyChannel.setCapacity(c.getMaxCapacity() - c.getCurrentCapacity());
            supplyChannel.setSkuId(c.getSkuId());
            supplyChannel.setSkuName(c.getSku().getSkuName());
            supplyChannel.setSkuImage(c.getSku().getSkuImage());
            return supplyChannel;
        }).collect(Collectors.toList());
        //创建补货协议数据
        SupplyCfg supplyCfg = new SupplyCfg();
        supplyCfg.setSupplyData(collect);
        supplyCfg.setInnerCode(vendingMachine.getInnerCode());
        supplyCfg.setMsgType("supplyTask");
        //发送数据
        try {
            mqttProducer.send(TopicConfig.SUPPLY_TOPIC, 2, supplyCfg);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            System.out.println(vendingMachine.getInnerCode() + ":数据发送失败");
        }
    }

    @Override
    public int inventory(int percent, VendingMachineEntity vendingMachine) {
        //警戒值的计算
        Integer maxCapacity = vendingMachine.getType().getChannelMaxCapacity();
        int alertValue = (int) (maxCapacity * ((float) percent / 100));

        //统计当前库存小于等于警戒线的货道数量  7
        LambdaQueryWrapper<ChannelEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(ChannelEntity::getVmId, vendingMachine.getId())
                .le(ChannelEntity::getCurrentCapacity, alertValue)
                .ne(ChannelEntity::getSkuId, 0L);
        return channelService.count(qw);
    }

    @Override
    public VendingMachineEntity findByInnerCode(String innerCode) {
        LambdaQueryWrapper<VendingMachineEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(VendingMachineEntity::getInnerCode, innerCode);

        return this.getOne(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = {Exception.class})
    public boolean add(CreateVMReq vendingMachine) {
        VendingMachineEntity vendingMachineEntity = new VendingMachineEntity();
        vendingMachineEntity.setInnerCode("");
        vendingMachineEntity.setNodeId(Long.valueOf(vendingMachine.getNodeId()));
        vendingMachineEntity.setVmType(vendingMachine.getVmType());
        NodeEntity nodeEntity = nodeService.getById(vendingMachine.getNodeId());
        if (nodeEntity == null) {
            throw new LogicException("所选点位不存在");
        }
        String cityCode = nodeEntity.getArea().getCityCode();
        vendingMachineEntity.setAreaId(nodeEntity.getArea().getId());
        vendingMachineEntity.setBusinessId(nodeEntity.getBusinessId());
        vendingMachineEntity.setRegionId(nodeEntity.getRegionId());

        vendingMachineEntity.setCityCode(cityCode);
        vendingMachineEntity.setCreateUserId(Long.valueOf(vendingMachine.getCreateUserId()));
        vendingMachineEntity.setOwnerId(nodeEntity.getOwnerId());
        vendingMachineEntity.setOwnerName(nodeEntity.getOwnerName());


        //调用用户服务获取创建者姓名
        vendingMachineEntity.setCreateUserName(userService.getUser(vendingMachine.getCreateUserId()).getUserName());
        this.save(vendingMachineEntity);

        //设置售货机的innerCode
        UpdateWrapper<VendingMachineEntity> uw = new UpdateWrapper<>();
        String innerCode = generateInnerCode(vendingMachineEntity.getNodeId());
        uw.lambda()
                .set(VendingMachineEntity::getInnerCode, innerCode)
                .eq(VendingMachineEntity::getId, vendingMachineEntity.getId());
        this.update(uw);

        vendingMachineEntity.setInnerCode(innerCode);
        vendingMachineEntity.setClientId(generateClientId(innerCode));
        //创建货道数据
        createChannel(vendingMachineEntity);

        //创建版本数据
        versionService.initVersionCfg(vendingMachineEntity.getId(), innerCode);

        return true;
    }

    @Override
    public boolean update(Long id, Long nodeId) {
        VendingMachineEntity vm = this.getById(id);
        if (vm.getVmStatus() == VMSystem.VM_STATUS_RUNNING)
            throw new LogicException("改设备正在运营");
        NodeEntity nodeEntity = nodeService.getById(nodeId);
        vm.setNodeId(nodeId);
        vm.setRegionId(nodeEntity.getRegionId());
        vm.setBusinessId(nodeEntity.getBusinessId());
        vm.setOwnerName(nodeEntity.getOwnerName());
        vm.setOwnerId(nodeEntity.getOwnerId());

        return this.updateById(vm);
    }

    @Override
    public List<ChannelEntity> getAllChannel(String innerCode) {
        return channelService.getChannelesByInnerCode(innerCode);
    }

    @Override
    public List<SkuViewModel> getSkuList(String innerCode) {
        //获取货道列表
        List<ChannelEntity> channelList = this.getAllChannel(innerCode)
                .stream()
                .filter(c -> c.getSkuId() > 0 && c.getSku() != null).collect(Collectors.toList());
        //获取有商品的库存余量
        Map<SkuEntity, Integer> skuMap = channelList
                .stream()
                .collect(Collectors.groupingBy(ChannelEntity::getSku, Collectors.summingInt(ChannelEntity::getCurrentCapacity)));
        //获取有商品的真实价格
        Map<Long, IntSummaryStatistics> skuPrice = channelList.stream().collect(Collectors.groupingBy(ChannelEntity::getSkuId, Collectors.summarizingInt(ChannelEntity::getPrice)));

        return skuMap.entrySet().stream().map(entry -> {
                    SkuEntity sku = entry.getKey(); //查询商品
                    if (sku.getCapacity() == null) {
                        sku.setCapacity(0);
                    }
                    sku.setRealPrice(skuPrice.get(sku.getSkuId()).getMin());//真实价格
                    SkuViewModel skuViewModel = new SkuViewModel();
                    BeanUtils.copyProperties(sku, skuViewModel);
                    skuViewModel.setImage(sku.getSkuImage());//图片
                    skuViewModel.setCapacity(entry.getValue());
                    return skuViewModel;
                }).sorted(Comparator.comparing(SkuViewModel::getCapacity).reversed())  //按库存量降序排序
                .collect(Collectors.toList());
    }

    @Override
    public SkuEntity getSku(String innerCode, long skuId) {
        SkuEntity skuEntity = skuService.getById(skuId);
        skuEntity.setRealPrice(channelService.getRealPrice(innerCode, skuId));
        LambdaQueryWrapper<ChannelEntity> qw = new LambdaQueryWrapper<>();
        qw
                .eq(ChannelEntity::getSkuId, skuId)
                .eq(ChannelEntity::getInnerCode, innerCode);
        List<ChannelEntity> channelList = channelService.list(qw);
        int capacity = 0;
        if (channelList == null || channelList.size() <= 0)
            capacity = 0;
        else
            capacity = channelList
                    .stream()
                    .map(ChannelEntity::getCurrentCapacity)
                    .reduce(Integer::sum).get();
        skuEntity.setCapacity(capacity);

        return skuEntity;
    }

    @Override
    @Transactional(rollbackFor = {Exception.class})
    public boolean supply(SupplyCfg supply) {
        VendingMachineEntity vendingMachineEntity = this.findByInnerCode(supply.getInnerCode());
        vendingMachineEntity.setLastSupplyTime(LocalDateTime.now());
        this.updateById(vendingMachineEntity);
        List<ChannelEntity> channelList = channelService.getChannelesByInnerCode(supply.getInnerCode());
        supply.getSupplyData()
                .forEach(
                        c -> {
                            Optional<ChannelEntity> item =
                                    channelList.stream()
                                            .filter(channel -> channel.getChannelCode().equals(c.getChannelId()))
                                            .findFirst();
                            if (item.isPresent()) {
                                ChannelEntity channelEntity = item.get();
                                channelEntity.setCurrentCapacity(channelEntity.getCurrentCapacity() + c.getCapacity());
                                channelEntity.setLastSupplyTime(LocalDateTime.now());
                                channelService.supply(channelEntity);
                            }
                        });
        //更新补货版本号；
        versionService.updateSupplyVersion(supply.getInnerCode());
//        notifyGoodsStatus(supply.getInnerCode(),false);

        return true;
    }

    @Override
    public boolean updateStatus(String innerCode, int status, Double lat, Double lon) {
        try {
            UpdateWrapper<VendingMachineEntity> uw = new UpdateWrapper<>();
            uw.lambda()
                    .eq(VendingMachineEntity::getInnerCode, innerCode)
                    .set(VendingMachineEntity::getVmStatus, status);
            this.update(uw);

            if (status == VMSystem.TASK_TYPE_DEPLOY) {
                VMDistance vmDistance = new VMDistance();
                vmDistance.setInnerCode(innerCode);
                vmDistance.setLat(lat);
                vmDistance.setLon(lon);

                this.setVMDistance(vmDistance);
            } else if (status == VMSystem.TASK_TYPE_REVOKE) {
                this.removeVmInES(innerCode);
            }
        } catch (Exception ex) {
            log.error("updateStatus error,innerCode is " + innerCode + " status is " + status, ex);

            return false;
        }

        return true;
    }

    @Override
    @Transactional
    public boolean vendOutResult(VendoutResp vendoutResp) {
        try {
            String key = "vmService.outResult." + vendoutResp.getVendoutResult().getOrderNo();

            //对结果做校验，防止重复上传(从redis校验)
            Object redisValue = redisTemplate.opsForValue().get(key);
            redisTemplate.delete(key);

            if (redisValue != null) {
                log.info("出货重复上传");
                return false;
            }


            //存入出货流水数据
            VendoutRunningEntity vendoutRunningEntity = new VendoutRunningEntity();
            vendoutRunningEntity.setInnerCode(vendoutResp.getInnerCode());
            vendoutRunningEntity.setOrderNo(vendoutResp.getVendoutResult().getOrderNo());
            vendoutRunningEntity.setStatus(vendoutResp.getVendoutResult().isSuccess());
            vendoutRunningEntity.setPrice(vendoutResp.getVendoutResult().getPrice());
            vendoutRunningEntity.setSkuId(vendoutResp.getVendoutResult().getSkuId());
            vendoutRunningService.save(vendoutRunningEntity);


            //存入redis
            redisTemplate.opsForValue().set(key, key);
            redisTemplate.expire(key, 7, TimeUnit.DAYS);

            //减货道库存
            ChannelEntity channel = channelService.getChannelInfo(vendoutResp.getInnerCode(), vendoutResp.getVendoutResult().getChannelId());
            int currentCapacity = channel.getCurrentCapacity() - 1;
            if (currentCapacity < 0) {
                log.info("缺货");
                notifyGoodsStatus(vendoutResp.getInnerCode(), true);

                return true;
            }

            channel.setCurrentCapacity(currentCapacity);
            channelService.updateById(channel);
        } catch (Exception e) {
            log.error("update vendout result error.", e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();

            return false;
        }

        return true;
    }

    @Override
    public Pager<String> getAllInnerCodes(boolean isRunning, long pageIndex, long pageSize) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<VendingMachineEntity> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageIndex, pageSize);

        QueryWrapper<VendingMachineEntity> qw = new QueryWrapper<>();
        if (isRunning) {
            qw.lambda()
                    .select(VendingMachineEntity::getInnerCode)
                    .eq(VendingMachineEntity::getVmStatus, 1);
        } else {
            qw.lambda()
                    .select(VendingMachineEntity::getInnerCode)
                    .ne(VendingMachineEntity::getVmStatus, 1);
        }
        this.page(page, qw);
        Pager<String> result = new Pager<>();
        result.setCurrentPageRecords(page.getRecords().stream().map(VendingMachineEntity::getInnerCode).collect(Collectors.toList()));
        result.setPageIndex(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setTotalCount(page.getTotal());

        return result;
    }

    @Override
    public Pager<VendingMachineEntity> query(Long pageIndex, Long pageSize, Integer status, String innerCode) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<VendingMachineEntity> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageIndex, pageSize);
        LambdaQueryWrapper<VendingMachineEntity> queryWrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            queryWrapper.eq(VendingMachineEntity::getVmStatus, status);
        }
        if (!Strings.isNullOrEmpty(innerCode)) {
            queryWrapper.likeLeft(VendingMachineEntity::getInnerCode, innerCode);
        }
        this.page(page, queryWrapper);

        return Pager.build(page);
    }


    @Override
    public Integer getCountByOwnerId(Integer ownerId) {
        LambdaQueryWrapper<VendingMachineEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(VendingMachineEntity::getOwnerId, ownerId);

        return this.count(qw);
    }

    @Override
    public Boolean setVMDistance(VMDistance vmDistance) {
        VendingMachineEntity vmEntity = this.findByInnerCode(vmDistance.getInnerCode());
        if (vmEntity == null) {
            throw new LogicException("该设备编号不存在:" + vmDistance.getInnerCode());
        }
        IndexRequest request = new IndexRequest("vm");
        request.id(vmDistance.getInnerCode());
        request.source(
                "addr", vmEntity.getNode().getAddr(),
                "innerCode", vmEntity.getInnerCode(),
                "nodeName", vmEntity.getNode().getName(),
                "location", vmDistance.getLat() + "," + vmDistance.getLon(),
                "typeName", vmEntity.getType().getName());
        try {
            esClient.index(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.error("添加售货机位置信息失败", e);

            return false;
        }
        vmEntity.setLatitude(vmDistance.getLat());
        vmEntity.setLongitudes(vmDistance.getLon());
        this.updateById(vmEntity);

        return true;
    }

    /**
     * 从ES中删除售货机
     *
     * @param innerCode
     * @return
     */
    private void removeVmInES(String innerCode) {
        DeleteRequest request = new DeleteRequest("vm").id(innerCode);
        try {
            esClient.delete(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.error("从ES中删除售货机失败", e);
        }
    }

    /**
     * 生成售货机InnerCode
     *
     * @param nodeId 点位Id
     * @return
     */
    private String generateInnerCode(long nodeId) {
        NodeEntity nodeEntity = nodeService.getById(nodeId);

        StringBuilder sbInnerCode = new StringBuilder(nodeEntity.getArea().getCityCode());

        int count = getCountByArea(nodeEntity.getArea());
        sbInnerCode.append(Strings.padStart(String.valueOf(count + 1), 5, '0'));

        return sbInnerCode.toString();
    }

    /**
     * 创建货道
     *
     * @param vm
     * @return
     */
    private boolean createChannel(VendingMachineEntity vm) {
        VmTypeEntity vmType = vmTypeService.getById(vm.getVmType());

        for (int i = 1; i <= vmType.getVmRow(); i++) {
            for (int j = 1; j <= vmType.getVmCol(); j++) {
                ChannelEntity channel = new ChannelEntity();
                channel.setChannelCode(i + "-" + j);
                channel.setCurrentCapacity(0);
                channel.setInnerCode(vm.getInnerCode());
                channel.setLastSupplyTime(vm.getLastSupplyTime());
                channel.setMaxCapacity(vmType.getChannelMaxCapacity());
                channel.setVmId(vm.getId());
                channelService.save(channel);
            }
        }

        return true;
    }

    /**
     * 获取某一地区下售货机数量
     *
     * @param area
     * @return
     */
    private int getCountByArea(AreaEntity area) {
        QueryWrapper<VendingMachineEntity> qw = new QueryWrapper<>();
        qw.lambda()
                .eq(VendingMachineEntity::getCityCode, area.getCityCode())
                .isNotNull(VendingMachineEntity::getInnerCode)
                .ne(VendingMachineEntity::getInnerCode, "");

        return this.count(qw);
    }

    /**
     * 发送缺货告警信息
     *
     * @param innerCode
     * @param isFault   true--缺货状态;false--不缺货状态
     */
    private void notifyGoodsStatus(String innerCode, boolean isFault) {
        VmStatusContract contract = new VmStatusContract();
        contract.setNeedResp(false);
        contract.setSn(0);
        contract.setInnerCode(innerCode);

        StatusInfo statusInfo = new StatusInfo();
        statusInfo.setStatus(isFault);
        statusInfo.setStatusCode("10003");
        List<StatusInfo> statusInfos = Lists.newArrayList();
        statusInfos.add(statusInfo);
        contract.setStatusInfo(statusInfos);

        try {
            //  发送设备不缺货消息(置设备为不缺货)
            mqttProducer.send(TopicConfig.VM_STATUS_TOPIC, 2, contract);
        } catch (JsonProcessingException e) {
            log.error("serialize error.", e);
        }
    }

    /**
     * 生成售货机的clientId
     *
     * @param innerCode
     * @return
     */
    private String generateClientId(String innerCode) {
        String clientId = System.currentTimeMillis() + innerCode;

        return org.springframework.util.DigestUtils.md5DigestAsHex(clientId.getBytes());
    }
}
