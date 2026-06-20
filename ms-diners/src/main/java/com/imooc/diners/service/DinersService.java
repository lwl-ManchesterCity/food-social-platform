package com.imooc.diners.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.imooc.commons.constant.ApiConstant;
import com.imooc.commons.model.domain.ResultInfo;
import com.imooc.commons.model.dto.DinersDTO;
import com.imooc.commons.model.pojo.Diners;
import com.imooc.commons.model.vo.ShortDinerInfo;
import com.imooc.commons.utils.AssertUtil;
import com.imooc.commons.utils.ResultInfoUtil;
import com.imooc.diners.config.OAuth2ClientConfiguration;
import com.imooc.diners.domain.OAuthDinerInfo;
import com.imooc.diners.mapper.DinersMapper;
import com.imooc.diners.vo.LoginDinerInfo;
import com.sun.jersey.api.client.RequestBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 食客服务业务逻辑层
 */
@Slf4j
@Service
public class DinersService {

    @Resource
    private RestTemplate restTemplate;
    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;
    @Resource
    private OAuth2ClientConfiguration oAuth2ClientConfiguration;
    @Resource
    private DinersMapper dinersMapper;
    @Resource
    private SendVerifyCodeService sendVerifyCodeService;

    //用户注册
    public ResultInfo register(DinersDTO dinersDTO, String path){
        //参数非空校验
        String username = dinersDTO.getUsername();
        AssertUtil.isNotEmpty(username,"请输入用户名");
        String password = dinersDTO.getPassword();
        AssertUtil.isNotEmpty(password,"请输入密码");
        String phone= dinersDTO.getPhone();
        AssertUtil.isNotEmpty(phone,"请输入手机号");
        String verifyCode = dinersDTO.getVerifyCode();
        AssertUtil.isNotEmpty(verifyCode,"请输入验证码");
        //验证码一致性的校验
        String code=sendVerifyCodeService.getCodeByPhone( phone);
        AssertUtil.isNotEmpty(code,"验证码已过期，请重新获取");
        AssertUtil.isTrue(!code.equals(verifyCode),"验证码输入错误");
        //验证用户名是否已注册
        Diners diners = dinersMapper.selectByUsername(username.trim());
        AssertUtil.isTrue(diners!=null,"该用户已注册");
        //注册
        //密码加密
        dinersDTO.setPassword(DigestUtil.md5Hex(password));
        dinersMapper.save(dinersDTO);
        //自动登录
        ResultInfo resultInfo =signIn(username.trim(),password.trim(),path);
        return resultInfo;
    }


    //校验手机号是否已注册
    public void checkPhoneIsRegistered(String phone){
        AssertUtil.isNotEmpty(phone,"请输入手机号");
        Diners diners = dinersMapper.selectByPhone(phone);
        AssertUtil.isTrue(diners==null,"该手机号未注册");
        AssertUtil.isTrue(diners.getIsValid()==0,"该手机号已注销");
    }

    public ResultInfo signIn(String acccount ,String password, String path){
        AssertUtil.isNotEmpty(acccount,"请输入登录帐号");
        AssertUtil.isNotEmpty(password,"请输入密码");
        //构建请求头
        HttpHeaders  headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        //构建请求体（请求参数）
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("username", acccount);
        body.add("password", password);
        body.setAll(BeanUtil.beanToMap(oAuth2ClientConfiguration));
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        //设置Authorization
        restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(
                oAuth2ClientConfiguration.getClientId(),oAuth2ClientConfiguration.getSecret()));
        //发送请求
        ResponseEntity<ResultInfo> result =restTemplate.postForEntity(oauthServerName+"/oauth/token",entity,ResultInfo.class);
        //处理返回结果
        AssertUtil.isTrue(result.getStatusCode()!=HttpStatus.OK,"登录失败");
        ResultInfo resultInfo = result.getBody();
        if(resultInfo.getCode()!=ApiConstant.SUCCESS_CODE){
            //登录失败
            resultInfo.setData(resultInfo.getMessage());
            return resultInfo;
        }
        OAuthDinerInfo dinerInfo =BeanUtil.fillBeanWithMap((LinkedHashMap)resultInfo.getData()
                ,new OAuthDinerInfo(),false);
        LoginDinerInfo loginDinerInfo = new LoginDinerInfo();
        loginDinerInfo.setNickname(dinerInfo.getNickname());
        loginDinerInfo.setToken(dinerInfo.getAccessToken());
        loginDinerInfo.setAvatarUrl(dinerInfo.getAvatarUrl());
        return ResultInfoUtil.buildSuccess(path,loginDinerInfo);
    }

    //根据ids查询食客的信息
    public  List<ShortDinerInfo> findByIds(String ids){
        AssertUtil.isNotEmpty(ids,"请选择食客信息");
        String[] idArr = ids.split(",");
       List<ShortDinerInfo> dinerInfos = dinersMapper.findByIds(idArr);
       return dinerInfos;
    }


}
