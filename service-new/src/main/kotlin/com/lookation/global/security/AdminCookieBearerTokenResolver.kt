package com.lookation.global.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.stereotype.Component

/**
 * 관리자 체인 전용 bearer 리졸버.
 *
 * 관리자 쿠키를 우선 읽고, 없으면 회원 쿠키를 읽는다. 회원 토큰은 `ROLE_ADMIN`이 없으므로
 * 관리자 경로에서 403으로 거부된다(로그인 화면으로 리다이렉트되지 않는다).
 */
@Component
class AdminCookieBearerTokenResolver(
    private val adminAuthCookies: AdminAuthCookies,
    private val authCookies: AuthCookies,
) : BearerTokenResolver {

    override fun resolve(request: HttpServletRequest): String? =
        adminAuthCookies.readAccess(request) ?: authCookies.readAccess(request)
}
