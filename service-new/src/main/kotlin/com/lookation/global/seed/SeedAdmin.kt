package com.lookation.global.seed

import com.lookation.global.security.AdminProperties
import com.lookation.repository.AdminUserRepository
import org.springframework.stereotype.Component

/**
 * Resolves the admin account the seed should attach to inspections, sanctions, charges and
 * reports. Uses the configured `lookation.admin.email` when present so an overridden admin
 * address does not produce a second admin user.
 */
@Component
class SeedAdmin(
    private val adminUserRepository: AdminUserRepository,
    properties: AdminProperties,
) {

    val email: String = properties.email.ifBlank { DEFAULT_EMAIL }

    fun id(): Long =
        adminUserRepository.findByEmail(email)?.id
            ?: error("시드 관리자 계정이 없습니다: $email")

    companion object {
        const val DEFAULT_EMAIL = "admin@lookation.local"
    }
}
