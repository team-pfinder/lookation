package com.lookation.global.security

import com.lookation.global.error.UnauthorizedException
import com.lookation.service.AdminTokenService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

/**
 * 관리자 체인의 투명 갱신 필터. 일반 필터와 달리 [AdminTokenService]를 쓴다.
 * refresh가 만료·폐기되면 `/admin/login?expired=1`로 보낸다.
 */
class AdminAccessTokenRefreshFilter(
    private val adminTokenService: AdminTokenService,
    private val jwtDecoder: JwtDecoder,
    private val authCookies: AuthCookies,
    private val htmlProperties: HtmlProperties,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return SKIP_PREFIXES.any { path == it || path.startsWith("$it/") }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val accessToken = authCookies.readAccess(request)
        if (accessToken != null && !isExpiringSoon(accessToken)) {
            filterChain.doFilter(request, response)
            return
        }

        val refreshToken = authCookies.readRefresh(request)
        if (refreshToken == null) {
            filterChain.doFilter(request, response)
            return
        }

        val pair = try {
            adminTokenService.rotate(refreshToken, htmlProperties.reuseGrace)
        } catch (exception: UnauthorizedException) {
            authCookies.clear(response)
            response.sendRedirect("/admin/login?expired=1")
            return
        }

        authCookies.write(response, pair.accessToken, pair.refreshToken)
        request.setAttribute(AuthCookies.REFRESHED_ACCESS_ATTRIBUTE, pair.accessToken)
        filterChain.doFilter(request, response)
    }

    private fun isExpiringSoon(token: String): Boolean =
        try {
            val expiresAt = jwtDecoder.decode(token).expiresAt
            expiresAt == null || expiresAt.isBefore(Instant.now().plus(EXPIRY_SKEW))
        } catch (exception: JwtException) {
            true
        }

    companion object {
        private val EXPIRY_SKEW = java.time.Duration.ofSeconds(30)

        /** 정적 자산과 관리자 로그인 화면은 갱신 대상이 아니다. */
        private val SKIP_PREFIXES = listOf(
            "/css",
            "/js",
            "/images",
            "/fonts",
            "/uploads",
            "/vendor",
            "/favicon.ico",
            "/error",
            "/admin/login",
            "/actuator",
        )
    }
}
