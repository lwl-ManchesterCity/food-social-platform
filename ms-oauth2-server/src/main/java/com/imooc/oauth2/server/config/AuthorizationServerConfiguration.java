package com.imooc.oauth2.server.config;

import com.imooc.commons.model.domain.SignInIdentity;
import com.imooc.oauth2.server.service.UserService;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;

import javax.annotation.Resource;
import java.util.LinkedHashMap;

/**
 * 授权服务
 */
@Configuration
@EnableAuthorizationServer
public class AuthorizationServerConfiguration extends AuthorizationServerConfigurerAdapter {

    @Resource
    private ClientOAuth2DataConfiguration clientOAuth2DataConfiguration;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private AuthenticationManager authenticationManager;
    @Resource
    private RedisTokenStore redisTokenStore;
    @Resource
    private UserService userService;

    //配置令牌端点安全约束
    @Override
    public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
       security.tokenKeyAccess("permitAll()")
               .checkTokenAccess("permitAll()");
    }

    //授权模型
    //调用认证服务的“客户端应用”，不是谁都能拿用户名密码换 token必须先证明“我是合法客户端”
    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
      clients.inMemory().withClient(clientOAuth2DataConfiguration.getClientId())
              .secret(passwordEncoder.encode(clientOAuth2DataConfiguration.getSecret()))
              .authorizedGrantTypes(clientOAuth2DataConfiguration.getGrantTypes())
              .accessTokenValiditySeconds(clientOAuth2DataConfiguration.getRefreshTokenValidityTime())
              .refreshTokenValiditySeconds(clientOAuth2DataConfiguration.getTokenValidityTime())
              .scopes(clientOAuth2DataConfiguration.getScopes());
    }

    //验证用户合法性，配置令牌
    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
       endpoints.authenticationManager(authenticationManager)
               .userDetailsService(userService)//通过username获取用户信息
               .tokenStore(redisTokenStore)//生成token，并把他存到redis
               .tokenEnhancer((accessToken, authentication)->{
                   SignInIdentity identity = (SignInIdentity) authentication.getUserAuthentication().getPrincipal();
                   LinkedHashMap<String, Object> map = new LinkedHashMap<>();
                   map.put("nickname", identity.getNickname());
                   map.put("avatarUrl", identity.getAvatarUrl());
                   DefaultOAuth2AccessToken token = (DefaultOAuth2AccessToken) accessToken;
                   token.setAdditionalInformation(map);
                   return token;
               });

    }
}