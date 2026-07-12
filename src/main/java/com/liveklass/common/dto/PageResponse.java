package com.liveklass.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Schema(description = "공통 페이지 응답")
public record PageResponse<T>(
		List<T> content,
		@Schema(description = "현재 페이지 (0-base)", example = "0") int page,
		@Schema(description = "페이지 크기", example = "20") int size,
		@Schema(description = "전체 건수", example = "42") long totalElements,
		@Schema(description = "전체 페이지 수", example = "3") int totalPages
) {

	public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(
				page.getContent().stream().map(mapper).toList(),
				page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
	}
}
