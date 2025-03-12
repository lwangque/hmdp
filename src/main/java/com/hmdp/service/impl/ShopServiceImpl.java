package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    public Result queryById(Long id) {
        Shop shop;
        //查询redis中是否有该商铺
        String s = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //有直接返回
        if (StrUtil.isNotBlank(s)){
            shop = JSONUtil.toBean(s, Shop.class);
            return Result.ok(shop);
        }

        if (s != null){
            return Result.fail("店铺不存在");
        }
        //没有，去数据库查
        shop = getById(id);
        //判断数据库中是否有该商铺
        if (shop == null){
            //没有返回错误信息
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,"");
            stringRedisTemplate.expire("", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }
        //有，将其插入redis并返回数据
        String jsonStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,jsonStr);
        stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    //更新店铺信息
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null){

            return Result.fail("店铺id不能为空");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
