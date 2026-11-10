package com.lookation.global.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 관리자 체인의 access/refresh 쿠키.
 *
 * 일반 [AuthCookies]와 쿠키 이름을 분리한다. 한 브라우저에 회원 세션과 관리자 세션이 함께 있어도
 * 서로의 토큰을 읽지 않으므로, 관리자 토큰이 회원 화면에서 회원 자격으로 소비되는 교차 역할 혼동을 막는다.
 */
@Component
class AdminAuthCookies(
    private val htmlProperties: HtmlProperties,
    private val jwtProperties: JwtProperties,
) {

    fun readAccess(request: HttpServletRequest): String? =
        request.getAttribute(REFRESHED_ACCESS_ATTRIBUTE) as String?
            ?: read(request, ACCESS_COOKIE)

    fun readRefresh(request: HttpServletRequest): String? = read(request, REFRESH_COOKIE)

    fun write(response: HttpServletResponse, accessToken: String, refreshToken: String) {
        add(response, ACCESS_COOKIE, accessToken, jwtProperties.accessTtl)
        add(response, REFRESH_COOKIE, refreshToken, jwtProperties.refreshTtl)
    }

    fun clear(response: HttpServletResponse) {
        add(response, ACCESS_COOKIE, "", Duration.ZERO)
        add(response, REFRESH_COOKIE, "", Duration.ZERO)
    }

    private fun read(request: HttpServletRequest, name: String): String? =
        request.cookies
            ?.firstOrNull { it.name == name }
            ?.value
            ?.takeIf { it.isNotBlank() }

    private fun add(response: HttpServletResponse, name: String, value: String, maxAge: Duration) {
        val cookie = ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(htmlProperties.secureCookie)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    companion object {
        const val ACCESS_COOKIE = "admin_access_token"
        const val REFRESH_COOKIE = "admin_refresh_token"

        /** 갱신 필터가 새 access 토큰을 요청에 실어 보낼 때 쓰는 속성 이름. */
        const val REFRESHED_ACCESS_ATTRIBUTE = "lookation.refreshedAdminAccessToken"
    }
}
