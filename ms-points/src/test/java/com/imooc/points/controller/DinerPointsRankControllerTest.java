package com.imooc.points.controller;

import com.imooc.commons.model.vo.DinerPointsRankVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.ws.rs.core.MediaType;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
@AutoConfigureMockMvc
class DinerPointsRankControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedisTemplate<String, Integer> redisTemplate;  // 用于直接操作Redis

    private String accessToken = "test_access_token";

    // 测试1：直接测试排行榜接口（不依赖添加积分）
    @Test
    void testRedisRankApiDirectly() throws Exception {
        // 首先确保Redis中有一些数据
        // 使用RedisTemplate直接添加测试数据
        redisTemplate.opsForZSet().add("diner:points", 1, 1000);
        redisTemplate.opsForZSet().add("diner:points", 2, 900);
        redisTemplate.opsForZSet().add("diner:points", 3, 800);
        redisTemplate.opsForZSet().add("diner:points", 5, 5000);  // 确保ID=5有高积分

        // 直接调用排行榜接口
        String result = mockMvc.perform(MockMvcRequestBuilders.get("/redis")
                        .param("access_token", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();

        System.out.println("排行榜结果：" + result);

        // 清理测试数据
        redisTemplate.delete("diner:points");
    }

    // 测试2：测试积分添加接口
    @Test
    void testAddPointsApi() throws Exception {
        // 正确调用积分添加接口（注意：是根路径 /）
        mockMvc.perform(MockMvcRequestBuilders.post("/")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("dinerId", "5")
                        .param("points", "100")
                        .param("types", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证是否添加到Redis
        Double score = redisTemplate.opsForZSet().score("diner:points", 5);
        assertNotNull(score);
        assertEquals(100, score.intValue());
    }

    // 测试3：完整的集成测试
    @Test
    void testCompleteFlow() throws Exception {
        // 步骤1：添加积分
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(MockMvcRequestBuilders.post("/")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("dinerId", "5")
                            .param("points", "100")
                            .param("types", "0"))
                    .andExpect(status().isOk());
        }

        // 步骤2：查询排行榜
        String result = mockMvc.perform(MockMvcRequestBuilders.get("/redis")
                        .param("access_token", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn()
                .getResponse()
                .getContentAsString();

        System.out.println("完整流程测试结果：" + result);

        // 步骤3：验证数据
        // 可以添加更多断言来验证返回的数据结构
    }
}