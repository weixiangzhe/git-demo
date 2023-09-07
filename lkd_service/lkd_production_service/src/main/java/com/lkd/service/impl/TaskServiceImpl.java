package com.lkd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.lkd.common.VMSystem;
import com.lkd.config.TopicConfig;
import com.lkd.contract.SupplyCfg;
import com.lkd.contract.SupplyChannel;
import com.lkd.contract.TaskCompleteContract;
import com.lkd.dao.TaskDao;
import com.lkd.emq.MqttProducer;
import com.lkd.entity.TaskDetailsEntity;
import com.lkd.entity.TaskEntity;
import com.lkd.entity.TaskStatusTypeEntity;
import com.lkd.exception.LogicException;
import com.lkd.feignService.UserService;
import com.lkd.feignService.VMService;
import com.lkd.http.viewModel.CancelTaskViewModel;
import com.lkd.http.viewModel.RankViewModel;
import com.lkd.http.viewModel.TaskReportInfo;
import com.lkd.http.viewModel.TaskViewModel;
import com.lkd.service.TaskDetailsService;
import com.lkd.service.TaskService;
import com.lkd.service.TaskStatusTypeService;
import com.lkd.utils.UserRoleUtils;
import com.lkd.viewmodel.Pager;
import com.lkd.viewmodel.UserViewModel;
import com.lkd.viewmodel.UserWork;
import com.lkd.viewmodel.VendingMachineViewModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TaskServiceImpl extends ServiceImpl<TaskDao, TaskEntity> implements TaskService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TaskDetailsService taskDetailsService;

    @Autowired
    private VMService vmService;

    @Autowired
    private TaskStatusTypeService statusTypeService;

    @Autowired
    private UserService userService;

    @Autowired
    private MqttProducer mqttProducer;

    @Override
    public UserWork getUserWork(Integer userId, LocalDateTime start, LocalDateTime end) {
        UserWork userWork = new UserWork();
        userWork.setUserId(userId);
        //并行处理提高程序吞吐量
        //获取用户完成工单数

        CompletableFuture<Integer> workCountFuture = CompletableFuture.supplyAsync(() -> this.getCountByUserId(userId, VMSystem.TASK_STATUS_FINISH, start, end))
                /*
                 * 用于注册一个回调函数（回调处理器），在异步操作完成时被触发执行。
                 * 当使用 whenComplete 注册回调函数后，它将在异步操作完成时被调用，
                 * 无论操作是成功还是失败。
                 * 如果操作成功完成，回调函数将接收异步操作的结果作为参数 r异常参数 e 将为 null。
                 * 如果操作发生了异常，回调函数将接收异常作为参数 e，结果参数 r 将为 null。
                 * */
                .whenComplete((r, e) -> {
                    if (e != null) {
                        //回调失败
                        userWork.setWorkCount(0);
                    } else {
                        //回调成功
                        userWork.setWorkCount(r);
                    }
                });

        //获取工单总数
        CompletableFuture<Integer> totalFuture = CompletableFuture.supplyAsync(() -> this.getCountByUserId(userId, null, start, end))
                .whenComplete((r, e) -> {
                    if (e != null) {
                        //回调失败
                        userWork.setWorkCount(0);
                    } else {
                        //回调成功
                        userWork.setWorkCount(r);
                    }
                });
        //获取拒绝工单数
        CompletableFuture<Integer> cancelCountFuture = CompletableFuture.supplyAsync(() -> this.getCountByUserId(userId, VMSystem.TASK_STATUS_CANCEL, start, end))
                .whenComplete((r, e) -> {
                    if (e != null) {
                        //回调失败
                        userWork.setWorkCount(0);
                    } else {
                        //回调成功
                        userWork.setWorkCount(r);
                    }
                });

        //获取进行中工单数‘
        CompletableFuture<Integer> progressTotalFuture = CompletableFuture.supplyAsync(() -> this.getCountByUserId(userId, VMSystem.TASK_STATUS_PROGRESS, start, end))
                .whenComplete((r, e) -> {
                    if (e != null) {
                        //回调失败
                        userWork.setWorkCount(0);
                    } else {
                        //回调成功
                        userWork.setWorkCount(r);
                    }
                });

        CompletableFuture.allOf(workCountFuture,cancelCountFuture,progressTotalFuture).join();

        return userWork;
    }


    //根据工单状态，获取用户(当月)工单数
    private Integer getCountByUserId(Integer userId, Integer taskStatus, LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<TaskEntity> qw = new LambdaQueryWrapper<>();
        qw.ge(TaskEntity::getUpdateTime, start)
                .le(TaskEntity::getUpdateTime, end);
        if (userId != null) {
            qw.eq(TaskEntity::getUserId, userId);

        }
        if (taskStatus != null) {
            qw.eq(TaskEntity::getTaskStatus, taskStatus);
        }
        return this.count(qw);
    }

    @Override
    public List<TaskReportInfo> getTaskReportInfo(LocalDateTime start, LocalDateTime end) {

        //运营工单总数
        CompletableFuture<Integer> supplyTotalFuture = CompletableFuture.supplyAsync(() -> this.taskCount(start, end, false, null));
        //运维工单总数
        CompletableFuture<Integer> repairTotalFuture = CompletableFuture.supplyAsync(() -> this.taskCount(start, end, true, null));
        //完成运营工单总数
        CompletableFuture<Integer> completedSupplyFuture = CompletableFuture.supplyAsync(() -> this.taskCount(start, end, false, VMSystem.TASK_STATUS_FINISH));
        //完成运维工单总数
        CompletableFuture<Integer> completedRepairFuture = CompletableFuture.supplyAsync(() -> this.taskCount(start, end, true, VMSystem.TASK_STATUS_FINISH));
        //拒绝运营工单总数
        CompletableFuture<Integer> cancelSupplyFuture = CompletableFuture.supplyAsync(() -> this.taskCount(start, end, false, VMSystem.TASK_STATUS_CANCEL));
        //拒绝掉的运维工单总数
        CompletableFuture<Integer> cancelRepairFuture = CompletableFuture.supplyAsync(() -> this.taskCount(start, end, true, VMSystem.TASK_STATUS_CANCEL));
        //获取运营人员数量
        CompletableFuture<Integer> operatorCountFuture = CompletableFuture.supplyAsync(() -> userService.getOperatorCount());
        //获取运维人员数量
        CompletableFuture<Integer> repairerCountFuture = CompletableFuture.supplyAsync(() -> userService.getRepairerCount());

        //并行处理
        CompletableFuture.allOf(
                supplyTotalFuture,
                repairTotalFuture,
                completedSupplyFuture,
                completedRepairFuture,
                cancelSupplyFuture,
                cancelRepairFuture,
                operatorCountFuture,
                repairerCountFuture
        ).join();
        List<TaskReportInfo> result = Lists.newArrayList();
        TaskReportInfo supplyTaskInfo = new TaskReportInfo();


        TaskReportInfo repairTaskInfo = new TaskReportInfo();


        try {
            supplyTaskInfo.setCancelTotal(cancelSupplyFuture.get());
            supplyTaskInfo.setCompletedTotal(completedSupplyFuture.get());
            supplyTaskInfo.setRepair(false);
            supplyTaskInfo.setWorkerCount(operatorCountFuture.get());
            result.add(supplyTaskInfo);


            repairTaskInfo.setTotal(repairTotalFuture.get());
            repairTaskInfo.setCancelTotal(cancelRepairFuture.get());
            repairTaskInfo.setCompletedTotal(completedRepairFuture.get());
            repairTaskInfo.setRepair(true);
            repairTaskInfo.setWorkerCount(repairerCountFuture.get());
            result.add(repairTaskInfo);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }


        return result;
    }


    /**
     * 统计工单数量
     *
     * @param start
     * @param end
     * @param repair         是否是运维工单
     * @param taskStatus:状态值
     * @return
     */
    private int taskCount(LocalDateTime start, LocalDateTime end, boolean repair, Integer taskStatus) {
        LambdaQueryWrapper<TaskEntity> qw = new LambdaQueryWrapper<>();
        qw.ge(TaskEntity::getUpdateTime, start)
                .le(TaskEntity::getUpdateTime, end);
        //按工单状态查询
        if (taskStatus != null) {
            qw.eq(TaskEntity::getTaskStatus, taskStatus);
        }
        if (repair) {
            //运维工单
            qw.ne(TaskEntity::getProductTypeId, VMSystem.TASK_TYPE_SUPPLY);
        } else {
            //运营工单
            qw.eq(TaskEntity::getProductTypeId, VMSystem.TASK_TYPE_SUPPLY);
        }
        return this.count(qw);
    }

    @Override
    public boolean cancelTask(CancelTaskViewModel cancelTaskViewModel) {
        //如果想要取消工单，需要有工单id和取消理由
        TaskEntity task = this.getById(cancelTaskViewModel.getId());
        //判读工单信息中工单状态时候是否完成或者工单状态时候为取消状态
        if (task.getTaskStatus() == VMSystem.TASK_STATUS_FINISH || task.getTaskStatus() == VMSystem.TASK_STATUS_CANCEL) {
            throw new LogicException("工单已经结束");

        }
        //如果判断不成立的话进行把工单信息中订单状态修改为取消状态
        task.setTaskStatus(VMSystem.TASK_STATUS_CANCEL);

        task.setDesc(cancelTaskViewModel.getDesc());
        //工单量减少
        this.updateTaskZSet(task, -1);
        //进行修改操作
        return this.updateById(task);
    }

    @Override
    public Boolean accept(Long id) {
        //根据工单id获取到对应的工单信息
        TaskEntity task = this.getById(id);
        //判断当前工单状态是否是带出处理工
        if (task.getTaskStatus() != VMSystem.TASK_STATUS_CREATE) {
            throw new LogicException("工单状态不是待处理");
        }
        //如果是，把工单信息中工单状态修改为进行中
        task.setTaskStatus(VMSystem.TASK_STATUS_PROGRESS);
        //进行修改操作
        return this.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = {Exception.class}, noRollbackFor = {LogicException.class})
    public boolean createTask(TaskViewModel taskViewModel) throws LogicException {
        //根据设备编号  和工单类型
        checkCreateTask(taskViewModel.getInnerCode(), taskViewModel.getProductType());
        //判断符合传输的设备编号和工单类型查询数据库变种是否有符合的数据
        if (hasTask(taskViewModel.getInnerCode(), taskViewModel.getProductType())) {
            throw new LogicException("该机器有未完成的同类型工单");
        }
        //代表机器没有这种类型的工单，进行添加工单操作
        TaskEntity taskEntity = new TaskEntity();
        //将传输过来的订单数据赋值给实体类进行添加操作
        BeanUtils.copyProperties(taskViewModel, taskEntity);
        //获取到redis中工单编号
        taskEntity.setTaskCode(this.generateTaskCode());
        //设置工单状态为创建或者待处理状态
        taskEntity.setTaskStatus(VMSystem.TASK_STATUS_CREATE);
        //将传输过来的工单类型赋值给实体类的工单类型id
        taskEntity.setProductTypeId(taskViewModel.getProductType());
        //根据任务分配人的id查询用户信息
        UserViewModel user = userService.getUser(taskViewModel.getAssignorId());
        taskEntity.setUserName(user.getUserName());
        taskEntity.setRegionId(user.getRegionId());

        taskEntity.setUserId(taskViewModel.getUserId());
        //调用售货机的微服务中的方法获取封装实体中的定位的详细信息
        taskEntity.setAddr(vmService.getVMInfo(taskViewModel.getInnerCode()).getNodeAddr());
        //实行保存操作
        this.save(taskEntity);
        //进行判断当前创建操作是不是补充工单操作
        if (taskEntity.getProductTypeId() == VMSystem.TASK_TYPE_SUPPLY) {
            //如果是的话就继续循环补充工单中的details的数组
            taskViewModel.getDetails().forEach(d -> {
                TaskDetailsEntity detailsEntity = new TaskDetailsEntity();
                BeanUtils.copyProperties(d, detailsEntity);
                detailsEntity.setTaskId(taskEntity.getTaskId());
                //在taskDetails表进行插入操作
                taskDetailsService.save(detailsEntity);
            });
        }
        //增加工单量列表
        this.updateTaskZSet(taskEntity, 1);
        return true;
    }


    /**
     * 更新工单量列表
     *
     * @param taskEntity
     * @param score
     */
    private void updateTaskZSet(TaskEntity taskEntity, int score) {
        String roleCode = "1003";//运维人员
        if (taskEntity.getProductTypeId().intValue() == 2) {
            //补货工单
            roleCode = "1002";//运营人员
        }
        String key = VMSystem.REGION_TASK_KEY_PREF +
                LocalDate.now()
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "." +
                taskEntity.getRegionId() + "." + roleCode;

        redisTemplate.opsForZSet().incrementScore(key, taskEntity.getAssignorId(), score);

    }


    @Override
    public boolean completeTask(long id) {

        return completeTask(id, 0d, 0d, "");
    }

    @Override
    public boolean completeTask(long id, Double lat, Double lon, String addr) {

        TaskEntity taskEntity = this.getById(id);
        //如果都没有成立单表没有完成  把工单状态变为完成
        taskEntity.setTaskStatus(VMSystem.TASK_STATUS_FINISH);

        taskEntity.setAddr(addr);

        //执行修改操作
        this.updateById(taskEntity);
        //补货工单
        if (taskEntity.getProductTypeId() == VMSystem.TASK_TYPE_SUPPLY) {
            System.out.println("发送补货协议");
            noticeVmSeviceSupply(taskEntity);
        }
        //维护投放或者撤机
        if (taskEntity.getProductTypeId() == VMSystem.TASK_TYPE_DEPLOY
                || taskEntity.getProductTypeId() == VMSystem.TASK_TYPE_REVOKE) {
            System.out.println("发送投放或者撤机");
            noticeVmServiceStatus(lat, lon, taskEntity);
        }


        return true;

    }

    /**
     * 补货协议封装与下发
     *
     * @param taskEntity
     */
    private void noticeVmSeviceSupply(TaskEntity taskEntity) {
        //1.根据工单id查询工单明细
        LambdaQueryWrapper<TaskDetailsEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(TaskDetailsEntity::getTaskId, taskEntity.getTaskId());
        List<TaskDetailsEntity> details = taskDetailsService.list(qw);
        //构建补货协议
        SupplyCfg supplyCfg = new SupplyCfg();
        supplyCfg.setInnerCode(taskEntity.getInnerCode());
        List<SupplyChannel> supplyChannels = Lists.newArrayList();
        details.forEach(d -> {
            SupplyChannel supplyChannel = new SupplyChannel();
            supplyChannel.setChannelId(d.getChannelCode());
            supplyChannel.setCapacity(d.getExpectCapacity());
            supplyChannels.add(supplyChannel);
        });

        supplyCfg.setSupplyData(supplyChannels);
        //2.下发补货协议
        //发送到emq
        try {
            mqttProducer.send(TopicConfig.COMPLETED_TASK_TOPIC, 2, supplyCfg);
        } catch (Exception e) {
            log.error("发送工单完成协议出错");
            throw new LogicException("发送工单完成协议出错");
        }
    }

    /**
     * 维修工单完成协议
     *
     * @param lat
     * @param lon
     * @param task
     */
    private void noticeVmServiceStatus(Double lat, Double lon, TaskEntity task) {
        //向EMQ发送数据
        //封装协议
        TaskCompleteContract taskCompleteContract = new TaskCompleteContract();
        taskCompleteContract.setInnerCode(task.getInnerCode());//售货机编号
        taskCompleteContract.setTaskType(task.getProductTypeId());//工单类型
        taskCompleteContract.setLat(lat);
        taskCompleteContract.setLon(lon);
        //发送
        try {
            mqttProducer.send(TopicConfig.COMPLETED_TASK_TOPIC, 2, taskCompleteContract);
        } catch (JsonProcessingException e) {
            throw new LogicException("发送工单完成失败");

        }
    }


    @Override
    public List<TaskStatusTypeEntity> getAllStatus() {
        QueryWrapper<TaskStatusTypeEntity> qw = new QueryWrapper<>();
        qw.lambda()
                .ge(TaskStatusTypeEntity::getStatusId, VMSystem.TASK_STATUS_CREATE);

        return statusTypeService.list(qw);
    }

    @Override
    public Pager<TaskEntity> search(Long pageIndex, Long pageSize, String innerCode, Integer userId, String taskCode, Integer status, Boolean isRepair, String start, String end) {
        Page<TaskEntity> page = new Page<>(pageIndex, pageSize);
        LambdaQueryWrapper<TaskEntity> qw = new LambdaQueryWrapper<>();
        if (!Strings.isNullOrEmpty(innerCode)) {
            qw.eq(TaskEntity::getInnerCode, innerCode);
        }
        if (userId != null && userId > 0) {
            qw.eq(TaskEntity::getAssignorId, userId);
        }
        if (!Strings.isNullOrEmpty(taskCode)) {
            qw.like(TaskEntity::getTaskCode, taskCode);
        }
        if (status != null && status > 0) {
            qw.eq(TaskEntity::getTaskStatus, status);
        }
        if (isRepair != null) {
            if (isRepair) {
                qw.ne(TaskEntity::getProductTypeId, VMSystem.TASK_TYPE_SUPPLY);
            } else {
                qw.eq(TaskEntity::getProductTypeId, VMSystem.TASK_TYPE_SUPPLY);
            }
        }
        if (!Strings.isNullOrEmpty(start) && !Strings.isNullOrEmpty(end)) {
            qw
                    .ge(TaskEntity::getCreateTime, LocalDate.parse(start, DateTimeFormatter.ISO_LOCAL_DATE))
                    .le(TaskEntity::getCreateTime, LocalDate.parse(end, DateTimeFormatter.ISO_LOCAL_DATE));
        }
        //根据最后更新时间倒序排序
        qw.orderByDesc(TaskEntity::getUpdateTime);

        return Pager.build(this.page(page, qw));
    }


    /**
     * 获取同一天内分配的工单最少的人
     *
     * @param regionId
     * @param isRepair 是否是维修工单
     * @return
     */
    @Override
    public Integer getLeastUser(String regionId, Boolean isRepair) {
        String roleCode = "1002";

        if (isRepair) {
            roleCode = "1003";
        }
        String key = VMSystem.REGION_TASK_KEY_PREF +
                LocalDate.now()
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "." +
                regionId + "." + roleCode;
        //根据key值查找redis中的对应数据，取第一个数据
        Set<Object> range = redisTemplate.opsForZSet().range(key, 0, 1);


        return (Integer) range.stream().collect(Collectors.toList()).get(0);

    }


    /**
     * 同一台设备下是否存在未完成的工单
     *
     * @param innerCode：设备编号
     * @param productionType：工单类型
     * @return
     */
    private boolean hasTask(String innerCode, int productionType) {
        QueryWrapper<TaskEntity> qw = new QueryWrapper<>();
        qw.lambda()
                .select(TaskEntity::getTaskId)
                .eq(TaskEntity::getInnerCode, innerCode)
                .eq(TaskEntity::getProductTypeId, productionType)
                .le(TaskEntity::getTaskStatus, VMSystem.TASK_STATUS_PROGRESS);

        return this.count(qw) > 0;
    }

    private void checkCreateTask(String innerCode, int productType) throws LogicException {
        //根据机器编号获取设备详情
        VendingMachineViewModel vmInfo = vmService.getVMInfo(innerCode);
        if (vmInfo == null) throw new LogicException("设备校验失败");
        if (productType == VMSystem.TASK_TYPE_DEPLOY && vmInfo.getVmStatus() == VMSystem.VM_STATUS_RUNNING) {
            throw new LogicException("该设备已在运营");
        }

        if (productType == VMSystem.TASK_TYPE_SUPPLY && vmInfo.getVmStatus() != VMSystem.VM_STATUS_RUNNING) {
            throw new LogicException("该设备不在运营状态");
        }

        if (productType == VMSystem.TASK_TYPE_REVOKE && vmInfo.getVmStatus() != VMSystem.VM_STATUS_RUNNING) {
            throw new LogicException("该设备不在运营状态");
        }
    }

    /**
     * 生成工单编码
     *
     * @return
     */
    private String generateTaskCode() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String key = "lkd.task.code." + date;
        //去redis中获取可以值
        Object obj = redisTemplate.opsForValue().get(key);
        //判断获取到是否为空
        if (obj == null) {
            //如果为空代表第一次添加，设定当前数据为0001并且设置过期时间为1天
            redisTemplate.opsForValue().set(key, 1L, Duration.ofDays(1));
            return date + "0001";
        }
        //不为空  代表当前添加数据不是第一次添加  从redis中获取到最新的数据进行对key+1在进行存储
        return date + Strings.padStart(redisTemplate.opsForValue().increment(key, 1).toString(), 4, '0');
    }


}
