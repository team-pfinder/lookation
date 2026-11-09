package com.lookation.service

import com.lookation.global.error.UnauthorizedException
import com.lookation.global.security.JwtProperties
import com.lookation.repository.AdminRefreshTokenRepository
import com.lookation.repository.AdminRefreshTokenRow
import com.lookation.repository.AdminUserRepository
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID

/**
 * 관리자 전용 JWT·refresh 토큰 발급. 일반 [TokenService]와 분리된 테이블을 쓴다.
 * access 토큰의 `roles`는 항상 `["ADMIN"]`이고 subject는 `admin_users.id`다.
 */
@Service
class AdminTokenService(
    private val jwtEncoder: JwtEncoder,
    private val jwtProperties: JwtProperties,
    private val refreshTokenRepository: AdminRefreshTokenRepository,
    private val adminUserRepository: AdminUserRepository,
) {

    fun issue(adminId: Long): TokenPair = create(adminId, UUID.randomUUID())

    @Transactional
    fun rotate(rawRefreshToken: String, grace: Duration = Duration.ZERO): TokenPair {
        val token = refreshTokenRepository.findByHash(hash(rawRefreshToken))
            ?: throw UnauthorizedException("유효하지 않은 refresh token입니다")
        if (token.expiresAt.isBefore(OffsetDateTime.now())) {
            throw UnauthorizedException("만료된 refresh token입니다")
        }
        if (token.revokedAt != null) {
            return rotateWithinGrace(token, grace)
                ?: revokeFamilyAndFail(token, "이미 사용된 refresh token입니다")
        }
        activeAdmin(token.adminId)
            ?: revokeFamilyAndFail(token, "사용할 수 없는 계정입니다")
        if (refreshTokenRepository.revoke(token.id) != 1) {
            return rotateWithinGrace(token, grace)
                ?: revokeFamilyAndFail(token, "이미 사용된 refresh token입니다")
        }
        return create(token.adminId, token.familyId)
    }

    fun revokeAllForAdmin(adminId: Long) {
        refreshTokenRepository.revokeAllForAdmin(adminId)
    }

    fun revoke(rawRefreshToken: String) {
        refreshTokenRepository.findByHash(hash(rawRefreshToken))
            ?.let { refreshTokenRepository.revoke(it.id) }
    }

    /** HTML 로그아웃은 브라우저 탭 전체를 로그아웃시켜야 하므로 family 단위로 폐기한다. */
    fun revokeFamily(rawRefreshToken: String) {
        refreshTokenRepository.findByHash(hash(rawRefreshToken))
            ?.let { refreshTokenRepository.revokeFamily(it.familyId) }
    }

    private fun rotateWithinGrace(token: AdminRefreshTokenRow, grace: Duration): TokenPair? {
        if (grace.isZero) return null
        val revokedAt = token.revokedAt
        if (revokedAt != null && revokedAt.isBefore(OffsetDateTime.now().minus(grace))) {
            return null
        }
        if (!refreshTokenRepository.hasActiveInFamily(token.familyId)) return null
        activeAdmin(token.adminId) ?: return null
        return create(token.adminId, token.familyId)
    }

    private fun activeAdmin(adminId: Long) = adminUserRepository.findById(adminId)

    private fun revokeFamilyAndFail(token: AdminRefreshTokenRow, message: String): Nothing {
        refreshTokenRepository.revokeFamily(token.familyId)
        throw UnauthorizedException(message)
    }

    private fun create(adminId: Long, familyId: UUID): TokenPair {
        val rawRefreshToken = generateRefreshToken()
        refreshTokenRepository.save(
            adminId = adminId,
            tokenHash = hash(rawRefreshToken),
            familyId = familyId,
            expiresAt = OffsetDateTime.now().plus(jwtProperties.refreshTtl),
        )
        return TokenPair(
            accessToken = issueAccessToken(adminId),
            refreshToken = rawRefreshToken,
            expiresIn = jwtProperties.accessTtl.seconds,
        )
    }

    private fun issueAccessToken(adminId: Long): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer)
            .subject(adminId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(jwtProperties.accessTtl))
            .claim("roles", listOf("ADMIN"))
            .build()
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }

    private fun generateRefreshToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
