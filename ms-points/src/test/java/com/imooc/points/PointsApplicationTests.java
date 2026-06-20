import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Set;

@SpringBootTest
class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testRedisConnection() {
        // 测试当前连接的 database
        System.out.println("测试 Redis 连接...");

        // 1. 测试写入
        stringRedisTemplate.opsForValue().set("test:connection", "success");

        // 2. 测试读取
        String value = stringRedisTemplate.opsForValue().get("test:connection");
        System.out.println("读取的值: " + value);

        // 3. 查看当前 database 的所有 keys
        Set<String> keys = stringRedisTemplate.keys("*");
        System.out.println("当前 database 的所有 keys: " + keys);

        // 4. 测试 ZSet 操作
        String key = "test:zset:points";
        stringRedisTemplate.delete(key);

        stringRedisTemplate.opsForZSet().add(key, "user:1001", 150.0);
        stringRedisTemplate.opsForZSet().add(key, "user:1002", 200.0);

        Set<ZSetOperations.TypedTuple<String>> result = stringRedisTemplate
                .opsForZSet().rangeWithScores(key, 0, -1);

        System.out.println("ZSet 测试结果:");
        for (ZSetOperations.TypedTuple<String> tuple : result) {
            System.out.println("  " + tuple.getValue() + " : " + tuple.getScore());
        }
    }
}