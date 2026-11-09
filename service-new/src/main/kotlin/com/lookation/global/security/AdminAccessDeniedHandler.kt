package com.lookation.global.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import java.io.IOException

/**
 * 관리자 체인의 권한 거부 처리. 일반 회원이 관리자 경로에 닿으면 403을 렌더한다.
 */
@Component
class AdminAccessDeniedHandler : AccessDeniedHandler {

    @Throws(IOException::class)
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        if (response.isCommitted) return
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "text/html;charset=UTF-8"
        response.writer.write(
            """
            <!DOCTYPE html>
            <html lang="ko"><head><meta charset="UTF-8"><title>접근 권한이 없습니다 · Lookation</title></head>
            <body><h1>접근 권한이 없습니다</h1><p>이 화면에 접근할 권한이 없습니다.</p>
            <p><a href="/admin/login">관리자 로그인으로 이동</a></p></body></html>
            """.trimIndent(),
        )
    }
}
