package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private StringRedisTemplate stringRedisTemplate;
    //业务名称
    private String name;
    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }
    private static final String KEY_PREFIX = "lock:";
    private static final String PRE_UUID = UUID.randomUUID().toString(true)+"-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的超时时间，过期自动释放
     * @return
     */
    public boolean tryLock(long timeoutSec) {
        //获取用户Id
        String ThreadId = PRE_UUID+Thread.currentThread().getId();
        //使用setnx的互斥性
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent
                (KEY_PREFIX+name , ThreadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁
     */
    public void unLock() {
//        //判断是否是自己的锁
//        if ((PRE_UUID+Thread.currentThread().getId())
//                .equals(stringRedisTemplate.opsForValue().get(KEY_PREFIX+name))){
//            stringRedisTemplate.delete(KEY_PREFIX+name);
//        }
        //删除锁，使用lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX+name)
                ,PRE_UUID+Thread.currentThread().getId());
    }
}
