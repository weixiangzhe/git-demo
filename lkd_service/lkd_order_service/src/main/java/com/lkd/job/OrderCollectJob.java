package com.lkd.job;
import java.time.LocalDate;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lkd.common.VMSystem;
import com.lkd.entity.OrderCollectEntity;
import com.lkd.entity.OrderEntity;
import com.lkd.feignService.UserService;
import com.lkd.feignService.VMService;
import com.lkd.service.OrderCollectService;
import com.lkd.service.OrderService;
import com.lkd.viewmodel.PartnerViewModel;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/22
 */
@Component
public class OrderCollectJob {
    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderCollectService orderCollectService;

    @Autowired
    private VMService vmService;
    @Autowired
    private UserService userService;


    /**
     * 订单合并
     * @param param
     * @return
     */

    @XxlJob("orderCollectJobHandler")
    public ReturnT<String>collectTask(String param){
        //得到前一天日期
        LocalDate dateTime = LocalDate.now().plusDays(-1);

        //大于规定时间,小于当前时间,获取支付状态为已支付的,设置分组
        QueryWrapper<OrderEntity> qw = new QueryWrapper<>();
        qw.select("owner_id", "node_id", "IFNULL(sum(amount),0) as amount", "IFNULL(sum(bill),0) as bill", "IFNULL(count(1),0) as price")
                .lambda().ge(OrderEntity::getCreateTime, dateTime)
                .lt(OrderEntity::getCreateTime, LocalDate.now())
                .eq(OrderEntity::getPayStatus, VMSystem.PAY_STATUS_PAYED)
                .groupBy(OrderEntity::getOwnerId, OrderEntity::getNodeId);

        List<OrderEntity> list = orderService.list(qw);

        for (OrderEntity order : list) {
            //根据合作商的id获取合作上的信息
            PartnerViewModel partner = userService.getPartner(order.getOwnerId());
            OrderCollectEntity orderCollect =new OrderCollectEntity();
            orderCollect.setOwnerId(order.getOwnerId());
            orderCollect.setOwnerName(partner.getName());
            orderCollect.setNodeId(order.getNodeId());
            //根据点位id获取合作商的姓名
            String nodeName = vmService.getNodeName(order.getNodeId());
            orderCollect.setNodeName(nodeName);
            orderCollect.setTotalBill(order.getBill());
            orderCollect.setOrderCount(order.getPrice());
            orderCollect.setOrderTotalMoney(order.getAmount());
            orderCollect.setDate(dateTime);
            orderCollect.setRatio(partner.getRatio());
            orderCollectService.save(orderCollect);
        }


        return ReturnT.SUCCESS;
    }

}
