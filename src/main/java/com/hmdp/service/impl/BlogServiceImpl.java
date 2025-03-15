package com.hmdp.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据id查询博客
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        // 根据id查询博客
        Blog blog = getById(id);
        if (blog == null){
            return Result.fail("博客不存在");
        }
        // 查询博客作者
        User user = userService.getById(blog.getUserId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
        //判断用户是否点赞
        this.isLikedBlog(blog);
        return Result.ok(blog);
    }

    @Override
    public Result querHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            //判断用户是否点赞
            this.isLikedBlog(blog);
        });
        return Result.ok(records);
    }

    /**
     * 点赞
     * @param id
     */
    @Override
    public Result likeBlog(Long id) {
        //查询博客是否存在
        Blog byId = getById(id);
        if (byId == null){
            return Result.fail("博客不存在");
        }
        //得到用户id
        Long useId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY +id;
        //判断是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, useId.toString());
        if (score != null)  {
            //是，赞数减一
            boolean update = update().setSql("liked = liked - 1").eq("id", id).update();
            //从redis移除用户id
            if (update){
                stringRedisTemplate.opsForZSet().remove(key,useId.toString());
            }
        }else{
            //否，赞数加一
            boolean update = update().setSql("liked = liked + 1").eq("id", id).update();
            //增加用户id到redis
            if (update){
                stringRedisTemplate.opsForZSet().add(key,useId.toString(),System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    /**
     * 查询点赞top5
     * @param id
     * @return
     */
    @Override
    public Result top5Likes(Long id) {
        //查询top5的点赞用户
        Set<String> range =
                stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        //解析出其中的id
        if (range == null || range.isEmpty()){
            return Result.ok();
        }
        List<Long> ids =
                range.stream().map(Long::valueOf).collect(Collectors.toList());
        //根据用户id查询用户
        String join = StrUtil.join(",", ids);
        List<User> users = userService.query().
                in("id",ids)
                .last("order by field(id,"+ join +")")
                .list();
        List<UserDTO> userDTOs = users.stream().
                map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOs);
    }

    /**
     * 判断用户是否点赞
     * @param blog
     */
    private void isLikedBlog(Blog blog) {
        //得到用户id
        UserDTO user = UserHolder.getUser();
        if (user == null){
            return;
        }
        String key = BLOG_LIKED_KEY +blog.getId();
        //判断是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, user.getId().toString());
        blog.setIsLike(score != null);
    }
}
