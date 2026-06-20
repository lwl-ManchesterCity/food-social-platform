package com.imooc.gateway.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imooc.commons.constant.ApiConstant;
import com.imooc.commons.model.domain.ResultInfo;
import com.imooc.commons.utils.ResultInfoUtil;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.nio.charset.Charset;

@Component
public class HandleException {

    @Resource
    private ObjectMapper objectMapper;

    public Mono<Void> writeError(ServerWebExchange exchange, String error) {
        ServerHttpResponse response = exchange.getResponse();
        ServerHttpRequest request = exchange.getRequest();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        ResultInfo resultInfo = ResultInfoUtil.buildError(
                ApiConstant.NO_LOGIN_CODE,
                error,
                request.getURI().getPath());
        //后面这个就是为了让前端有一个呈现的结果是吧，所以要转为二进制才通过http发送，因为resultInfo这个是给后端看的
        String resultInfoJson = null;
        DataBuffer buffer = null;
        try {
            resultInfoJson = objectMapper.writeValueAsString(resultInfo);
            //在 Gateway 的 WebFlux 模型里，响应内容最终要包装成 DataBuffer 才能写回前端。
            buffer =  response.bufferFactory().wrap(resultInfoJson.getBytes(Charset.forName("UTF-8")));
        } catch (JsonProcessingException ex) {
            ex.printStackTrace();
        }
        return response.writeWith(Mono.just(buffer));
    }

}