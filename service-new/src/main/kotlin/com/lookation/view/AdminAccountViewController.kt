package com.lookation.view

import com.lookation.controller.form.ApplySanctionForm
import com.lookation.domain.AccountRole
import com.lookation.global.error.ApiException
import com.lookation.global.error.BadRequestException
import com.lookation.service.AdminAccountService
import com.lookation.service.SanctionService
import com.lookation.view.form.SanctionViewForm
import io.swagger.v3.oas.annotations.Hidden
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

@Hidden
@Controller
class AdminAccountViewController(
    private val adminAccountService: AdminAccountService,
    private val sanctionService: SanctionService,
) {

    @GetMapping("/admin/accounts")
    fun accounts(
        @RequestParam(required = false) role: String?,
        @RequestParam(required = false) cursor: Long?,
        model: Model,
    ): String {
        model.addAttribute("page", adminAccountService.list(role, cursor, 20))
        model.addAttribute("roleFilter", role.orEmpty())
        model.addAttribute("roles", AccountRole.entries.map { it.name })
        return "admin/accounts/list"
    }

    @GetMapping("/admin/accounts/{id}")
    fun account(
        @PathVariable id: Long,
        model: Model,
    ): String {
        model.addAttribute("account", adminAccountService.get(id))
        model.addAttribute("sanctions", sanctionService.listByAccount(id))
        model.addAttribute("form", SanctionViewForm())
        return "admin/accounts/detail"
    }

    @PostMapping("/admin/accounts/{id}/sanctions")
    fun applySanction(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
        @Valid @ModelAttribute("form") form: SanctionViewForm,
        bindingResult: BindingResult,
        model: Model,
    ): String {
        val level = form.level
        if (bindingResult.hasErrors() || level == null) {
            model.addAttribute("account", adminAccountService.get(id))
            model.addAttribute("sanctions", sanctionService.listByAccount(id))
            return "admin/accounts/detail"
        }
        return try {
            val endsAt = parseEndsAt(form.endsAt)
            sanctionService.apply(
                jwt.requireAdminId(),
                ApplySanctionForm(accountId = id, level = level, reason = form.reason, endsAt = endsAt),
            )
            "redirect:/admin/accounts/$id"
        } catch (exception: ApiException) {
            model.addAttribute("account", adminAccountService.get(id))
            model.addAttribute("sanctions", sanctionService.listByAccount(id))
            model.addAttribute("error", exception.message)
            model.addAttribute("renderedErrorLocally", true)
            "admin/accounts/detail"
        }
    }

    @PostMapping("/admin/sanctions/{id}/revoke")
    fun revokeSanction(
        @PathVariable id: Long,
        @RequestParam(required = false) accountId: Long?,
        model: Model,
    ): String {
        sanctionService.revoke(id)
        return if (accountId != null) "redirect:/admin/accounts/$accountId" else "redirect:/admin/accounts"
    }

    private fun parseEndsAt(raw: String?): OffsetDateTime? {
        if (raw.isNullOrBlank()) return null
        val parsed = try {
            LocalDateTime.parse(raw).atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime()
        } catch (_: DateTimeParseException) {
            throw BadRequestException("종료 일시 형식이 올바르지 않습니다")
        }
        if (!parsed.isAfter(OffsetDateTime.now())) {
            throw BadRequestException("종료 일시는 현재 이후여야 합니다")
        }
        return parsed
    }
}
