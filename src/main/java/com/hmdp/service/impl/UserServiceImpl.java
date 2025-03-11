package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //验证手机格式是否正确
        if (RegexUtils.isPhoneInvalid(phone)) {
            //如果不正确，返回错误信息
            return Result.fail("手机格式不正确");
        }
        //符合生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存验证码和手机号到Redis
        stringRedisTemplate.opsForValue().
                set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        stringRedisTemplate.opsForValue().
                set(LOGIN_PHONE_KEY + phone, phone, LOGIN_PHONE_TTL, TimeUnit.MINUTES);
        //发送验证码
        log.debug("发送验证码:{}",code);
        //返回ok
        return Result.ok();
    }

    //登录功能
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        //验证手机格式是否正确
        if (RegexUtils.isPhoneInvalid(phone)) {
            //如果不正确，返回错误信息
            return Result.fail("手机格式不正确");
        }
        //验证验证码是否正确
        String codeVerify = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String phoneVerify = stringRedisTemplate.opsForValue().get(LOGIN_PHONE_KEY + phone);
        if (codeVerify == null ||
                !code.equals(codeVerify)
                || !phone.equals(phoneVerify)){
            //如果不正确，返回错误信息
            return Result.fail("验证码不正确");
        }
        //查询用户
        User user = query().eq("phone", phone).one();
        //如果不存在，创建新用户并保存
        if (user == null){
            user = new User();
            user.setPhone(phone);
            user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
            user.setCreateTime(LocalDateTime.now());
            save(user);
        }
        //保存用户信息到Redis
        //生成token
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<Object, Object> userMap = new HashMap<>();
        userMap.put("id",userDTO.getId().toString());
        userMap.put("nickName",userDTO.getNickName());
        userMap.put("icon",userDTO.getIcon());
        stringRedisTemplate.opsForHash()
                .putAll(LOGIN_USER_KEY+token,userMap);
        //设置token有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.MINUTES);
        //返回token
        return Result.ok(token);
    }
}
