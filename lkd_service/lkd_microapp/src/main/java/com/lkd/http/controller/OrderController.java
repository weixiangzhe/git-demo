package com.lkd.http.controller;


import com.ecwid.consul.v1.ConsulClient;
import com.lkd.common.VMSystem;
import com.lkd.config.ConsulConfig;
import com.lkd.exception.LogicException;
import com.lkd.feignService.OrderService;
import com.lkd.feignService.VMService;
import com.lkd.utils.DistributedLock;
import com.lkd.viewmodel.RequestPay;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/order")
@Slf4j
public class OrderController {
    @Autowired
    private OrderService orderService;
    @Autowired
    private VMService vmService;

    @Autowired
    private ConsulConfig consulConfig;
@Autowired
    private RedisTemplate redisTemplate;

    /**
     * 小程序请求支付
     *
     * @param requestPay
     * @return
     */
    @PostMapping("/requestPay")
    public String requestPay(@RequestBody RequestPay requestPay) {

        if (!vmService.hasCapacity(requestPay.getInnerCode(), Long.parseLong(requestPay.getSkuId()))) {
            throw new LogicException("该商品已售空");
        }
        //分布式锁，每次同一时间只能处理一个商品
        DistributedLock lock = new DistributedLock(
                consulConfig.getConsulRegisterHost(),
                consulConfig.getConsulRegisterPort());
        DistributedLock.LockContext lockContext = lock.getLock(requestPay.getInnerCode(), 60);
        if (!lockContext.isGetLock()) {
            throw new LogicException("机器出货中请稍后再试");
        }
        //存入redis后是为了释放锁
        redisTemplate.boundValueOps(VMSystem.VM_LOCK_KEY_PREF+requestPay.getInnerCode())
                .set(lockContext.getSession(), Duration.ofSeconds(60));
        return orderService.requestPay(requestPay);
    }
}
