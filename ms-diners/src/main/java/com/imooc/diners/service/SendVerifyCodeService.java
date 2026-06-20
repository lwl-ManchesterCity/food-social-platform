package com.imooc.diners.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.imooc.commons.constant.RedisKeyConstant;
import com.imooc.commons.utils.AssertUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 发送验证码业务逻辑层
 */
@Service
public class SendVerifyCodeService {

    @Resource
    private RedisTemplate<String, String> redisTemplate;


    public void send(String phone){
        //检查非空
        AssertUtil.isTrue(StrUtil.isBlank(phone), "请输入手机号码");
        //根据手机号查询是否已经生成验证码，已生成直接返回
        if(!checkCodeIsExpired(phone)){
            return;
        }
        //生成6位验证码
        String code = RandomUtil.randomNumbers(6);
        //调用短信服务发送验证码

        //发送成功，将code保存至redis，失效期时间60s
        String key=RedisKeyConstant.verify_code.getKey() + phone;
        redisTemplate.opsForValue().set(key, code, 60, TimeUnit.SECONDS);
    }

    private boolean checkCodeIsExpired(String  phone) {
        String key =RedisKeyConstant.verify_code.getKey() + phone;
        String code = redisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(code)){
            return true;
        }
        return false;
    }

    public String getCodeByPhone(String phone){
        String key =RedisKeyConstant.verify_code.getKey() + phone;
        String code = redisTemplate.opsForValue().get(key);
        return code;
    }


}
