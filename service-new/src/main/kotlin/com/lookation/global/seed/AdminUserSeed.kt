package com.lookation.global.seed

import com.lookation.repository.AdminUserRepository
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Ensures the seeded admin row in `admin_users`.
 * [com.lookation.global.security.AdminSeeder]가 먼저 만들면 그 자격증명을 유지하고 건너뛴다.
 */
@Component
@Order(AdminUserSeed.ORDER)
class AdminUserSeed(
    private val adminUsers: AdminUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val seedAdmin: SeedAdmin,
) : SeedStep {

    override val name = "관리자"

    @Transactional
    override fun seed(): SeedResult {
        if (adminUsers.existsByEmail(seedAdmin.email)) return SeedResult(inserted = 0, skipped = 1)
        adminUsers.insert(
            email = seedAdmin.email,
            passwordHash = requireNotNull(passwordEncoder.encode(AccountSeed.SEED_PASSWORD)),
            name = "Admin",
            nickname = "admin",
        )
        return SeedResult(inserted = 1, skipped = 0)
    }

    companion object {
        const val ORDER = 5
    }
}
