package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;
    //查询是否关注
    @Override
    public Result isFollow(Long followUserId) {
        //获得当前用户id
        Long userId = UserHolder.getUser().getId();
        //查询是否关注
        Integer count = query().
                eq("user_id", userId).
                eq("follow_user_id", followUserId)
                .count();
        return Result.ok(count>0);
    }

    //关注或取关
    @Override
    public Result follow(Long followUserId, Boolean action) {
        //获得当前用户id
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        if (action){
            //关注
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            follow.setCreateTime(LocalDateTime.now());
            boolean isSuccess = save(follow);
            if (isSuccess){
                //保存关注的id到redis
                stringRedisTemplate.opsForSet()
                        .add(key,followUserId.toString());
            }
        }else{
            //取关
            boolean remove = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if (remove){
                //从redis移除关注用户id
                stringRedisTemplate.opsForSet()
                        .remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    //查询共同关注
    @Override
    public Result followCommons(Long id) {
        //获得当前用户id
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        String key2 = "follows:" + id;
        //查询共同关注
        Set<String> ids = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (ids == null || ids.isEmpty()){
            //没有共同关注
            return Result.ok(Collections.emptyList());
        }
        List<Long> collect = ids.stream().map(Long::valueOf).collect(Collectors.toList());
        List<User> users = userService.listByIds(collect);
        List<UserDTO> userDTOS = users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
