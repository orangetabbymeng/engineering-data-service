package com.sulaksono.egineeringdataservice.config;

import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configures a dedicated {@link AsyncTaskExecutor} for methods annotated with {@code @Async}.
 * Pool and shutdown settings are sourced from {@code spring.task.execution.*}.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {

    @Bean("asyncExecutor")
    public ThreadPoolTaskExecutor asyncExecutor(
            ThreadPoolTaskExecutorBuilder builder) {
        return builder
                .taskDecorator(new MDCTaskDecorator())
                .build();
    }
}