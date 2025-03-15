package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisWorker redisWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 秒杀优惠券
     * @param voucherId 优惠券id
     * @return
     */
    @Override

    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠卷
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //2.判断是否在秒杀时间段内
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经结束");
        }
        //3.判断库存是否充足
        if (seckillVoucher.getStock()<=0){
            return Result.fail("库存不足");
        }
        //4得到用户id
        UserDTO user = UserHolder.getUser();

        //获取锁对象
        SimpleRedisLock lock =
                new SimpleRedisLock(stringRedisTemplate, "oneOrder:"+user.getId());
        try {
            //判断获取锁是否成功
            if (!lock.tryLock(5)){
                return Result.fail("请勿重复下单");
            }
            //获取代理对象（事物）
            IVoucherOrderService proxy =
                    (IVoucherOrderService)AopContext.currentProxy();
            //8.返回订单id
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unLock();
        }

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {

        UserDTO user = UserHolder.getUser();
        //5.判断用户是否已经抢过一单
        Integer count = query().
                eq("user_id", user.getId()).
                eq("voucher_id", voucherId).count();
        if (count>0){
            return Result.fail("不能重复下单");
        }
        //6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)//乐观锁
                .update();
        if (!success){
            return Result.fail("库存不足");
        }
        //7.创建订单
        //7.1获得订单id
        long id = redisWorker.uniqueId("seckillVoucherOrder");
        //保存订单
        save(VoucherOrder.builder()
                .voucherId(voucherId)
                .userId(user.getId())
                .id(id).build());
        return Result.ok(id);
    }
}
