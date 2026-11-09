package com.lookation.view

import com.lookation.repository.AdminUserRepository
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

/** 관리자 JWT 인증 주체를 화면용 관리자 정보로 바꾼다. */
@Component
class AdminViewerLookup(
    private val adminUserRepository: AdminUserRepository,
) {

    fun resolve(authentication: Authentication?): AdminViewer? {
        val jwt = authentication?.principal as? Jwt ?: return null
        if ("ROLE_ADMIN" !in authentication.authorities.map { it.authority }) return null
        val adminId = jwt.subject?.toLongOrNull() ?: return null
        val admin = adminUserRepository.findById(adminId) ?: return null
        return AdminViewer(
            id = admin.id,
            nickname = admin.nickname,
        )
    }
}
