package com.lkd.http.controller;

import com.lkd.service.OrderService;
import com.lkd.viewmodel.OrderViewModel;
import com.lkd.viewmodel.Pager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;


    /**
     * 获取商圈下3个月内销量前10商品
     * @param businessId
     * @return
     */

    @GetMapping("/businessTop10/{businessId}")
    public  List<Long> getBusinessTop10Skus(@PathVariable Integer businessId){
    return orderService.getTop10Sku(businessId);
    }



    /**
     * 取消订单
     *
     * @param orderNo
     * @return
     */
    @GetMapping("/cancel/{orderNo}")
    public Boolean cancel(@PathVariable String orderNo) {

        return orderService.cancel(orderNo);
    }

    @GetMapping("/search")
    public Pager<OrderViewModel> search(@RequestParam(value = "pageIndex", required = false, defaultValue = "1") Integer pageIndex,
                                        @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize,
                                        @RequestParam(value = "orderNo", required = false) String orderNo,
                                        @RequestParam(value = "start", required = false) String start,
                                        @RequestParam(value = "end", required = false) String end) {
        return orderService.search(pageIndex, pageSize, orderNo, start, end);
    }


}
