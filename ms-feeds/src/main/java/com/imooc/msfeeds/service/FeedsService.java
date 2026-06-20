package com.imooc.msfeeds.service;

import cn.hutool.core.bean.BeanUtil;
import com.google.common.collect.Lists;
import com.imooc.commons.constant.ApiConstant;
import com.imooc.commons.constant.RedisKeyConstant;
import com.imooc.commons.exception.ParameterException;
import com.imooc.commons.model.domain.ResultInfo;
import com.imooc.commons.model.pojo.Feeds;
import com.imooc.commons.model.vo.FeedsVO;
import com.imooc.commons.model.vo.ShortDinerInfo;
import com.imooc.commons.model.vo.SignInDinerInfo;
import com.imooc.commons.utils.AssertUtil;
import com.imooc.msfeeds.mapper.FeedsMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FeedsService {

    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;
    @Value("${service.name.ms-follow-server}")
    private String followServerName;
    @Value("${service.name.ms-diners-server}")
    private String dinersServerName;
    @Resource
    private RestTemplate restTemplate;
    @Resource
    private FeedsMapper feedsMapper;
    @Resource
    private RedisTemplate redisTemplate;

    //查询feed
    @Transactional(rollbackFor = Exception.class)
    public List<FeedsVO> selectForPage(Integer page, String accessToken) {
        if(page == null){
            page=1;
        }
        //获取登录用户信息
        SignInDinerInfo dinerInfo = loadSignInDinerInfo(accessToken);
        //我关注的好友的 feedkey
        String key=RedisKeyConstant.following_feeds.getKey()+dinerInfo.getId();
        //shortset 的 zverange
        long strat=(page-1)*ApiConstant.PAGE_SIZE;
        long end=page*ApiConstant.PAGE_SIZE-1;
        Set<Integer> feedIds=redisTemplate.opsForZSet().reverseRange(key,strat,end);
        if(feedIds ==null || feedIds.isEmpty()){
            return Lists.newArrayList();
        }
        //根据多主键查询feed
        List<Feeds> feeds = feedsMapper.findFeedsByIds(feedIds);
        //初始化关注好友的id集合
        List<Integer> followingDinerIds=new ArrayList<>();
        //添加用户id至集合
        List<FeedsVO> feedsVOS =feeds.stream().map(feed ->{
            FeedsVO feedsVO = new FeedsVO();
            BeanUtil.copyProperties(feed,feedsVO);
            //添加用户id
            followingDinerIds.add(feed.getFkDinerId());
            return feedsVO;
        }).collect(Collectors.toList());
        //远程调用获取feed中用户信息
        ResultInfo resultInfo =restTemplate.getForObject(dinersServerName+"findByIds?access_token=${accessToken}&ids=${ids}",
                ResultInfo.class,accessToken,followingDinerIds);
        if(resultInfo.getCode()!= ApiConstant.SUCCESS_CODE){
            throw new ParameterException(resultInfo.getCode(),resultInfo.getMessage());
        }
        List<LinkedHashMap> dinerInfoMaps = (List<LinkedHashMap>)resultInfo.getData();
        //构建一个 key 为用户id，value为shortdinerinfo 的map
        Map<Integer,ShortDinerInfo> dinerInfoMap = dinerInfoMaps.stream().
                collect(Collectors.toMap(
                        //key
                        diner -> (Integer)diner.get("id"),
                        //value
                        diner -> BeanUtil.fillBeanWithMap(diner,new ShortDinerInfo(),true)
                ));
        //循环vo集合，根据用户id从map中获取用户信息并设置到vo中
        feedsVOS.forEach(feedsVO -> {
            feedsVO.setDinerInfo(dinerInfoMap.get(feedsVO.getFkDinerId()));
        });
        return feedsVOS;
    }

    //变更feed
    @Transactional(rollbackFor = Exception.class)
    public void addFollowingFeed(Integer followingDinerId,String accessToken,int type) {
        //请选择关注的好友
        AssertUtil.isTrue(followingDinerId==null||followingDinerId <1,"请选择关注对象");
        //获取登录用户信息
        SignInDinerInfo dinerInfo = loadSignInDinerInfo(accessToken);
        //获取关注/取关 的食客的 所有feed
        List<Feeds> feedsList = feedsMapper.findByDinerId(followingDinerId);
        String key=RedisKeyConstant.following_feeds.getKey()+dinerInfo.getId();
        //根据type决定是添加还是移除操作
        if(type==0){
            //取关
            List<Integer> feedIds =feedsList.stream()
                                    .map(feeds -> feeds.getId())
                                    .collect(Collectors.toList());
            redisTemplate.opsForZSet().remove(key,feedIds.toArray(new Integer[]{}));
        }
        else if(type==1){
            //关注
            Set<ZSetOperations.TypedTuple> typedTupleSet=feedsList.stream()
                    .map(feeds -> new DefaultTypedTuple<>(feeds.getId(),(double)feeds.getUpdateDate().getTime()))
                    .collect(Collectors.toSet());
            redisTemplate.opsForZSet().add(key, typedTupleSet);

        }
        else {
            throw new ParameterException("关注状态异常");
        }

    }

    //删除feed
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id,String accessToken) {
        //请选择要删除的feed
        AssertUtil.isTrue(id==null||id <1,"请选择要删除的feed");
        //获取登录用户
        SignInDinerInfo dinerInfo = loadSignInDinerInfo(accessToken);
        //获取 feed 的内容
        Feeds feeds = feedsMapper.findById(id);
        //判断 feed 是否已被删除且只能删除自己的 feed
        AssertUtil.isTrue(feeds==null||feeds.getIsValid()==0,"该feed已被删除");
        AssertUtil.isTrue(feeds.getFkDinerId().intValue()!=dinerInfo.getId(),"只能删除自己的feed");
        //删除feed
        int count = feedsMapper.delete(id);
        AssertUtil.isTrue(count==0,"删除失败");
        //将内容从粉丝的集合中删除 --异步消息队列优化
        List<Integer> followers = findFollowers(dinerInfo.getId());
        //先获取我的粉丝
        followers.forEach(follower -> {
            String key=RedisKeyConstant.following_feeds.getKey()+follower;
            redisTemplate.opsForZSet().remove(key,feeds.getId());
        });
    }

    //添加feed
    @Transactional(rollbackFor = Exception.class)
   public void create(Feeds feeds,String accessToken) {
        //非空校验 feed 内容不能为空，不能太长
        AssertUtil.isNotEmpty(feeds.getContent(),"请输入内容");
        AssertUtil.isTrue(feeds.getContent().length()>=30,"内容不能太长");
        //获取登录用户
        SignInDinerInfo dinerInfo = loadSignInDinerInfo(accessToken);
        //feed 关联用户信息
        feeds.setFkDinerId(dinerInfo.getId());
        //添加feed
        int count = feedsMapper.save(feeds);
        AssertUtil.isTrue(count==0,"添加失败");
        //这里用推方案，把feed推送到粉丝列表中 --后续可以采用异步消息队列解决性能问题
        //先获取粉丝id集合
        List<Integer> followers =findFollowers(dinerInfo.getId());

        //推送 feed
        long now=System.currentTimeMillis();
        followers.forEach(follower -> {
            String key=RedisKeyConstant.following_feeds.getKey()+follower;
            redisTemplate.opsForZSet().add(key,feeds.getId(),now);
        });


   }
    //获取粉丝id集合
    private List<Integer> findFollowers(Integer dinerId) {
        String url=followServerName+"followers/"+dinerId;
        ResultInfo resultInfo = restTemplate.getForObject(url, ResultInfo.class);
        if(resultInfo.getCode() != ApiConstant.SUCCESS_CODE){
            throw new ParameterException(resultInfo.getCode(),resultInfo.getMessage());
        }
        List<Integer> followers = (List<Integer>) resultInfo.getData();
        return followers;
    }

    //获取登录用户信息
    private SignInDinerInfo loadSignInDinerInfo(String accessToken) {
        //必须登录
        AssertUtil.mustLogin(accessToken);
        String url=oauthServerName+"/user/me?access_token={accessToken}";
        ResultInfo resultInfo =restTemplate.getForObject(url, ResultInfo.class, accessToken);
        if(resultInfo.getCode() != ApiConstant.SUCCESS_CODE){
            throw new ParameterException(resultInfo.getCode(),resultInfo.getMessage());
        }
        SignInDinerInfo dinerInfo = BeanUtil.fillBeanWithMap((LinkedHashMap)resultInfo.getData(),new SignInDinerInfo(),false);
        return dinerInfo;
    }
}
