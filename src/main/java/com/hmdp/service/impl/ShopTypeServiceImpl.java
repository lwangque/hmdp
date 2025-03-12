package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        List<ShopType> shopTypeList;
        //查询redis中是否有该商铺
        String s = stringRedisTemplate.opsForValue().get("cache:shopList");
        //有直接返回
        if (StrUtil.isNotBlank(s)){
            shopTypeList = JSONUtil.toList(s, ShopType.class);
            return Result.ok(shopTypeList);
        }
        //没有，去数据库查
        shopTypeList = query().orderByAsc("sort").list();
        //判断数据库中是否有该商铺
        if (shopTypeList == null || shopTypeList.isEmpty()){
            //没有返回错误信息
            return Result.fail("店铺不存在");
        }
        //有，将其插入redis并返回数据
        String jsonStr = JSONUtil.toJsonStr(shopTypeList);
        stringRedisTemplate.opsForValue().set("cache:shopList",jsonStr);

        return Result.ok(shopTypeList);
    }
}
