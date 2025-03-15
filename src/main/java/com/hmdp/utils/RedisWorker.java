package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisWorker {

    private StringRedisTemplate stringRedisTemplate;
    public RedisWorker(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //开始时间戳
    private static final long BEGIN_TIMESTAMP = 1741802400L;
    private static final int COUNT_BITS = 32;
    //获得全场唯一id
    public long uniqueId(String keyPrefix){
        //获得当前时间戳
        LocalDateTime now = LocalDateTime.now();
        long epochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp =  epochSecond - BEGIN_TIMESTAMP;
        //生成序列号
        // 获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yy:MM:dd"));
        long incrementuniqueId = stringRedisTemplate.opsForValue().
                increment("uniqueId:" + keyPrefix + ":" + date);
        return timestamp << COUNT_BITS | incrementuniqueId;
    }
}
