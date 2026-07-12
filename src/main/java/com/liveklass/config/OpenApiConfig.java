package com.liveklass.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("LiveKlass 과제 API")
						.version("v1")
						.description("수강 신청(A) · 정산(B) · 알림(C). 정책 결정은 docs/requirements.md 참조. "
								+ "인증: HTTP Basic, 시드 사용자 비밀번호는 `{id}!` (예: creator-1!)"))
				.components(new Components().addSecuritySchemes("basicAuth",
						new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
				.addSecurityItem(new SecurityRequirement().addList("basicAuth"));
	}
}
