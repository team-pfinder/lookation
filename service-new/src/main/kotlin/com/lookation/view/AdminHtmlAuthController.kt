package com.lookation.view

import com.lookation.controller.form.LoginForm
import com.lookation.global.error.ApiException
import com.lookation.global.security.AuthCookies
import com.lookation.service.AdminAuthService
import com.lookation.service.AdminTokenService
import com.lookation.view.form.LoginViewForm
import io.swagger.v3.oas.annotations.Hidden
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * 관리자 콘솔 인증 화면. 일반 `/login`과 분리된 전용 로그인이다.
 */
@Hidden
@Controller
class AdminHtmlAuthController(
    private val adminAuthService: AdminAuthService,
    private val adminTokenService: AdminTokenService,
    private val authCookies: AuthCookies,
) {

    @GetMapping("/admin/login")
    fun loginPage(
        @RequestParam(required = false) next: String?,
        @RequestParam(required = false) expired: String?,
        @RequestParam(required = false) loggedOut: String?,
        model: Model,
    ): String {
        model.addAttribute("form", LoginViewForm(next = safeAdminNext(next)))
        if (next != null && next != safeAdminNext(next)) {
            model.addAttribute("error", "이동할 수 없는 경로입니다")
        }
        if (expired != null) model.addAttribute("notice", "로그인이 만료되었습니다. 다시 로그인해 주세요.")
        if (loggedOut != null) model.addAttribute("notice", "로그아웃되었습니다.")
        return "admin/login"
    }

    @PostMapping("/admin/login")
    fun login(
        @Valid @ModelAttribute("form") form: LoginViewForm,
        bindingResult: BindingResult,
        response: HttpServletResponse,
        model: Model,
    ): String {
        if (bindingResult.hasErrors()) {
            model.addAttribute("form", form.copy(password = ""))
            return "admin/login"
        }
        return try {
            val pair = adminAuthService.login(LoginForm(email = form.email, password = form.password))
            authCookies.write(response, pair.accessToken, pair.refreshToken)
            "redirect:${safeAdminNext(form.next)}"
        } catch (exception: ApiException) {
            model.addAttribute("error", exception.message)
            model.addAttribute("form", form.copy(password = ""))
            "admin/login"
        }
    }

    @PostMapping("/admin/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): String {
        authCookies.readRefresh(request)?.let { adminTokenService.revokeFamily(it) }
        authCookies.clear(response)
        return "redirect:/admin/login?loggedOut=1"
    }

    private fun safeAdminNext(next: String?): String =
        next?.takeIf {
            it.startsWith("/admin") && !it.startsWith("//") && !it.startsWith("/admin/login") && '\\' !in it
        } ?: "/admin"
}
