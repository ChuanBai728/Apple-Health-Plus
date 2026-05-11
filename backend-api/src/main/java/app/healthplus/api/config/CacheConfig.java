package app.healthplus.api.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 缓存配置。
 * <p>
 * 仅在激活 "redis-cache" Profile 时生效（dev 环境默认不启用，避免缓存反序列化问题）。
 * 使用 Jackson JSON 序列化 + 类型信息嵌入（{@code @JsonTypeInfo}）确保缓存值可正确反序列化。
 * <p>
 * 设计思路：
 * <ul>
 *   <li>默认 TTL 10 分钟，防止冷数据长期占用内存</li>
 *   <li>针对不同业务场景设置差异化 TTL（报告类 24h、查询类 10min、概览 5min）</li>
 *   <li>禁止缓存 null 值，防止缓存穿透</li>
 * </ul>
 */
@Configuration
@EnableCaching
@org.springframework.context.annotation.Profile("redis-cache")
public class CacheConfig {

    /**
     * 创建 Redis 缓存管理器。
     * <p>
     * 配置要点：
     * - Key 使用 String 序列化，Value 使用 Jackson JSON 序列化
     * - 开启默认类型信息（DefaultTyping），确保多态类型可正确反序列化
     * - 注册 JavaTimeModule 以支持 Instant/LocalDate 等时间类型
     *
     * @param factory Redis 连接工厂
     * @return 配置好的 RedisCacheManager 实例
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer))
                .disableCachingNullValues(); // prevent cache penetration for nulls

        // Differentiated TTLs
        Map<String, RedisCacheConfiguration> ttlMap = new HashMap<>();
        ttlMap.put("insight", defaultConfig.entryTtl(Duration.ofHours(24)));     // health reports
        ttlMap.put("metrics", defaultConfig.entryTtl(Duration.ofMinutes(10)));    // metric queries
        ttlMap.put("overview", defaultConfig.entryTtl(Duration.ofMinutes(5)));    // overview dashboard

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(ttlMap)
                .build();
    }
}
