package com.lkd.business.msgHandler;

import com.lkd.annotations.ProcessType;
import com.lkd.business.MsgHandler;
import com.lkd.common.VMSystem;
import com.lkd.contract.TaskCompleteContract;
import com.lkd.service.VendingMachineService;
import com.lkd.utils.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/11
 */
@Component
@ProcessType("taskCompleted")
public class TaskCompletedMsgHandler implements MsgHandler {
    @Autowired
    private VendingMachineService vendingMachineService;

    @Override
    public void process(String jsonMsg) throws IOException {



        //当工单完成操作执行完毕，回想EMQ中发送数据，其中包括msgType：为taskCompleted
         //根据自定义注解获取到完成发送的数据进行接受转换
        TaskCompleteContract taskCompleteContract = JsonUtil.getByJson(jsonMsg, TaskCompleteContract.class);

        //如果是投放工单，将售货机修改为运营状态
        if (taskCompleteContract.getTaskType() == VMSystem.TASK_TYPE_DEPLOY) {
            vendingMachineService.updateStatus(taskCompleteContract.getInnerCode(), VMSystem.VM_STATUS_RUNNING, taskCompleteContract.getLat(), taskCompleteContract.getLon());
        }

        //如果是撤机工单，将售货机修改为撤机状态

        if (taskCompleteContract.getTaskType() == VMSystem.VM_STATUS_REVOKE) {
            vendingMachineService.updateStatus(taskCompleteContract.getInnerCode(), VMSystem.VM_STATUS_REVOKE , taskCompleteContract.getLat(), taskCompleteContract.getLon());

        }


    }
}
