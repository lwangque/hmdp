package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 根据id查询博客
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * 查询热门博客
     * @param current
     * @return
     */
    Result querHotBlog(Integer current);

    /**
     * 点赞博客
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 点赞排行榜
     * @return
     */
    Result top5Likes(Long id);

    /**
     * 发布博客
     * @param blog
     * @return
     */
    Result saveBlog(Blog blog);

    /**
     * 查询关注人的博客
     * @param max
     * @param offset
     * @return
     */
    Result queryBlogOfFollow(Long max, Integer offset);
}
