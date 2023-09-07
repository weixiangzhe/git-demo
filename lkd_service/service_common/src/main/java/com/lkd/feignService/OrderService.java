package com.lkd.feignService;

import com.lkd.feignService.fallback.OrderServiceFallbackFactory;
import com.lkd.viewmodel.OrderViewModel;
import com.lkd.viewmodel.Pager;
import com.lkd.viewmodel.RequestPay;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(value = "order-service",fallbackFactory = OrderServiceFallbackFactory.class)
public interface OrderService {

    @GetMapping("/order/businessTop10Skus/{businessId}")
    List<Long> getBusinessTop10Skus(@PathVariable("businessId") Integer businessId);

    @PostMapping("/wxpay/requestPay")
     String requestPay(@RequestBody RequestPay requestPay);

    @GetMapping("/order/search")
     Pager<OrderViewModel> search(@RequestParam(value = "pageIndex", required = false, defaultValue = "1") Integer pageIndex,
                                        @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize,
                                        @RequestParam(value = "orderNo", required = false) String orderNo,
                                        @RequestParam(value = "start", required = false) String start,
                                        @RequestParam(value = "end", required = false) String end);
}
