package com.lkd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.lkd.common.VMSystem;
import com.lkd.config.TopicConfig;
import com.lkd.contract.VendoutReq;
import com.lkd.contract.VendoutReqData;
import com.lkd.contract.VendoutResp;
import com.lkd.dao.OrderDao;
import com.lkd.emq.MqttProducer;
import com.lkd.entity.OrderEntity;
import com.lkd.feignService.UserService;
import com.lkd.feignService.VMService;
import com.lkd.http.viewModel.CreateOrderReq;
import com.lkd.http.viewModel.OrderResp;
import com.lkd.service.OrderCollectService;
import com.lkd.service.OrderService;
import com.lkd.utils.JsonUtil;
import com.lkd.viewmodel.*;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.*;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {

    //@Autowired
    //private MqttProducer mqttProducer;


    @Autowired
    private VMService vmService;
    @Autowired
    private RestHighLevelClient client;

    @Autowired
    private UserService userService;


    @Override
    public List<Long> getTop10Sku(Integer businessId) {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        //查询：最近三个月
        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("update_time");
        //大于当前时间往前推50月然后进行格式化
        rangeQueryBuilder.gte(LocalDateTime.now().plusMonths(-50).format(DateTimeFormatter.ISO_DATE_TIME));
        //小于当前时间然后进行格式化
        rangeQueryBuilder.lte(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.must(rangeQueryBuilder);
        //进行商圈id唯一性查询
        boolQueryBuilder.must(QueryBuilders.termQuery("business_id", businessId));
        sourceBuilder.query(boolQueryBuilder);
        //分组和统计查询
        AggregationBuilder aggregationBuilder = AggregationBuilders.terms("sku").field("sku_id")
                .subAggregation(AggregationBuilders.count("count").field("sku_id"))
                .order(BucketOrder.aggregation("count", false))
                .size(10);

        sourceBuilder.aggregation(aggregationBuilder);
        searchRequest.source(sourceBuilder);

        try {
            SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
            Aggregations aggregations = search.getAggregations();
            //Terms 是接口模式
             Terms sku=aggregations.get("sku");
            List<Long> list = Lists.newArrayList();
            for (Terms.Bucket bucket : sku.getBuckets()) {
                long skuId= Long.parseLong(bucket.getKey().toString());
                list.add(skuId);
            }
            return list;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public Pager<OrderViewModel> search(Integer pageIndex, Integer pageSize, String orderNo, String start, String end) {
        SearchRequest searchRequest = new SearchRequest("order");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        //根据订单编号查询
        if (!Strings.isNullOrEmpty(orderNo)) {
            boolQueryBuilder.must(QueryBuilders.termQuery("order_no", orderNo));
        }
        //根据时间范围查询
        if (!Strings.isNullOrEmpty(start) && !Strings.isNullOrEmpty(end)) {
            RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("update_time").gte(start).lte(end);
            boolQueryBuilder.must(rangeQueryBuilder);

        }
        //进行分页查询
        searchSourceBuilder.from((pageIndex - 1) * pageSize);
        searchSourceBuilder.size(pageSize);
        searchSourceBuilder.sort("update_time", SortOrder.DESC);
        searchSourceBuilder.query(boolQueryBuilder);
        searchRequest.source(searchSourceBuilder);


        List<OrderViewModel> list = Lists.newArrayList();
        try {
            SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);

            SearchHits hits = search.getHits();

            for (SearchHit hit : hits.getHits()) {
                OrderViewModel order = new OrderViewModel();
                JsonNode jsonNode = JsonUtil.getTreeNode(hit.getSourceAsString());
                order.setId(jsonNode.findParent("id").asLong());
                order.setStatus(jsonNode.findPath("status").asInt());
                order.setBill(jsonNode.findPath("bill").asInt());
                order.setOwnerId(jsonNode.findPath("owner_id").asInt());
                order.setPayType(jsonNode.findPath("pay_type").asText());
                order.setOrderNo(jsonNode.findPath("order_no").asText());
                order.setInnerCode(jsonNode.findPath("inner_code").asText());
                order.setSkuName(jsonNode.findPath("sku_name").asText());
                order.setSkuId(jsonNode.findPath("sku_id").asLong());
                order.setPayStatus(jsonNode.findPath("pay_status").asInt());
                order.setBusinessName(jsonNode.findPath("business_name").asText());
                order.setBusinessId(jsonNode.findPath("business_id").asInt());
                order.setRegionId(jsonNode.findPath("region_id").asLong());
                order.setRegionName(jsonNode.findPath("region_name").asText());
                order.setPrice(jsonNode.findPath("price").asInt());
                order.setAmount(jsonNode.findPath("amount").asInt());
                order.setAddr(jsonNode.findPath("addr").asText());
                order.setOpenId(jsonNode.findPath("open_id").asText());
                order.setClassId(jsonNode.findPath("class_id").asInt());
                /*用于获取es中的时间格式进行转化*/
                order.setCreateTime(LocalDateTime.parse(jsonNode.findPath("create_time").asText(), DateTimeFormatter.ISO_DATE_TIME));
                order.setUpdateTime(LocalDateTime.parse(jsonNode.findPath("update_time").asText(), DateTimeFormatter.ISO_DATE_TIME));
                list.add(order);
            }
            long total = hits.getTotalHits().value;
            Pager<OrderViewModel> pager = new Pager<>();
            pager.setCurrentPageRecords(list);
            pager.setTotalCount(total);
            pager.setPageIndex(pageIndex);
            pager.setPageSize(pageSize);
            return pager;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    //添加订单编号
    @Override
    public OrderResp createOrder(CreateOrderReq req) {
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderNo(req.getOrderNo() == null ? IdWorker.get32UUID() : req.getOrderNo());
        orderEntity.setAmount(req.getAmount());
        orderEntity.setInnerCode(req.getInnerCode());
        orderEntity.setSkuId(req.getSkuId());
        //根据售货机编号获取设备详情
        VendingMachineViewModel vmInfo = vmService.getVMInfo(req.getInnerCode());
        //获取商品名称
        SkuViewModel sku = vmService.getSku(req.getInnerCode(), req.getSkuId() + "");
        orderEntity.setSkuName(sku.getSkuName());
        orderEntity.setPrice(req.getPrice());
        orderEntity.setThirdNo(req.getThirdNO());
        orderEntity.setPayStatus(0);
        orderEntity.setPayType(req.getPayType());
        orderEntity.setAddr(vmInfo.getNodeAddr());
        BeanUtils.copyProperties(vmInfo, orderEntity);
        orderEntity.setStatus(0);
        //获得商品类别id
        SkuViewModel skuById = vmService.getSkuById(req.getSkuId());
        orderEntity.setClassId(skuById.getClassId());
        //设置合作商的账单分账金额
        PartnerViewModel partner = userService.getPartner(vmInfo.getOwnerId());
        BigDecimal bigDecimal =new BigDecimal(sku.getRealPrice());
        ////遵循四舍五入的规则 ,把合作商的分成价格添加到里面去
        int bill = bigDecimal.multiply(new BigDecimal(partner.getRatio())).intValue();
         orderEntity.setBill(bill);
        //执行添加
        this.save(orderEntity);
        //创建用于后续的数据对象
        OrderResp resp = new OrderResp();
        resp.setId(orderEntity.getId().toString());
        resp.setAmount(req.getAmount());
        resp.setInnerCode(req.getInnerCode());
        resp.setOrderNo(orderEntity.getOrderNo());
        resp.setPrice(req.getPrice());
        resp.setSkuId(req.getSkuId());
        resp.setThirdNO(req.getThirdNO());

        return resp;
    }


    @Override
    public boolean vendoutResult(VendoutResp vendoutResp) {
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderNo(vendoutResp.getVendoutResult().getOrderNo());
        UpdateWrapper<OrderEntity> uw = new UpdateWrapper<>();
        LambdaUpdateWrapper<OrderEntity> lambdaUpdateWrapper = uw.lambda();
        lambdaUpdateWrapper.set(OrderEntity::getPayStatus, 1);
        //根据用户是否支付结果的报文判断是否成功
        if (vendoutResp.getVendoutResult().isSuccess()) {

            lambdaUpdateWrapper.set(OrderEntity::getStatus, VMSystem.ORDER_STATUS_VENDOUT_SUCCESS);
        } else {
            lambdaUpdateWrapper.set(OrderEntity::getStatus, VMSystem.ORDER_STATUS_VENDOUT_FAIL);
        }
        lambdaUpdateWrapper.eq(OrderEntity::getOrderNo, vendoutResp.getVendoutResult().getOrderNo());

        return this.update(lambdaUpdateWrapper);
    }

    @Override
    public boolean payComplete(String orderNo, String thirdNo) {
        OrderEntity order = this.getByOrderNo(orderNo);
        if (order == null) return false;

        // TODO:2.0项目没有原来的公司概念了
        float bill = 0f;
//        float bill = (float) order.getAmount() * (((float)companyService.findById(order.getCompanyId()).getData().getDivide())/100);

        UpdateWrapper<OrderEntity> uw = new UpdateWrapper<>();
        uw.lambda()
                .eq(OrderEntity::getOrderNo, orderNo)
                .set(OrderEntity::getThirdNo, thirdNo)
                .set(OrderEntity::getPayStatus, 1)
                .set(OrderEntity::getBill, (int) bill);

        //向售货机发起出货请求
        sendVendout(orderNo);

        return this.update(uw);
    }

    @Override
    public boolean payComplete(String orderNo) {
        sendVendout(orderNo);
        return true;
    }


    @Override
    public OrderEntity getByOrderNo(String orderNo) {
        QueryWrapper<OrderEntity> qw = new QueryWrapper<>();
        qw.lambda()
                .eq(OrderEntity::getOrderNo, orderNo);

        return this.getOne(qw);
    }

    @Override
    public Boolean cancel(String orderNo) {
        OrderEntity order = this.getByOrderNo(orderNo);

        if (order.getStatus() > VMSystem.ORDER_STATUS_CREATE)
            return true;

        order.setStatus(VMSystem.ORDER_STATUS_INVALID);
        order.setCancelDesc("用户取消");

        return true;
    }

    @Autowired
    private MqttProducer mqttProducer;

    /**
     * @param orderNo
     */
    private void sendVendout(String orderNo) {
        //根据订单编号获取订单数据
        OrderEntity orderEntity = this.getByOrderNo(orderNo);
        //创建EMQ中的数据对象
        VendoutReqData reqData = new VendoutReqData();
        reqData.setOrderNo(orderNo);
        reqData.setPayPrice(orderEntity.getAmount());
        reqData.setPayType(Integer.parseInt(orderEntity.getPayType()));
        reqData.setSkuId(orderEntity.getSkuId());
        reqData.setTimeout(60);
        reqData.setRequestTime(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

        //向售货机发送出货请求
        VendoutReq req = new VendoutReq();
        req.setVendoutData(reqData);
        req.setSn(System.nanoTime());
        req.setInnerCode(orderEntity.getInnerCode());
        req.setNeedResp(true);

        try {
            mqttProducer.send(TopicConfig.getVendoutTopic(orderEntity.getInnerCode()), 2, req);
        } catch (JsonProcessingException e) {
            log.error("send vendout req error.", e);
        }
    }
}
