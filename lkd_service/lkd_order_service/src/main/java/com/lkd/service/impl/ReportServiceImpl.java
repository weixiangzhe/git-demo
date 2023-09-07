package com.lkd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.base.Strings;
import com.lkd.contract.BarCharCollect;
import com.lkd.contract.BaseContract;
import com.lkd.entity.OrderCollectEntity;
import com.lkd.service.OrderCollectService;
import com.lkd.service.ReportService;
import com.lkd.viewmodel.Pager;
import com.sun.org.apache.bcel.internal.generic.NEW;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/23
 */
@Service
public class ReportServiceImpl implements ReportService {
    @Autowired
    private OrderCollectService orderCollectService;

    @Override
    public Pager<OrderCollectEntity> getPartnerCollect(Long pageIndex, Long pageSize, String name, LocalDate start, LocalDate end) {
        Page<OrderCollectEntity> page = new Page<>(pageIndex, pageSize);
        QueryWrapper<OrderCollectEntity> qw = new QueryWrapper<>();
        qw.select("IFNULL(sum(order_count),0) as order_count ",
                "IFNULL(sum(total_bill),0) as total_bill ",
                "IFNULL(sum(order_total_money),0) as order_total_money ",
                "IFNULL(min(ratio),0) as ratio",
                "owner_name",
                "date");
        if (!Strings.isNullOrEmpty(name)) {
            qw.lambda().like(OrderCollectEntity::getNodeName, name);
        }
        qw.lambda()
                .ge(OrderCollectEntity::getDate, start)
                .le(OrderCollectEntity::getDate, end)
                .groupBy(OrderCollectEntity::getOwnerName, OrderCollectEntity::getDate)
                .orderByDesc(OrderCollectEntity::getDate);


        return Pager.build(orderCollectService.page(page, qw));
    }


    @Override
    public List<OrderCollectEntity> getTop12(Integer partnerId) {
        LambdaQueryWrapper<OrderCollectEntity> qw = new LambdaQueryWrapper<>();
        qw.select(OrderCollectEntity::getDate, OrderCollectEntity::getNodeName, OrderCollectEntity::getOrderCount
                        , OrderCollectEntity::getTotalBill)
                .eq(OrderCollectEntity::getOwnerId, partnerId)
                .orderByDesc(OrderCollectEntity::getDate)
                .last("limit 12");
        return orderCollectService.list(qw);
    }

    @Override
    public Pager<OrderCollectEntity> search(Long pageIndex, Long pageSize, Integer partnerId, String nodeName, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<OrderCollectEntity> qw = new LambdaQueryWrapper<>();
        qw.select(OrderCollectEntity::getDate, OrderCollectEntity::getNodeName, OrderCollectEntity::getOrderCount, OrderCollectEntity::getTotalBill)
                .eq(OrderCollectEntity::getOwnerId, partnerId);
        if (!Strings.isNullOrEmpty(nodeName)) {
            qw.like(OrderCollectEntity::getNodeName, nodeName);
        }

        qw
                .ge(OrderCollectEntity::getDate, start)
                .le(OrderCollectEntity::getDate, end)
                .orderByDesc(OrderCollectEntity::getDate);
        Page<OrderCollectEntity> page = new Page<>(pageIndex, pageSize);
        return Pager.build(orderCollectService.page(page, qw));
    }

    @Override
    public List<OrderCollectEntity> getList(Integer partnerId, String nodeName, LocalDate start, LocalDate end) {

        LambdaQueryWrapper<OrderCollectEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(OrderCollectEntity::getOwnerId, partnerId)
                .ge(OrderCollectEntity::getDate, start)
                .le(OrderCollectEntity::getDate, end);
        if (!Strings.isNullOrEmpty(nodeName)) {
            qw.like(OrderCollectEntity::getNodeName, nodeName);
        }
        return orderCollectService.list(qw);
    }

    @Override
    public BarCharCollect getCollect(Integer partnerId, LocalDate start, LocalDate end) {
        QueryWrapper<OrderCollectEntity> qw = new QueryWrapper<>();
        qw
                .select("IFNULL(sum(total_bill),0) as total_bill", "date")
                .lambda()
                .ge(OrderCollectEntity::getDate, start)
                .le(OrderCollectEntity::getDate, end)
                .eq(OrderCollectEntity::getOwnerId, partnerId)
                .orderByDesc(OrderCollectEntity::getDate)
                .groupBy(OrderCollectEntity::getDate);
        List<OrderCollectEntity> list = orderCollectService.list(qw);
        Map<LocalDate, Integer> collect = list.stream().collect(Collectors.toMap(OrderCollectEntity::getDate, OrderCollectEntity::getTotalBill));
        BarCharCollect barCharCollect = new BarCharCollect();
        List<LocalDate> localDates = datesUntil(start, end);
        localDates.forEach(
                date -> {
                    barCharCollect.getXAxis().add(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
                    if (collect.containsKey(date)) {
                        barCharCollect.getYAxis().add(collect.get(date));
                    } else {
                        barCharCollect.getYAxis().add(0);
                    }
                }
        );
        List<Integer> list1 = replaceZeros(barCharCollect.getYAxis());
        barCharCollect.setYAxis(list1);


        return barCharCollect;
    }

    //得到规定时间范围内的每一天的时间
    private static List<LocalDate> datesUntil(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate currentDate = start;

        while (!currentDate.isAfter(end)) {
            dates.add(currentDate);
            currentDate = currentDate.plusDays(1);
        }

        return dates;
    }

    //数据封装
    public static List<Integer> replaceZeros(List<Integer> array) {
        List<Integer> list = new ArrayList<>();
        int nonZeroValue = 0; // 最近的非零值
        for (Integer integer : array) {
            if (integer != 0) {
                nonZeroValue = integer;
                list.add(nonZeroValue);
            } else {
                integer = nonZeroValue;
                list.add(integer);
            }
        }
        return list;
    }


}
