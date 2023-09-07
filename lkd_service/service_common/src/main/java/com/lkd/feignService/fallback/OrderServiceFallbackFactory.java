package com.lkd.feignService.fallback;

import com.google.common.collect.Lists;
import com.lkd.feignService.OrderService;
import com.lkd.viewmodel.OrderViewModel;
import com.lkd.viewmodel.Pager;
import com.lkd.viewmodel.RequestPay;
import feign.hystrix.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class OrderServiceFallbackFactory implements FallbackFactory<OrderService> {
    @Override
    public OrderService create(Throwable throwable) {
        log.error("订单服务调用失败",throwable);
        return new OrderService() {

            @Override
            public List<Long> getBusinessTop10Skus(Integer businessId) {
                return Lists.newArrayList();
            }

            @Override
            public String requestPay(RequestPay requestPay) {
                return "111111";
            }

            @Override
            public Pager<OrderViewModel> search(Integer pageIndex, Integer pageSize, String orderNo, String start, String end) {
                return null;
            }
        };
    }
}
