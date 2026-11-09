package com.lookation.domain

/**
 * 관리자 전용 계정. 일반 계정(`accounts`)과 완전히 분리된 테이블(`admin_users`)을 쓴다.
 * 비밀번호는 신규 계정과 동일하게 Argon2id다.
 */
data class AdminUser(
    val id: Long,
    val email: String,
    val passwordHash: String,
    val name: String,
    val nickname: String,
)
