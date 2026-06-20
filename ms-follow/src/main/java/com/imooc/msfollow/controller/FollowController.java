package com.imooc.msfollow.controller;

import com.imooc.commons.constant.ApiConstant;
import com.imooc.commons.model.domain.ResultInfo;
import com.imooc.commons.utils.ResultInfoUtil;
import com.imooc.msfollow.service.FollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.Set;

/**
 * 关注/取关控制层
 */
@RestController
public class FollowController {

    @Resource
    private FollowService followService;
    @Resource
    private HttpServletRequest request;

    /**
     * 获取粉丝列表
     *
     * @param dinerId
     * @return
     */
    @GetMapping("followers/{dinerId}")
    public ResultInfo findFollowers(@PathVariable Integer dinerId) {
        System.out.println("=== Follow服务开始 ===");

        try {
            Set<Integer> followers = followService.findFollowers(dinerId);
            System.out.println("1. 查询到的粉丝：" + followers);

            ResultInfo result = ResultInfoUtil.buildSuccess(request.getServletPath(), followers);
            System.out.println("2. 返回的ResultInfo对象：" + result);
            System.out.println("3. 返回的code：" + result.getCode());
            System.out.println("4. 期望的SUCCESS_CODE：" + ApiConstant.SUCCESS_CODE);

            // 检查是否为1
            if (result.getCode() != ApiConstant.SUCCESS_CODE) {
                System.out.println("警告：返回的code不是1！");
            }

            return result;

        } catch (Exception e) {
            System.out.println("异常：" + e.getMessage());
            e.printStackTrace();
            return ResultInfoUtil.buildError(0, "Oops! Something was wrong.", "/");
        }
    }

    /**
     * 共同关注列表
     *
     * @param dinerId
     * @param access_token
     * @return
     */
    @GetMapping("commons/{dinerId}")
    public ResultInfo findCommonsFriends(@PathVariable Integer dinerId,
                                         String access_token) {
        return followService.findCommonsFriends(dinerId, access_token, request.getServletPath());
    }

    /**
     * 关注/取关
     *
     * @param followDinerId 关注的食客ID
     * @param isFollowed    是否关注 1=关注 0=取消
     * @param access_token  登录用户token
     * */
    @GetMapping("/{followDinerId}")
    public ResultInfo follow(@PathVariable Integer followDinerId,
                             @RequestParam int isFollowed,
                             String access_token) {
        ResultInfo resultInfo = followService.follow(followDinerId, isFollowed, access_token
                , request.getServletPath());
        return resultInfo;
    }


}