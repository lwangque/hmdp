package com.hmdp.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.ScrollResult;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    @Autowired
    private IFollowService followService;

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
     * 保存博客
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess){
            return Result.fail("新增博文失败！");
        }
        //获取博文id
        Long blogId = blog.getId();
        //获取当前用户所有粉丝的id
        List<Follow> followeds = followService.query()
                .eq("follow_user_id", user.getId()).list();
        List<Long> ids = followeds.stream()
                .map(Follow::getUserId)
                .collect(Collectors.toList());
        if (ids.isEmpty()){
            return Result.ok();
        }
        for (Long id : ids){
            //推送至粉丝
            stringRedisTemplate.opsForZSet().add(
                    "feed:" + id,
                    blogId.toString(),
                    System.currentTimeMillis()
            );
        }
        return Result.ok();
    }

    /**
     * 查询用户关注人的最新博文
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //查询用户id
        Long userId = UserHolder.getUser().getId();
        //查询收件箱
        String key = "feed:" + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // 判断是否为空
        if (typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        // 非空，解析数据
        List<Long> ids = new ArrayList<>(2);
        long minSorce = 0;
        Integer count = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples){
            ids.add(Long.valueOf(tuple.getValue()));
            if (tuple.getScore().longValue() == minSorce){
                count++;
            }else{
                minSorce = tuple.getScore().longValue();
                count = 1;
            }
        }
        //2.根据id查询blog
        String join = StrUtil.join(",", ids);
        List<Blog> Blogs = query().in("id", ids)
                .last("order by field(id," + join + ")")
                .list();
        for (Blog blog : Blogs){
            Long userBlogId = blog.getUserId();
            User user = userService.getById(userBlogId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            this.isLikedBlog(blog);
        }
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(Blogs);
        scrollResult.setOffset(count);
        scrollResult.setMinTime(minSorce);
        return Result.ok(scrollResult);
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
