package com.liveklass.user.api;

import com.liveklass.auth.AuthUser;
import com.liveklass.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "사용자")
@RestController
@RequestMapping("/api/users")
public class UserController {

	@Operation(summary = "내 정보 조회")
	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal AuthUser authUser) {
		return UserResponse.from(authUser);
	}
}
