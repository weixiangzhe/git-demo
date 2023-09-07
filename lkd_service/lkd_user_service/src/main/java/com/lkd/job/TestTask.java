package com.lkd.job;

import com.lkd.common.VMSystem;
import com.lkd.entity.UserEntity;
import com.lkd.service.UserService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @Description
 * @Author susu
 * @Date 2023/8/14
 */
@Component
public class TestTask {

    @Autowired
    private UserService userService;
    @Autowired
    private RedisTemplate redisTemplate;

    @XxlJob("workCountInitJobHandler")
    public ReturnT<String> testHandler(String parm) {
        List<UserEntity> userEntityList = userService.list();


        userEntityList.forEach(user -> {
                //以固定字符串（前缀）+时间+区域+工单类别（运营/运维）/
                String key = VMSystem.REGION_TASK_KEY_PREF+
                          LocalDate.now().plusDays(0)
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd"))+"."+
                        user.getRegionId()+"."+user.getRoleCode();


                redisTemplate.opsForZSet().add(key,user.getId(),0);
                redisTemplate.expire(key, Duration.ofDays(2));

        });


        return ReturnT.SUCCESS;
    }
}
