package com.contractaudit.security.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketTest {

    @Test
    void allowsUpToCapacity_thenRefuses() {
        // Крошечное пополнение, чтобы за время теста токены не восстановились.
        TokenBucket bucket = new TokenBucket(3, 0.00001);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).as("четвёртый запрос сверх ёмкости").isFalse();
    }

    @Test
    void refillsOverTime() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(1, 100);   // 100 токенов/сек

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
        Thread.sleep(50);                                // ~5 токенов восстановилось
        assertThat(bucket.tryConsume()).isTrue();
    }
}
