package com.liveklass.user;

import com.liveklass.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

	// G-4: 샘플 데이터 id(creator-1 등) 호환을 위한 varchar PK
	@Id
	@Column(length = 36)
	private String id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	@Column(nullable = false, length = 100)
	private String password;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Builder
	private User(String id, String name, String email, String password, Role role) {
		this.id = id != null ? id : UUID.randomUUID().toString();
		this.name = name;
		this.email = email;
		this.password = password;
		this.role = role;
	}
}
