package com.liveklass.enrollment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// A-4: CONFIRMED 취소 기한 (확정 후 N일, 기본 7일)
@ConfigurationProperties(prefix = "liveklass.enrollment")
public record EnrollmentProperties(@DefaultValue("7") long cancelPeriodDays) {
}
