package com.imooc.msfollow.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.imooc.commons.constant.ApiConstant;
import com.imooc.commons.constant.RedisKeyConstant;
import com.imooc.commons.exception.ParameterException;
import com.imooc.commons.model.domain.ResultInfo;
import com.imooc.commons.model.domain.SignInIdentity;
import com.imooc.commons.model.pojo.Follow;
import com.imooc.commons.model.vo.ShortDinerInfo;
import com.imooc.commons.model.vo.SignInDinerInfo;
import com.imooc.commons.utils.AssertUtil;
import com.imooc.commons.utils.ResultInfoUtil;
import com.imooc.msfollow.mapper.FollowMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.reflections.Reflections.log;

/**
 * 关注/取关业务逻辑层
 */
@Service
public class FollowService {

    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;
    @Value("${service.name.ms-diners-server}")
    private String dinersServerName;
    @Value("${service.name.ms-feeds-server}")
    private String feedsServerName;
    @Resource
    private RestTemplate restTemplate;
    @Resource
    private FollowMapper followMapper;
    @Resource
    private RedisTemplate redisTemplate;

    //获取粉丝列表
    public Set< Integer> findFollowers(Integer dinerId){
        AssertUtil.isNotNull(dinerId, "请选择要查看的用户");
        Set<Integer> followings = redisTemplate.opsForSet().members(RedisKeyConstant.followers.getKey()+dinerId);
        return followings;
    }

    //共同关注列表
    public ResultInfo findCommonsFriends(Integer dinerId, String accessToken, String path){
        //是否选择了关注对象
        AssertUtil.isTrue(dinerId == null||dinerId < 1, "请选择关注对象");
        //获取登录用户信息（封装方法）
        SignInDinerInfo dinerInfo = loadSignInDinerInfo(accessToken);
        //获取登录用户的关注列表
        String loginDinerKey=RedisKeyConstant.following.getKey()+dinerInfo.getId();
        //获取登录用户查看对象的关注信息
        String dinerKey=RedisKeyConstant.following.getKey()+dinerId;
        //通过redis计算交集
        Set<Integer> dinerIds = redisTemplate.opsForSet().intersect(loginDinerKey, dinerKey);
        //判断共同关注情况
        if(dinerIds== null&&dinerIds.isEmpty()){
            return  ResultInfoUtil.buildSuccess( path, new ArrayList<ShortDinerInfo>());
        }
        //根据ids查询食客的信息
        ResultInfo resultInfo =restTemplate.getForObject(dinersServerName+"findByIds?access_token={accessToken}&ids={ids}"
                                    ,ResultInfo.class,accessToken,StrUtil.join(",", dinerIds));
        if(resultInfo.getCode() != ApiConstant.SUCCESS_CODE){
            resultInfo.setPath( path);
            return resultInfo;
        }
        //处理结果集
        List<LinkedHashMap> dinnerInfoMaps=(List<LinkedHashMap>)resultInfo.getData();
        List<ShortDinerInfo> shortDinerInfos = dinnerInfoMaps.stream().map(diner ->
                BeanUtil.fillBeanWithMap(diner, new ShortDinerInfo(),true)).collect(Collectors.toList());
        return ResultInfoUtil.buildSuccess( path, shortDinerInfos);
    }

  public ResultInfo follow(Integer followDinerId, int isFollowed,String accessToken, String path){
      //是否选择了关注对象
      AssertUtil.isTrue(followDinerId == null||followDinerId < 1, "请选择关注对象");
      //获取登录用户信息（封装方法）
      SignInDinerInfo dinerInfo = loadSignInDinerInfo(accessToken);
      //获取当前登录用户与需要关注用户的关注信息
      Follow follow = followMapper.selectFollow(dinerInfo.getId(), followDinerId);
      //如果没有关注信息，且要进行关注操作 --添加关注
      if(follow == null && isFollowed == 1){
          //添加关注信息
          int count = followMapper.save(dinerInfo.getId(), followDinerId);
          //添加关注列表到redis
          if(count ==1){
              addToRedisSet(dinerInfo.getId(), followDinerId);
              //保存feed
              sendSaveOrRemoveFeed(followDinerId,accessToken, 1);
          }
          return ResultInfoUtil.build(ApiConstant.SUCCESS_CODE, "关注成功",path,"关注成功");
      }
      //如果有关注信息，目前处于关注状态且进行取关操作 --取消关注
      if(follow != null && follow.getIsValid() == 1 && isFollowed == 0){
          //取关
          int count = followMapper.update(follow.getId(), isFollowed);
          if(count == 1){
              //从redis中删除关注列表
              removeFromRedisSet(dinerInfo.getId(), followDinerId);
              //移除feed
              sendSaveOrRemoveFeed(followDinerId,accessToken, 0);
          }
          return ResultInfoUtil.build(ApiConstant.SUCCESS_CODE, "取关成功",path,"取关成功");
      }
      //如果有关注信息，目前处于取关状态且进行关注操作 --重新关注
      if(follow != null && follow.getIsValid() == 0 && isFollowed == 1){
          //重新关注
          int count = followMapper.update(follow.getId(), isFollowed);
          //添加关注列表到redis
          if(count == 1){
              addToRedisSet(dinerInfo.getId(), followDinerId);
              //保存feed
              sendSaveOrRemoveFeed(followDinerId,accessToken, 1);
          }
          return ResultInfoUtil.build(ApiConstant.SUCCESS_CODE, "关注成功",path,"关注成功");
      }
      // 在三个if之后，加上明确的else
      if (follow == null && isFollowed == 0) {
          return ResultInfoUtil.build(ApiConstant.SUCCESS_CODE, "未关注，无需取关", path, null);
      } else if (follow != null && follow.getIsValid() == isFollowed) {
          // 状态已一致
          String msg = isFollowed == 1 ? "已关注，无需重复操作" : "已取关，无需重复操作";
          return ResultInfoUtil.build(ApiConstant.SUCCESS_CODE, msg, path, null);
      } else {
          // 理论上不会走到这里，加个日志
          log.warn("未知的关注状态: dinerId={}, followDinerId={}, isValid={}, isFollowed={}",
                  dinerInfo.getId(), followDinerId,
                  follow != null ? follow.getIsValid() : null, isFollowed);
          throw new ParameterException("关注状态异常");
      }
  }

    /**
     * 发送请求添加或者移除关注人的Feed列表
     *
     * @param followDinerId 关注好友的ID
     * @param accessToken   当前登录用户token
     * @param type          0=取关 1=关注
     */
    private void sendSaveOrRemoveFeed(Integer followDinerId, String accessToken, int type) {
        String feedsUpdateUrl = feedsServerName + "updateFollowingFeeds/"
                + followDinerId + "?access_token=" + accessToken;
        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        // 构建请求体（请求参数）
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("type", type);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(feedsUpdateUrl, entity, ResultInfo.class);
    }

    private void removeFromRedisSet(Integer dinerId, Integer followDinerId) {
      redisTemplate.opsForSet().remove(RedisKeyConstant.following.getKey()+dinerId, followDinerId);
      redisTemplate.opsForSet().remove(RedisKeyConstant.followers.getKey()+followDinerId, dinerId);
    }

    private void addToRedisSet(Integer DinerId, Integer followDinerId) {
      redisTemplate.opsForSet().add(RedisKeyConstant.following.getKey()+DinerId, followDinerId);
      redisTemplate.opsForSet().add(RedisKeyConstant.followers.getKey()+followDinerId, DinerId);
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
