package com.liveklass.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 테스트에서는 liveklass.scheduling.enabled=false로 끄고 잡 메서드를 직접 호출한다
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "liveklass.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
