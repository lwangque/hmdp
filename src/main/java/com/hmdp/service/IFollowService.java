package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 查询是否关注
     * @param followUserId
     * @return
     */
    Result isFollow(Long followUserId);

    /**
     * 关注或取关
     * @param followUserId
     * @param action
     * @return
     */
    Result follow(Long followUserId, Boolean action);

    /**
     * 查询共同关注
     * @param id
     * @return
     */
    Result followCommons(Long id);
}
