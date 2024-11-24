package com.ticketing.queueflow.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisConfig implements ApplicationListener<ApplicationReadyEvent> {
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        reactiveRedisTemplate.opsForValue().get("1")
                .doOnSuccess(i -> log.info("Initialize to redis connection"))
                .doOnError(err -> log.error("Failed to initialize redis connection: {}", err.getMessage()))
                .subscribe();
    }
//
//    @Bean
//    public ReactiveRedisTemplate<String, User> reactiveRedisUserTemplate(ReactiveRedisConnectionFactory connectionFactory) {
//        var objectMapper = new ObjectMapper()
//                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
//                .registerModule(new JavaTimeModule())
//                .disable(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS);
//
//        Jackson2JsonRedisSerializer<User> jsonSerializer = new Jackson2JsonRedisSerializer<>(objectMapper, User.class);
//
//        RedisSerializationContext<String, User> serializationContext = RedisSerializationContext
//                .<String, User>newSerializationContext()
//                .key(RedisSerializer.string())
//                .value(jsonSerializer)
//                .hashKey(RedisSerializer.string())
//                .hashValue(jsonSerializer)
//                .build();
//
//        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
//    }
}
