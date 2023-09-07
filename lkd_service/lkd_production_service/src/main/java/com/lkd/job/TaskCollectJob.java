package com.lkd.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lkd.common.VMSystem;
import com.lkd.entity.TaskCollectEntity;
import com.lkd.entity.TaskEntity;
import com.lkd.service.TaskCollectService;
import com.lkd.service.TaskService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/24
 */
@Component
public class TaskCollectJob {
    @Autowired
    private TaskService taskService;
    @Autowired
    private TaskCollectService taskCollectService;


    @XxlJob("taskCollectJobHandler")
    public ReturnT<String> collectTask(String param) {

        cleanTask();

        TaskCollectEntity taskCollectEntity = new TaskCollectEntity();
        //获取前一天的时间
        LocalDate start = LocalDate.now().plusDays(-1);
        //进行中的工单
        taskCollectEntity.setProgressCount(this.count(start, VMSystem.TASK_STATUS_PROGRESS));
        //取消或者拒绝工单
        taskCollectEntity.setCancelCount(this.count(start, VMSystem.TASK_STATUS_CANCEL));
        //完成工单
        taskCollectEntity.setFinishCount(this.count(start, VMSystem.TASK_STATUS_FINISH));
        //日期
        taskCollectEntity.setCollectDate(start);
        //添加
        taskCollectService.save(taskCollectEntity);
        return ReturnT.SUCCESS;
    }

    /**
     * 按时间和状态进行统计
     *
     * @param start
     * @param taskStatus
     * @return
     */
    private int count(LocalDate start, Integer taskStatus) {
        LambdaQueryWrapper<TaskEntity> qw = new LambdaQueryWrapper<>();
        qw.ge(TaskEntity::getUpdateTime, start)
                .le(TaskEntity::getUpdateTime, start.plusDays(1))
                .eq(TaskEntity::getTaskStatus, taskStatus);
        return taskService.count(qw);
    }

    /**
     * 清理无效工单
     */
    private void cleanTask() {
        LambdaUpdateWrapper<TaskEntity> uw = new LambdaUpdateWrapper<>();
        uw.lt(TaskEntity::getUpdateTime, LocalDate.now())
                .and(w -> w.eq(TaskEntity::getTaskStatus, VMSystem.TASK_STATUS_CREATE)).or().eq(TaskEntity::getTaskStatus, VMSystem.TASK_STATUS_PROGRESS)
                .set(TaskEntity::getTaskStatus, VMSystem.TASK_STATUS_CANCEL)
                .set(TaskEntity::getDesc, "订单超时");
        taskService.update(uw);

    }

}
