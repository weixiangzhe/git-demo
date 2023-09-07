package com.lkd.business;

import com.google.common.collect.Lists;

import com.lkd.annotations.ProcessType;
import com.lkd.common.VMSystem;
import com.lkd.contract.StatusInfo;
import com.lkd.contract.VmStatusContract;
import com.lkd.entity.TaskEntity;
import com.lkd.feignService.VMService;
import com.lkd.http.viewModel.TaskViewModel;
import com.lkd.service.TaskService;
import com.lkd.utils.JsonUtil;
import com.lkd.viewmodel.VendingMachineViewModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.IOException;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/15
 */
@Component
@ProcessType(value = "vmStatus")
public class VMStatusHandler implements MsgHandler {
    @Autowired
    private TaskService taskService;
    @Autowired
    private VMService vmService;

    /**
     * {
     * "msgType":"vmStatus",
     * "sn":342424343,
     * "vmId":"01000001",
     * "statusInfo":
     * [{
     * "statusCode":"1001",
     * "status":true,
     * }]
     * }
     */
    @Override
    public void process(String jsonMsg) throws IOException {

        System.out.println("上报设备部件状态，开始执行自动化.....");
        System.out.println(jsonMsg);
        //解析报文
        VmStatusContract vmStatusContract = JsonUtil.getByJson(jsonMsg, VmStatusContract.class);
        if (ObjectUtils.isEmpty(vmStatusContract)) return;
        for (StatusInfo statusInfo : vmStatusContract.getStatusInfo()) {
            //如果报文中的status是flaes的话创建维修工单
            if (!statusInfo.isStatus()) {
                //根据售货机编号查询售货机
                VendingMachineViewModel vmInfo = vmService.getVMInfo(vmStatusContract.getInnerCode());
                //  地区编号  查询最少得对象
                Integer leastUser = taskService.getLeastUser(vmInfo.getRegionId().toString(), true);
                //创建工单对象
                TaskViewModel taskViewModel = new TaskViewModel();
                taskViewModel.setCreateType(0);
                taskViewModel.setInnerCode(vmInfo.getInnerCode());
                taskViewModel.setAssignorId(leastUser);
                taskViewModel.setProductType(VMSystem.TASK_TYPE_REPAIR);
                taskViewModel.setDesc("自动化工单");
                taskService.createTask(taskViewModel);
                return;


            }
        }
    }
}
