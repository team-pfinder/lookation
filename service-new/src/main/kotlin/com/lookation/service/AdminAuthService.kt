package com.lookation.service

import com.lookation.controller.form.LoginForm
import com.lookation.global.error.UnauthorizedException
import com.lookation.repository.AdminUserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 관리자 전용 로그인. 일반 [AuthService]는 `accounts`만 조회하므로
 * 관리자 이메일로는 `/login` 인증이 실패하고, 관리자는 이 서비스로만 들어온다.
 */
@Service
class AdminAuthService(
    private val adminUserRepository: AdminUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val adminTokenService: AdminTokenService,
) {

    @Transactional
    fun login(form: LoginForm): TokenPair {
        val admin = adminUserRepository.findByEmail(form.email)
            ?: throw UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다")
        if (!passwordEncoder.matches(form.password, admin.passwordHash)) {
            throw UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다")
        }
        return adminTokenService.issue(admin.id)
    }
}
