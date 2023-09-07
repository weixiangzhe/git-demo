package com.lkd.http.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.lkd.common.VMSystem;
import com.lkd.conf.OrderConfig;
import com.lkd.contract.OrderCheck;
import com.lkd.emq.MqttProducer;
import com.lkd.entity.OrderEntity;
import com.lkd.http.viewModel.CreateOrderReq;
import com.lkd.http.viewModel.OrderResp;
import com.lkd.service.OrderService;
import com.lkd.viewmodel.RequestPay;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/17
 */
@RestController
@RequestMapping("/wxpay")
@Slf4j
public class WxPayController {


    @Autowired
    private OrderService orderService;
    @Autowired
    private MqttProducer mqttProducer;

    /**
     * 微信小程序支付
     *
     * @param requestPay
     * @return
     */
    @PostMapping("/requestPay")
    public String requestPay(@RequestBody RequestPay requestPay) {
        CreateOrderReq createOrderReq = new CreateOrderReq();
        createOrderReq.setSkuId(Long.parseLong(requestPay.getSkuId()));
        createOrderReq.setInnerCode(requestPay.getInnerCode());
        createOrderReq.setPayType("2");
        //创建订单
        OrderResp order = orderService.createOrder(createOrderReq);
        //在这步其实是根据微信支付结果来选择执行回调还是延时队列
        //发送延时队列，如果十几分钟订单没有支付
        OrderCheck orderCheck = new OrderCheck();
        orderCheck.setOrderNO(order.getOrderNo());
        try {
            mqttProducer.send("$delayed/10/" + OrderConfig.ORDER_DELAY_CHECK_TOPIC, 2, orderCheck);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        //发起支付请求.........................

        //支付完成之后回调
        // this.notify(order);
        return "success";


    }


    private void notify(OrderResp orderResp) {
        //修改支付状态
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setId(Long.parseLong(orderResp.getId()));
        orderEntity.setStatus(VMSystem.ORDER_STATUS_PAYED);

        orderEntity.setPayStatus(VMSystem.PAY_STATUS_PAYED);

        orderService.updateById(orderEntity);
        //发送出货
        orderService.payComplete(orderResp.getOrderNo());
    }

}
