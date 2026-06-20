package com.imooc.points.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.imooc.commons.constant.ApiConstant;
import com.imooc.commons.constant.RedisKeyConstant;
import com.imooc.commons.exception.ParameterException;
import com.imooc.commons.model.domain.ResultInfo;
import com.imooc.commons.model.pojo.DinerPoints;
import com.imooc.commons.model.vo.DinerPointsRankVO;
import com.imooc.commons.model.vo.ShortDinerInfo;
import com.imooc.commons.model.vo.SignInDinerInfo;
import com.imooc.commons.utils.AssertUtil;
import com.imooc.points.mapper.DinerPointsMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.*;

/**
 * 积分业务逻辑层
 */
@Slf4j
@Service
public class DinerPointsService {

    @Resource
    private DinerPointsMapper dinerPointsMapper;
    @Resource
    private RestTemplate restTemplate;
    @Resource
    private RedisTemplate redisTemplate;
    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;
    @Value("${service.name.ms-diners-server}")
    private String dinersServerName;
    // 排行榜 TOPN
    private static final int TOPN = 20;


    /**
     * 添加积分
     *
     * @param dinerId 食客ID
     * @param points  积分
     * @param types   类型 0=签到，1=关注好友，2=添加Feed，3=添加商户评论
     */
    @Transactional(rollbackFor = Exception.class)
    public void addPoints(Integer dinerId, Integer points, Integer types) {
        // 基本参数校验
        AssertUtil.isTrue(dinerId == null || dinerId < 1, "食客不能为空");
        AssertUtil.isTrue(points == null || points < 1, "积分不能为空");
        AssertUtil.isTrue(types == null, "请选择对应的积分类型");

        // 插入数据库
        DinerPoints dinerPoints = new DinerPoints();
        dinerPoints.setFkDinerId(dinerId);
        dinerPoints.setPoints(points);
        dinerPoints.setTypes(types);
        dinerPointsMapper.save(dinerPoints);
        // 将积分保存到 Redis
        redisTemplate.opsForZSet().incrementScore(
                RedisKeyConstant.diner_points.getKey(), dinerId, points);

    }

    /**
     * 查询前 20 积分排行榜，并显示个人排名 -- Redis
     *
     * @param accessToken
     * @return
     */
    public List<DinerPointsRankVO> findDinerPointRankFromRedis(String accessToken) {
        // 获取登录用户信息
        SignInDinerInfo signInDinerInfo = loadSignInDinerInfo(accessToken);
        // 统计积分排行榜
        Set<ZSetOperations.TypedTuple<Integer>> rangeWithScores=redisTemplate.opsForZSet().reverseRangeWithScores(
                RedisKeyConstant.diner_points.getKey(),0,19);
        if (rangeWithScores == null || rangeWithScores.isEmpty()) {
            return Lists.newArrayList();
        }
        //初始化食客id集合
        List<Integer> rankDinerIds = Lists.newArrayList();
        //根据 key：食客id  value：积分信息 构建一个map
        Map<Integer, DinerPointsRankVO> ranksMap = new LinkedHashMap<>();
        //初始化排名
        int rank=1;
        //循环处理排行榜，添加排名信息
        for (ZSetOperations.TypedTuple<Integer> rangeWithScore : rangeWithScores) {
            //食客id
            Integer dinerId = rangeWithScore.getValue();
            //积分信息
            int points=rangeWithScore.getScore().intValue();
            //将食客id添加到食客id集合
            rankDinerIds.add(dinerId);
            DinerPointsRankVO dinerPointsRankVO = new DinerPointsRankVO();
            dinerPointsRankVO.setId(dinerId);
            dinerPointsRankVO.setTotal(points);
            dinerPointsRankVO.setRanks(rank);
            //将vo对象添加到map中
            ranksMap.put(dinerId, dinerPointsRankVO);
            //排名+1
            rank++;
        }
        //获取diners 用户信息
        ResultInfo resultInfo =restTemplate.getForObject(dinersServerName +"findByIds?access_token=${accessToken}&ids=${ids}",
                ResultInfo.class, accessToken, StrUtil.join(",", rankDinerIds));//目的是把列表转成逗号分隔的字符串：
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE) {
            throw new ParameterException(resultInfo.getCode(), resultInfo.getMessage());
        }
        List<LinkedHashMap> dinerInfoMaps = (List<LinkedHashMap>) resultInfo.getData();
        //完善食客昵称的头像
        for (LinkedHashMap dinerInfoMap : dinerInfoMaps) {
            ShortDinerInfo shortDinerInfo = BeanUtil.fillBeanWithMap(dinerInfoMap,
                    new ShortDinerInfo(), false);
                    DinerPointsRankVO rankVO = ranksMap.get(shortDinerInfo.getId());
                    rankVO.setNickname(shortDinerInfo.getNickname());
                    rankVO.setAvatarUrl(shortDinerInfo.getAvatarUrl());
        }
        //判断个人是否在ranks 中，如果在，添加标记直接返回
        if(ranksMap.containsKey(signInDinerInfo.getId())){
            //如果在，添加标记
            DinerPointsRankVO rankVO = ranksMap.get(signInDinerInfo.getId());
            rankVO.setIsMe(1);
            return Lists.newArrayList(ranksMap.values());

        }
        //如果不在ranks中，获取个人排名追加到最后
        //获取排名
        Long myRank=redisTemplate.opsForZSet().reverseRank(
                RedisKeyConstant.diner_points.getKey(), signInDinerInfo.getId());
        if(myRank!=null){
            DinerPointsRankVO myRankVO = new DinerPointsRankVO();
            BeanUtil.copyProperties(signInDinerInfo,myRankVO);
            myRankVO.setRanks(myRank.intValue()+1);
            myRankVO.setIsMe(1);
            //获取积分
            Double points=redisTemplate.opsForZSet().score(
                    RedisKeyConstant.diner_points.getKey(), signInDinerInfo.getId());
            myRankVO.setTotal(points.intValue());
            ranksMap.put(signInDinerInfo.getId(),myRankVO);
        }
        return Lists.newArrayList(ranksMap.values());
        }


    //查询前20积分榜盘，并显示个人排名
    public List<DinerPointsRankVO> findDinerPointRank(String accessToken) {
        // 获取登录用户信息
        SignInDinerInfo signInDinerInfo = loadSignInDinerInfo(accessToken);
        // 统计积分排行榜
        List<DinerPointsRankVO> ranks =dinerPointsMapper.findTopN(TOPN);
        if (ranks == null || ranks.isEmpty()) {
            return Lists.newArrayList();
        }
        //根据 key：食客id value：积分信息 构建一个map
        Map<Integer, DinerPointsRankVO> ranksMap = new LinkedHashMap<>();
        for (int i = 0; i < ranks.size(); i++) {
            ranksMap.put(ranks.get(i).getId(), ranks.get(i));
        }
        //判断个人是否在ranks 中，如果在，添加标记直接返回
        if(ranksMap.containsKey(signInDinerInfo.getId())){
            DinerPointsRankVO myRank = ranksMap.get(signInDinerInfo.getId());
            myRank.setIsMe(1);
            return Lists.newArrayList(ranksMap.values());
        }
        //如果不在ranks中，获取个人排名追加在最后
        DinerPointsRankVO myRank = dinerPointsMapper.findDinerRank(signInDinerInfo.getId());
        myRank.setIsMe(1);
        ranks.add(myRank);
        return ranks;
    }

    /**
     * 获取登录用户信息
     *
     * @param accessToken
     * @return
     */
    private SignInDinerInfo loadSignInDinerInfo(String accessToken) {
        // 必须登录
        AssertUtil.mustLogin(accessToken);
        String url = oauthServerName + "user/me?access_token={accessToken}";
        ResultInfo resultInfo = restTemplate.getForObject(url, ResultInfo.class, accessToken);
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE) {
            throw new ParameterException(resultInfo.getCode(), resultInfo.getMessage());
        }
        SignInDinerInfo dinerInfo = BeanUtil.fillBeanWithMap((LinkedHashMap) resultInfo.getData(),
                new SignInDinerInfo(), false);
        if (dinerInfo == null) {
            throw new ParameterException(ApiConstant.NO_LOGIN_CODE, ApiConstant.NO_LOGIN_MESSAGE);
        }
        return dinerInfo;
    }

}