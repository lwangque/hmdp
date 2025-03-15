package com.hmdp;

import com.hmdp.service.impl.UserServiceImpl;
import com.hmdp.utils.RedisWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

//@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private RedisWorker redisWorker;
    @Test
    public void test(){
        /*LocalDateTime now = LocalDateTime.of(2025,3,12,18,00,00,00);
        long epochSecond = now.toEpochSecond(ZoneOffset.UTC);
        System.out.println(epochSecond);*/
    }

}
