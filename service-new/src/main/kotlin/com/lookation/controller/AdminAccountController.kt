package com.lookation.controller

import com.lookation.dto.AccountDto
import com.lookation.dto.PagedDto
import com.lookation.service.AdminAccountService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "관리자 - 계정",
    description = "관리자가 회원 계정을 조회합니다. `ADMIN`만 호출할 수 있습니다.",
)
@RestController
@RequestMapping("/api/v1/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
class AdminAccountController(private val adminAccountService: AdminAccountService) {

    @Operation(
        summary = "회원 계정 목록",
        description = "계정을 최신순(ID 내림차순)으로 커서 페이징해 반환합니다. `role`을 주면 `MEMBER` 또는 `HOST` 역할 보유 계정만 필터링합니다. `limit`이 1~100을 벗어나면 400입니다.",
    )
    @GetMapping
    fun list(
        @Parameter(description = "역할 필터: `MEMBER`, `HOST`. 생략하면 전체", example = "MEMBER")
        @RequestParam(required = false) role: String?,
        @Parameter(description = "이전 페이지 마지막 계정 ID. 생략하면 첫 페이지", example = "42")
        @RequestParam(required = false) cursor: Long?,
        @Parameter(description = "페이지 크기 (1~100)", example = "20")
        @RequestParam(defaultValue = "20") limit: Int,
    ): PagedDto<AccountDto> = adminAccountService.list(role, cursor, limit)

    @Operation(
        summary = "회원 계정 상세",
        description = "계정 1건의 이메일·이름·닉네임·역할을 반환합니다. 없는 id면 404입니다.",
    )
    @GetMapping("/{id}")
    fun get(
        @Parameter(description = "계정 ID", example = "42")
        @PathVariable id: Long,
    ): AccountDto = adminAccountService.get(id)
}
