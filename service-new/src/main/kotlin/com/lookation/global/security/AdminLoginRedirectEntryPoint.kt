package com.lookation.global.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 관리자 체인의 인증 진입점. 일반 체인의 `/login`이 아니라 `/admin/login`으로 보낸다.
 */
class AdminLoginRedirectEntryPoint : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        val next = nextPath(request)
        val location = if (next == null) "/admin/login" else "/admin/login?next=" + encode(next)
        response.sendRedirect(location)
    }

    private fun nextPath(request: HttpServletRequest): String? {
        if (request.method != "GET") return null
        val uri = request.requestURI ?: return null
        if (!uri.startsWith("/") || uri.startsWith("//")) return null
        if (uri == "/admin/login") return null
        val query = request.queryString
        return if (query.isNullOrBlank()) uri else "$uri?$query"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
}
