package com.lookation.repository

import com.lookation.domain.AdminUser
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

/**
 * 관리자 전용 계정 저장소.
 *
 * `admin_users`는 V4에서 추가된 테이블로 jOOQ 코드 생성 기준(V2) 밖에 있다.
 * 생성된 테이블 클래스 대신 DSL 동적 테이블로 접근한다.
 */
@Repository
class AdminUserRepository(private val dsl: DSLContext) {

    private companion object {
        val TABLE = DSL.table("admin_users")
        val ID = DSL.field<Long>(DSL.name("admin_users", "id"), Long::class.java)
        val EMAIL = DSL.field<String>(DSL.name("admin_users", "email"), String::class.java)
        val PASSWORD_HASH = DSL.field<String>(DSL.name("admin_users", "password_hash"), String::class.java)
        val NAME = DSL.field<String>(DSL.name("admin_users", "name"), String::class.java)
        val NICKNAME = DSL.field<String>(DSL.name("admin_users", "nickname"), String::class.java)
    }

    fun existsByEmail(email: String): Boolean =
        dsl.fetchCount(TABLE, EMAIL.equalIgnoreCase(email)) > 0

    fun insert(
        email: String,
        passwordHash: String,
        name: String,
        nickname: String,
    ): Long =
        dsl.insertInto(TABLE)
            .set(EMAIL, email)
            .set(PASSWORD_HASH, passwordHash)
            .set(NAME, name)
            .set(NICKNAME, nickname)
            .returningResult(ID)
            .fetchOne()!!
            .value1()

    fun findByEmail(email: String): AdminUser? =
        dsl.select(ID, EMAIL, PASSWORD_HASH, NAME, NICKNAME)
            .from(TABLE)
            .where(EMAIL.equalIgnoreCase(email))
            .fetchOne()
            ?.toAdminUser()

    fun findById(id: Long): AdminUser? =
        dsl.select(ID, EMAIL, PASSWORD_HASH, NAME, NICKNAME)
            .from(TABLE)
            .where(ID.eq(id))
            .fetchOne()
            ?.toAdminUser()

    private fun org.jooq.Record.toAdminUser(): AdminUser = AdminUser(
        id = get(ID),
        email = get(EMAIL),
        passwordHash = get(PASSWORD_HASH),
        name = get(NAME),
        nickname = get(NICKNAME),
    )
}
