package com.ticketing.queueflow.service;

import com.ticketing.queueflow.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.util.Tuple;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserQueueService {
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    private static final String USER_QUEUE_WAIT_KEY = "user:queue:%s:wait";
    private static final String USER_QUEUE_WAIT_KEY_FOR_SCAN = "user:queue:*:wait";
    private static final String USER_QUEUE_PROCEED_KEY = "user:queue:%s:proceed";

    @Value("${scheduler.enabled}")
    private Boolean isScheduling = false;

    // 대기열 등록 API
    public Mono<Long> registerWaitQueue(final String queue, final Long userId) {
        // redis sorted set
        // - key : userId   - value : unix timestamp
        // rank
        long unixTimestamp = Instant.now().getEpochSecond();
        return reactiveRedisTemplate.opsForZSet().add(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString(), unixTimestamp)
                .filter(i -> i)     // true 인 경우만 진행(데이터 새로 추가된 경우만)
                .switchIfEmpty(Mono.error(ErrorCode.QUEUE_ALREADY_REGISTERED_USER.build()))   // 오류 처리
                .flatMap(i -> reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString()))  // true인 경우 해당 user_id 순위 반환
                .map(i -> i >= 0 ? i + 1 : i); // 순위 0부터 시작해서 i+1로 전달
    }

    // 진입을 허용
    public Mono<Long> allowUser(final String queue, final Long count) {
        // 진입을 허용 하는 단계
        // 1. wait queue 사용자 제거
        // 2. proceed queue 사용자 추가
        return reactiveRedisTemplate.opsForZSet().popMin(USER_QUEUE_WAIT_KEY.formatted(queue), count)
                .flatMap(member -> reactiveRedisTemplate.opsForZSet()
                        .add(USER_QUEUE_PROCEED_KEY.formatted(queue), member.getValue(), Instant.now().getEpochSecond()))
                .count();
    }

    // 진입이 가능한 상태인지 조회
    public Mono<Boolean> isAllowed(final String queue, final Long userId) {
        return reactiveRedisTemplate.opsForZSet()
                    .rank(USER_QUEUE_PROCEED_KEY.formatted(queue), userId.toString())
                    .defaultIfEmpty(-1L)
                    .map(rank -> rank >= 0);
    }

    // 대기번호 확인
    public Mono<Long> getRank(final String queue, final Long userId) {
        return reactiveRedisTemplate.opsForZSet()
                .rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString())
                .defaultIfEmpty(-1L)
                .map(rank -> rank >= 0 ? rank + 1 : rank);
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 10000) // 서버시작 5초 후 시작, 10초 주기로 스케줄링 진행
    public void scheduleAllowUser() {
        if (!isScheduling) {
            log.info("passed scheduling...");
            return;
        }

        log.info("called scheduling...");

        Long maxAllowUserCount = 3L;
        reactiveRedisTemplate.scan(ScanOptions.scanOptions()
                        .match(USER_QUEUE_WAIT_KEY_FOR_SCAN)
                        .count(100)
                        .build())
                .map(key -> key.split(":")[2])
                .flatMap(queue -> allowUser(queue, maxAllowUserCount)
                        .map(allowed -> Tuples.of(queue, allowed)))
                .doOnNext(tuple -> log.info("Tried %d and allowed %d members of %s queue".formatted(maxAllowUserCount, tuple.getT2(), tuple.getT1())))
                .subscribe();
    }

}
