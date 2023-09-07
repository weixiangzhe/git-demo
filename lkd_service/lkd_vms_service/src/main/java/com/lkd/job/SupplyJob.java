package com.lkd.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lkd.common.VMSystem;
import com.lkd.entity.VendingMachineEntity;
import com.lkd.feignService.TaskService;
import com.lkd.service.VendingMachineService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/15
 */
@Component
public class SupplyJob {

    @Autowired
    private VendingMachineService vendingMachineService;
    @Autowired
    private TaskService taskService;

    @XxlJob("supplyHandler")
    public ReturnT<String> supplyHandler(String parm) {

        //获取警戒比
        Integer percent = taskService.getSupplyAlertValue();

        LambdaQueryWrapper<VendingMachineEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(VendingMachineEntity::getVmStatus, VMSystem.VM_STATUS_RUNNING);
        List<VendingMachineEntity> list = vendingMachineService.list(qw);
        list.forEach(v -> {
            System.out.println(v);
            //售货机缺货货道数量
            int count = vendingMachineService.inventory(percent, v);
           if (count>0){
               System.out.println("售货机"+v.getInnerCode()+"需要补货");
               //发送补货消息
                vendingMachineService.sendSupplyTask(v);
           }

        });
        return ReturnT.SUCCESS;
    }


}
