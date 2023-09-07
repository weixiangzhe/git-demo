package com.lkd.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lkd.annotations.ProcessType;
import com.lkd.common.VMSystem;
import com.lkd.contract.OrderCheck;
import com.lkd.entity.OrderEntity;
import com.lkd.service.OrderService;
import com.lkd.utils.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.IOException;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/18
 */
@Component
@ProcessType("orderCheck")
public class OrderCheckHandler implements MsgHandler {
    @Autowired
    private OrderService orderService;

    @Override
    public void process(String jsonMsg) throws IOException {
        System.out.println("-----------------》订单状态失效");
        OrderCheck orderCheck = JsonUtil.getByJson(jsonMsg, OrderCheck.class);
        OrderEntity byOrderNo = orderService.getByOrderNo(orderCheck.getOrderNO());
        if (byOrderNo.getStatus()==VMSystem.ORDER_STATUS_CREATE){
            UpdateWrapper<OrderEntity> updateWrapper = new UpdateWrapper<>();
            updateWrapper.lambda().eq(OrderEntity::getOrderNo, orderCheck.getOrderNO())
                    .set(OrderEntity::getStatus, VMSystem.ORDER_STATUS_INVALID);
            orderService.update(updateWrapper);
            System.out.println("修改状态成功!");
        }

    }
}
