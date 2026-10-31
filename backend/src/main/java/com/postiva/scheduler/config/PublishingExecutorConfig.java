package com.postiva.scheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class PublishingExecutorConfig {
    @Bean(name = "publishingImmediateExecutor")
    public Executor publishingImmediateExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // With a queue, a pool never grows past its core size: core is the real parallelism.
        // Six covers one post on three platforms twice over, or a scheduler batch.
        executor.setCorePoolSize(6);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("postiva-publish-now-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
