package com.lookation.repository

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

data class AdminRefreshTokenRow(
    val id: Long,
    val adminId: Long,
    val familyId: UUID,
    val expiresAt: OffsetDateTime,
    val revokedAt: OffsetDateTime?,
)

/**
 * 관리자 전용 refresh 토큰 저장소. 일반 `refresh_tokens`와 분리된 테이블을 쓴다.
 */
@Repository
class AdminRefreshTokenRepository(private val dsl: DSLContext) {

    private companion object {
        val TABLE = DSL.table("admin_refresh_tokens")
        val ID = DSL.field<Long>(DSL.name("admin_refresh_tokens", "id"), Long::class.java)
        val ADMIN_ID = DSL.field<Long>(DSL.name("admin_refresh_tokens", "admin_id"), Long::class.java)
        val TOKEN_HASH = DSL.field<String>(DSL.name("admin_refresh_tokens", "token_hash"), String::class.java)
        val FAMILY_ID = DSL.field<UUID>(DSL.name("admin_refresh_tokens", "family_id"), UUID::class.java)
        val EXPIRES_AT = DSL.field<OffsetDateTime>(DSL.name("admin_refresh_tokens", "expires_at"), OffsetDateTime::class.java)
        val REVOKED_AT = DSL.field<OffsetDateTime>(DSL.name("admin_refresh_tokens", "revoked_at"), OffsetDateTime::class.java)
    }

    fun save(adminId: Long, tokenHash: String, familyId: UUID, expiresAt: OffsetDateTime) {
        dsl.insertInto(TABLE)
            .set(ADMIN_ID, adminId)
            .set(TOKEN_HASH, tokenHash)
            .set(FAMILY_ID, familyId)
            .set(EXPIRES_AT, expiresAt)
            .execute()
    }

    fun findByHash(tokenHash: String): AdminRefreshTokenRow? =
        dsl.select(ID, ADMIN_ID, FAMILY_ID, EXPIRES_AT, REVOKED_AT)
            .from(TABLE)
            .where(TOKEN_HASH.eq(tokenHash))
            .fetchOne()
            ?.let {
                AdminRefreshTokenRow(
                    id = it.get(ID),
                    adminId = it.get(ADMIN_ID),
                    familyId = it.get(FAMILY_ID),
                    expiresAt = it.get(EXPIRES_AT),
                    revokedAt = it.get(REVOKED_AT),
                )
            }

    fun revoke(id: Long): Int =
        dsl.update(TABLE)
            .set(REVOKED_AT, OffsetDateTime.now())
            .where(ID.eq(id))
            .and(REVOKED_AT.isNull)
            .execute()

    fun revokeAllForAdmin(adminId: Long) {
        dsl.update(TABLE)
            .set(REVOKED_AT, OffsetDateTime.now())
            .where(ADMIN_ID.eq(adminId))
            .and(REVOKED_AT.isNull)
            .execute()
    }

    /**
     * 계열 폐기는 재사용 탐지 시 401 응답과 함께 커밋돼야 하므로 바깥 트랜잭션과 무관하게 별도 트랜잭션으로 실행한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun revokeFamily(familyId: UUID) {
        dsl.update(TABLE)
            .set(REVOKED_AT, OffsetDateTime.now())
            .where(FAMILY_ID.eq(familyId))
            .and(REVOKED_AT.isNull)
            .execute()
    }

    fun hasActiveInFamily(familyId: UUID): Boolean =
        dsl.fetchExists(
            dsl.selectOne()
                .from(TABLE)
                .where(FAMILY_ID.eq(familyId))
                .and(REVOKED_AT.isNull),
        )
}
