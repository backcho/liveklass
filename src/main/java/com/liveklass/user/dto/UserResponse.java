package com.liveklass.user.dto;

import com.liveklass.auth.AuthUser;
import com.liveklass.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 정보")
public record UserResponse(
		@Schema(example = "creator-1") String id,
		@Schema(example = "김강사") String name,
		@Schema(example = "creator-1@liveklass.local") String email,
		@Schema(example = "CREATOR") Role role
) {

	public static UserResponse from(AuthUser authUser) {
		return new UserResponse(authUser.getId(), authUser.getName(), authUser.getEmail(), authUser.getRole());
	}
}
