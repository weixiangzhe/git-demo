package com.lkd.business;

import com.google.common.collect.Lists;

import com.lkd.annotations.ProcessType;
import com.lkd.common.VMSystem;
import com.lkd.contract.SupplyCfg;
import com.lkd.contract.SupplyChannel;
import com.lkd.entity.TaskDetailsEntity;
import com.lkd.feignService.VMService;
import com.lkd.http.viewModel.TaskDetailsViewModel;
import com.lkd.http.viewModel.TaskViewModel;
import com.lkd.service.TaskService;
import com.lkd.utils.JsonUtil;
import com.lkd.viewmodel.VendingMachineViewModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/16
 */
@Component
@ProcessType("supplyTask")
public class SupplyTaskHandler implements MsgHandler {
    @Autowired
    private VMService vmService;
    @Autowired
    private TaskService taskService;

    @Override
    public void process(String jsonMsg) throws IOException {


        //1.解析报文
        SupplyCfg supplyCfg = JsonUtil.getByJson(jsonMsg, SupplyCfg.class);
        //找出指定的人
        VendingMachineViewModel vm = vmService.getVMInfo(supplyCfg.getInnerCode());
        Integer userId = taskService.getLeastUser(vm.getRegionId().toString(), false);
        //创建补货工单
        TaskViewModel taskViewModel = new TaskViewModel();
        taskViewModel.setCreateType(0);
        taskViewModel.setInnerCode(supplyCfg.getInnerCode());
        taskViewModel.setAssignorId(userId);
        taskViewModel.setProductType(VMSystem.TASK_TYPE_SUPPLY);
        taskViewModel.setDesc("自动补货工单");
        List<SupplyChannel> supplyData = supplyCfg.getSupplyData();
        List<TaskDetailsViewModel> taskDetailsEntityList = Lists.newArrayList();

        for (SupplyChannel supplyDatum : supplyData) {
            TaskDetailsViewModel taskDetails = new TaskDetailsViewModel();
            taskDetails.setChannelCode(supplyDatum.getChannelId());
            taskDetails.setExpectCapacity(supplyDatum.getCapacity());
            taskDetails.setSkuId(supplyDatum.getSkuId());
            taskDetails.setSkuName(supplyDatum.getSkuName());
            taskDetails.setSkuImage(supplyDatum.getSkuImage());
            taskDetailsEntityList.add(taskDetails);
        }

        taskViewModel.setDetails(taskDetailsEntityList);
        taskService.createTask(taskViewModel);
    }
}
