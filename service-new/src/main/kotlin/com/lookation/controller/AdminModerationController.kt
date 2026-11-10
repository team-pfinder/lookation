package com.lookation.controller

import com.lookation.dto.PagedDto
import com.lookation.dto.QnaDto
import com.lookation.dto.ReviewDto
import com.lookation.service.QnaService
import com.lookation.service.ReviewService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "관리자 - Q&A·리뷰 중재",
    description = "관리자가 전체 Q&A와 리뷰를 조회하고 부적절한 콘텐츠를 삭제·블라인드합니다. `ADMIN`만 호출할 수 있습니다.",
)
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminModerationController(
    private val qnaService: QnaService,
    private val reviewService: ReviewService,
) {

    @Operation(
        summary = "전체 Q&A 목록",
        description = "공간별 Q&A를 최신순으로 커서 페이징해 반환합니다. `limit`이 1~100을 벗어나면 400입니다.",
    )
    @GetMapping("/qnas")
    fun qnas(
        @Parameter(description = "이전 페이지 마지막 질문 ID", example = "42")
        @RequestParam(required = false) cursor: Long?,
        @Parameter(description = "페이지 크기 (1~100)", example = "20")
        @RequestParam(defaultValue = "20") limit: Int,
    ): PagedDto<QnaDto> = qnaService.listAll(cursor, limit)

    @Operation(
        summary = "Q&A 삭제 (관리자)",
        description = "답변 여부와 관계없이 질문을 삭제합니다. 질문에 달린 답변도 함께 삭제됩니다. 없는 질문이면 404, 성공하면 본문 없이 204입니다.",
    )
    @DeleteMapping("/qnas/{id}")
    fun deleteQna(
        @Parameter(description = "삭제할 질문 ID", example = "42")
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        qnaService.deleteByAdmin(id)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "전체 리뷰 목록",
        description = "블라인드된 리뷰를 포함해 전체 리뷰를 최신순으로 커서 페이징해 반환합니다. `limit`이 1~100을 벗어나면 400입니다.",
    )
    @GetMapping("/reviews")
    fun reviews(
        @Parameter(description = "이전 페이지 마지막 리뷰 ID", example = "42")
        @RequestParam(required = false) cursor: Long?,
        @Parameter(description = "페이지 크기 (1~100)", example = "20")
        @RequestParam(defaultValue = "20") limit: Int,
    ): PagedDto<ReviewDto> = reviewService.listAll(cursor, limit)
}
