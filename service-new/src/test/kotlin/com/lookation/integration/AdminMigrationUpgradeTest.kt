package com.lookation.integration

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager

/**
 * V4가 기존 DB를 업그레이드할 때 관리자 귀속을 보존하는지 검증한다.
 *
 * 신규 DB만 테스트하면 놓치는 경로라, V3까지 적용한 뒤 레거시 ADMIN 데이터를 넣고 V4를 적용한다.
 */
@Testcontainers
class AdminMigrationUpgradeTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
    }

    @Test
    fun `V4 upgrade preserves legacy admin attribution`() {
        flyway().target(MigrationVersion.fromVersion("3")).load().migrate()

        val legacyAdminId: Long
        val memberId: Long
        connection().use { conn ->
            legacyAdminId = insertReturningId(
                conn,
                "insert into accounts (email, password_hash, name, nickname) " +
                    "values ('legacy-admin@lookation.local', 'hash', 'Legacy Admin', 'legacy-admin')",
            )
            conn.createStatement().use {
                it.executeUpdate("insert into account_roles (account_id, role) values ($legacyAdminId, 'ADMIN')")
            }
            memberId = insertReturningId(
                conn,
                "insert into accounts (email, password_hash, name, nickname) " +
                    "values ('legacy-member@lookation.local', 'hash', 'Legacy Member', 'legacy-member')",
            )
            conn.createStatement().use {
                it.executeUpdate(
                    "insert into sanctions (account_id, reason, level, admin_id) " +
                        "values ($memberId, 'legacy-attribution', 1, $legacyAdminId)",
                )
                // ADMIN 역할이 없던 비정상 admin_id는 NULL로 보존되어야 한다.
                it.executeUpdate(
                    "insert into sanctions (account_id, reason, level, admin_id) " +
                        "values ($legacyAdminId, 'dangling-attribution', 1, $memberId)",
                )
            }
        }

        flyway().target(MigrationVersion.fromVersion("4")).load().migrate()

        connection().use { conn ->
            conn.createStatement().use { statement ->
                statement.executeQuery(
                    "select id from admin_users where email = 'legacy-admin@lookation.local'",
                ).use { rs ->
                    assertThat(rs.next()).isTrue()
                    assertThat(rs.getLong(1)).isEqualTo(legacyAdminId)
                }
                statement.executeQuery(
                    "select admin_id from sanctions where reason = 'legacy-attribution'",
                ).use { rs ->
                    assertThat(rs.next()).isTrue()
                    assertThat(rs.getLong(1)).isEqualTo(legacyAdminId)
                }
                statement.executeQuery(
                    "select admin_id from sanctions where reason = 'dangling-attribution'",
                ).use { rs ->
                    assertThat(rs.next()).isTrue()
                    rs.getLong(1)
                    assertThat(rs.wasNull()).isTrue()
                }
                statement.executeQuery("select count(*) from account_roles where role = 'ADMIN'").use { rs ->
                    rs.next()
                    assertThat(rs.getInt(1)).isZero()
                }

                // 명시 id 삽입 뒤 identity 시퀀스가 충돌하지 않아야 한다.
                statement.executeQuery(
                    "insert into admin_users (email, password_hash, name, nickname) " +
                        "values ('new-admin@lookation.local', 'hash', 'New Admin', 'new-admin') returning id",
                ).use { rs ->
                    assertThat(rs.next()).isTrue()
                    assertThat(rs.getLong(1)).isGreaterThan(legacyAdminId)
                }
            }
        }
    }

    private fun flyway(): org.flywaydb.core.api.configuration.FluentConfiguration = Flyway.configure()
        .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        .locations("classpath:db/migration")

    private fun connection(): Connection =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

    private fun insertReturningId(connection: Connection, sql: String): Long =
        connection.createStatement().use { statement ->
            statement.executeQuery("$sql returning id").use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }
}
