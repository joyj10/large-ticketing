package com.ticketing.queueflow.service;

import com.ticketing.queueflow.EmbeddedRedis;
import com.ticketing.queueflow.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(EmbeddedRedis.class)
@ActiveProfiles("test")
class UserQueueServiceTest {
    @Autowired
    private UserQueueService userQueueService;

    @Autowired
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    private String queueName = "default";

    // 테스트 전 redis 초기화
    @BeforeEach
    public void clearRedis() {
        ReactiveRedisConnection redisConnection = reactiveRedisTemplate.getConnectionFactory().getReactiveConnection();
        redisConnection.serverCommands().flushAll().subscribe();
    }

    @DisplayName("대기열 사용자를 등록 시 순위가 증가 한다.")
    @Test
    void registerWaitQueue() {
        StepVerifier.create(userQueueService.registerWaitQueue(queueName, 100L))
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(userQueueService.registerWaitQueue(queueName, 101L))
                .expectNext(2L)
                .verifyComplete();

        StepVerifier.create(userQueueService.registerWaitQueue(queueName, 102L))
                .expectNext(3L)
                .verifyComplete();
    }

    @DisplayName("동일 사용자 대기열 등록 시 에러가 발생한다.")
    @Test
    void alreadyRegisterWaitQueue() {
        StepVerifier.create(userQueueService.registerWaitQueue(queueName, 100L))
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(userQueueService.registerWaitQueue(queueName, 100L))
                .expectError(ApplicationException.class);
    }

    @DisplayName("모든 사용자 접근을 허용한 후 대기열 등록 시 다시 1번 부터 시작된다.")
    @Test
    void allowUserAfterRegisterWaitQueue() {
        StepVerifier.create(userQueueService.registerWaitQueue(queueName, 100L)
                        .then(userQueueService.registerWaitQueue(queueName, 101L))
                        .then(userQueueService.registerWaitQueue(queueName, 102L))
                        .then(userQueueService.allowUser(queueName, 3L))
                        .then(userQueueService.registerWaitQueue(queueName, 200L)))
                .expectNext(1L)
                .verifyComplete();
    }

    @DisplayName("사용자 접근 허용 시 요청 수가 대기열 수보다 작은 경우 요청 수와 동일한 수를 리턴 한다.")
    @Test
    void allowUser() {
        StepVerifier.create(userQueueService.registerWaitQueue(queueName, 100L)
                        .then(userQueueService.registerWaitQueue(queueName, 101L))
                        .then(userQueueService.registerWaitQueue(queueName, 102L))
                        .then(userQueueService.allowUser(queueName, 2L)))
                .expectNext(2L)
                .verifyComplete();
    }

    @DisplayName("사용자 접근 허용 시 요청 수가 대기열 수보다 큰 경우 대기열 수를 리턴 한다.")
    @Test
    void allowUser2() {
        StepVerifier.create(userQueueService.registerWaitQueue(queueName, 100L)
                        .then(userQueueService.registerWaitQueue(queueName, 101L))
                        .then(userQueueService.registerWaitQueue(queueName, 102L))
                        .then(userQueueService.allowUser(queueName, 5L)))
                .expectNext(3L)
                .verifyComplete();
    }

    @DisplayName("대기열이 비어 있는 경우 사용자 접근 허용 시 0L 리턴 한다.")
    @Test
    void emptyAllowUser() {
        StepVerifier.create(userQueueService.allowUser(queueName, 3L))
                .expectNext(0L)
                .verifyComplete();
    }

    @DisplayName("대기열이 비어 있어서 진입 불가능 상태 시 false 리턴 한다.")
    @Test
    void isNotAllowed() {
        StepVerifier.create(userQueueService.isAllowed(queueName, 100L))
                .expectNext(false)
                .verifyComplete();
    }

    @DisplayName("대기열에 해당 사용자가 없어 진입 불가능 상태 시 false 리턴 한다.")
    @Test
    void isNotAllowed2() {
        StepVerifier.create(userQueueService.registerWaitQueue(queueName, 100L)
                        .then(userQueueService.allowUser(queueName, 3L))
                        .then(userQueueService.isAllowed(queueName, 101L)))
                .expectNext(false)
                .verifyComplete();
    }

    @DisplayName("사용자 진입 허용 상태인 경우 true 리턴 한다.")
    @Test
    void isAllowed() {
        StepVerifier.create(userQueueService.registerWaitQueue(queueName, 100L)
                        .then(userQueueService.allowUser(queueName, 3L))
                        .then(userQueueService.isAllowed(queueName, 100L)))
                .expectNext(true)
                .verifyComplete();
    }

    @DisplayName("요청 사용자의 대기 순번을 알려준다.")
    @Test
    void getRank() {
        StepVerifier.create(
                userQueueService.registerWaitQueue("default", 100L)
                        .then(userQueueService.getRank("default", 100L)))
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(
                        userQueueService.registerWaitQueue("default", 101L)
                                .then(userQueueService.getRank("default", 101L)))
                .expectNext(2L)
                .verifyComplete();
    }


    @DisplayName("요청 사용자의 대기 순번이 없는 경우 -1이 리턴 된다.")
    @Test
    void emptyRank() {
        StepVerifier.create(userQueueService.getRank("default", 100L))
                .expectNext(-1L)
                .verifyComplete();
    }
}
