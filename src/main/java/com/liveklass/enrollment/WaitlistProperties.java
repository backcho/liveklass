package com.liveklass.enrollment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// A-6 승격 정책: 승격 결제 기한 (기본 24시간)
@ConfigurationProperties(prefix = "liveklass.waitlist")
public record WaitlistProperties(@DefaultValue("24") long paymentDueHours) {
}
