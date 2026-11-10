package com.lookation.integration

import com.lookation.global.security.AdminAuthCookies
import com.lookation.global.security.AuthCookies
import com.lookation.global.seed.InitializeService
import com.lookation.global.seed.SeedStep
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * 관리자 콘솔 SSR 통합 흐름. 로그인→대시보드→목록→조치 한 줄기를 MockMvc로 검증한다.
 * 좋은 테스트는 외부 행위만 검증한다: 컨트롤러 요청/응답.
 */
@SpringBootTest(
    properties = [
        "lookation.jwt.secret=0123456789abcdef0123456789abcdef",
        "lookation.encryption.key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
        "lookation.upload-dir=build/test-uploads",
        "lookation.admin.email=admin@lookation.local",
        "lookation.admin.password=lookation-admin1!",
        "lookation.test.suite=admin-console",
    ],
)
@AutoConfigureMockMvc
@Import(TestMailConfig::class)
@Testcontainers
class AdminConsoleFlowTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))

        private const val MEMBER_EMAIL = "seed-member-1@lookation.local"
        private const val MEMBER_PASSWORD = "lookation1234!"
        private const val ADMIN_EMAIL = "admin@lookation.local"
        private const val ADMIN_PASSWORD = "lookation-admin1!"
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var steps: List<SeedStep>

    @Autowired
    private lateinit var dsl: org.jooq.DSLContext

    @Autowired
    private lateinit var accountRepository: com.lookation.repository.AccountRepository

    @BeforeEach
    fun seed() {
        InitializeService(steps).seed()
    }

    @Test
    fun `관리자 전용 로그인으로만 콘솔에 진입한다`() {
        mockMvc.perform(get("/admin/login"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Welcome Admin")))

        val adminCookies = adminLogin()

        mockMvc.perform(get("/admin").cookie(*adminCookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Dashboard")))

        // 일반 로그인으로는 관리자 자격증명을 쓸 수 없다.
        val result = mockMvc.perform(
            post("/login").with(csrf()).param("email", ADMIN_EMAIL).param("password", ADMIN_PASSWORD),
        )
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("이메일 또는 비밀번호가 올바르지 않습니다")))
            .andReturn()
        assertThat(result.response.getCookie("access_token")).isNull()
    }

    @Test
    fun `일반 회원은 관리자 콘솔에 닿을 수 없고 비로그인은 리다이렉트된다`() {
        mockMvc.perform(get("/admin"))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/admin/login?next=%2Fadmin"))

        val memberCookies = memberLogin()

        mockMvc.perform(get("/admin").cookie(*memberCookies))
            .andExpect(status().isForbidden)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("접근 권한이 없습니다")))

        mockMvc.perform(get("/admin/accounts").cookie(*memberCookies))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `구 관리자 accounts 행이 남아 있어도 일반 로그인은 거부된다`() {
        accountRepository.insert(
            email = ADMIN_EMAIL,
            passwordHash = "legacy-hash",
            name = "Legacy Admin",
            nickname = "legacy-admin",
        )

        mockMvc.perform(
            post("/login").with(csrf()).param("email", ADMIN_EMAIL).param("password", ADMIN_PASSWORD),
        )
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("이메일 또는 비밀번호가 올바르지 않습니다")))

        // 관리자 전용 경로는 계속 동작한다.
        mockMvc.perform(
            post("/admin/login").with(csrf()).param("email", ADMIN_EMAIL).param("password", ADMIN_PASSWORD),
        ).andExpect(status().isFound)
    }

    @Test
    fun `관리자 토큰이 회원 쿠키에 섞여도 회원 화면을 쓸 수 없다`() {
        val adminCookies = adminLogin()
        val adminAccess = adminCookies.first { it.name == AdminAuthCookies.ACCESS_COOKIE }.value

        mockMvc.perform(get("/mypage/reservations").cookie(Cookie(AuthCookies.ACCESS_COOKIE, adminAccess)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `회원 토큰이 관리자 쿠키에 섞여도 콘솔에 들어갈 수 없다`() {
        val memberCookies = memberLogin()
        val memberAccess = memberCookies.first { it.name == AuthCookies.ACCESS_COOKIE }.value

        mockMvc.perform(get("/admin").cookie(Cookie(AdminAuthCookies.ACCESS_COOKIE, memberAccess)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `대시보드는 처리 대기 건수를 보여준다`() {
        val cookies = adminLogin()

        mockMvc.perform(get("/admin").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("공간 검수 대기")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("신고 대기")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("충전 대기")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("출금 대기")))
    }

    @Test
    fun `계정 목록과 제재 등록 해제를 흐름으로 검증한다`() {
        val cookies = adminLogin()

        mockMvc.perform(get("/admin/accounts").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("seed-member-1")))

        mockMvc.perform(get("/admin/accounts?role=MEMBER").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("seed-member-1")))

        // 상세 페이지로 팝업 없이 이동한다.
        val accountId = accountId(MEMBER_EMAIL)
        val detail = mockMvc.perform(get("/admin/accounts/$accountId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString(MEMBER_EMAIL)))
            .andReturn().response.contentAsString
        assertThat(detail).contains("제재 등록")

        // 제재 등록 후 해제까지 한 줄기.
        val applied = mockMvc.perform(
            post("/admin/accounts/$accountId/sanctions")
                .with(csrf()).cookie(*cookies)
                .param("level", "1").param("reason", "콘솔 제재 테스트"),
        )
            .andExpect(status().isFound)
            .andReturn()
        assertThat(applied.response.getRedirectedUrl()).contains("/admin/accounts/$accountId")

        val afterSanction = mockMvc.perform(get("/admin/accounts/$accountId").cookie(*cookies))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(afterSanction).contains("콘솔 제재 테스트")

        // sanction id를 찾아 해제한다.
        val sanctionId = extractSanctionId(afterSanction)
        if (sanctionId != null) {
            mockMvc.perform(
                post("/admin/sanctions/$sanctionId/revoke")
                    .with(csrf()).cookie(*cookies).param("accountId", accountId.toString()),
            ).andExpect(status().isFound)
        }
    }

    @Test
    fun `신고 검수 충전 출금 공지 도움말 qna 리뷰를 흐름으로 검증한다`() {
        val cookies = adminLogin()

        mockMvc.perform(get("/admin/reports").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("신고내역")))

        mockMvc.perform(get("/admin/inspections").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("공간검수")))

        mockMvc.perform(get("/admin/charges").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("충전관리")))

        mockMvc.perform(get("/admin/withdrawals").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("출금관리")))

        mockMvc.perform(get("/admin/notices").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("공지사항")))

        mockMvc.perform(get("/admin/helps").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("도움말")))

        mockMvc.perform(get("/admin/qnas").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Q&amp;A관리")))

        mockMvc.perform(get("/admin/reviews").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("리뷰관리")))
    }

    @Test
    fun `검수 충전 출금 판정과 공지 도움말 crud까지 연결된다`() {
        val cookies = adminLogin()

        // 검수: 시드 대기 공간이 있으면 상세→승인까지 간다. 없으면 목록만 확인한다.
        val inspectionsList = mockMvc.perform(get("/admin/inspections").cookie(*cookies))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        extractId(inspectionsList, "/admin/inspections/")?.let { spaceId ->
            mockMvc.perform(get("/admin/inspections/$spaceId").cookie(*cookies))
                .andExpect(status().isOk)
            mockMvc.perform(
                post("/admin/inspections/$spaceId/decision").with(csrf()).cookie(*cookies)
                    .param("approved", "true"),
            ).andExpect(status().isFound)
        }

        // 충전: 시드 REQUESTED가 있으면 승인까지 간다.
        val chargesList = mockMvc.perform(get("/admin/charges?status=REQUESTED").cookie(*cookies))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        extractId(chargesList, "/admin/charges/")?.let { chargeId ->
            mockMvc.perform(get("/admin/charges/$chargeId").cookie(*cookies))
                .andExpect(status().isOk)
            mockMvc.perform(
                post("/admin/charges/$chargeId/decision").with(csrf()).cookie(*cookies)
                    .param("approved", "true"),
            ).andExpect(status().isFound)
        }

        // 출금: 시드 REQUESTED가 있으면 거절까지 간다.
        val withdrawalsList = mockMvc.perform(get("/admin/withdrawals?status=REQUESTED").cookie(*cookies))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        extractId(withdrawalsList, "/admin/withdrawals/")?.let { withdrawalId ->
            mockMvc.perform(get("/admin/withdrawals/$withdrawalId").cookie(*cookies))
                .andExpect(status().isOk)
            mockMvc.perform(
                post("/admin/withdrawals/$withdrawalId/decision").with(csrf()).cookie(*cookies)
                    .param("approved", "false"),
            ).andExpect(status().isFound)
        }

        // 공지: 등록→상세→수정→삭제 흐름.
        val created = mockMvc.perform(
            post("/admin/notices").with(csrf()).cookie(*cookies)
                .param("title", "콘솔 공지 테스트").param("content", "본문"),
        ).andExpect(status().isFound).andReturn()
        val noticeId = extractId(
            created.response.getRedirectedUrl().orEmpty(), "/admin/notices/",
        )
        checkNotNull(noticeId) { "공지 생성 리다이렉트에서 id를 못 찾았다" }
        mockMvc.perform(get("/admin/notices/$noticeId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("콘솔 공지 테스트")))
        mockMvc.perform(
            post("/admin/notices/$noticeId").with(csrf()).cookie(*cookies)
                .param("title", "콘솔 공지 수정").param("content", "수정본문"),
        ).andExpect(status().isFound)
        mockMvc.perform(
            post("/admin/notices/$noticeId/delete").with(csrf()).cookie(*cookies),
        ).andExpect(status().isFound)

        // 도움말: 등록→상세→삭제 흐름.
        val helpCreated = mockMvc.perform(
            post("/admin/helps").with(csrf()).cookie(*cookies)
                .param("category", "예약").param("title", "콘솔 도움말").param("content", "도움말 본문"),
        ).andExpect(status().isFound).andReturn()
        val helpId = extractId(
            helpCreated.response.getRedirectedUrl().orEmpty(), "/admin/helps/",
        )
        checkNotNull(helpId) { "도움말 생성 리다이렉트에서 id를 못 찾았다" }
        mockMvc.perform(get("/admin/helps/$helpId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("콘솔 도움말")))
        mockMvc.perform(
            post("/admin/helps/$helpId/delete").with(csrf()).cookie(*cookies),
        ).andExpect(status().isFound)
    }

    @Test
    fun `qna 삭제와 리뷰 블라인드까지 연결된다`() {
        val cookies = adminLogin()

        val qnasList = mockMvc.perform(get("/admin/qnas").cookie(*cookies))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        extractId(qnasList, "/admin/qnas/")?.let { qnaId ->
            mockMvc.perform(get("/admin/qnas/$qnaId").cookie(*cookies))
                .andExpect(status().isOk)
            mockMvc.perform(
                post("/admin/qnas/$qnaId/delete").with(csrf()).cookie(*cookies),
            ).andExpect(status().isFound)
        }

        val reviewsList = mockMvc.perform(get("/admin/reviews").cookie(*cookies))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        extractId(reviewsList, "/admin/reviews/")?.let { reviewId ->
            mockMvc.perform(get("/admin/reviews/$reviewId").cookie(*cookies))
                .andExpect(status().isOk)
            mockMvc.perform(
                post("/admin/reviews/$reviewId/blind").with(csrf()).cookie(*cookies),
            ).andExpect(status().isFound)
        }
    }

    @Test
    fun `신고 상세와 판정까지 연결된다`() {
        val cookies = adminLogin()
        // 시드 신고 중 RECEIVED가 있다. 목록에서 첫 링크를 따라간다.
        val list = mockMvc.perform(get("/admin/reports?status=RECEIVED").cookie(*cookies))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val reportId = extractId(list, "/admin/reports/")
        if (reportId == null) return
        mockMvc.perform(get("/admin/reports/$reportId").cookie(*cookies))
            .andExpect(status().isOk)
        mockMvc.perform(
            post("/admin/reports/$reportId/decision").with(csrf()).cookie(*cookies).param("accepted", "true"),
        ).andExpect(status().isFound)
        mockMvc.perform(get("/admin/reports/$reportId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("ACCEPTED")))
    }

    private fun adminLogin(): Array<Cookie> {
        val result = mockMvc.perform(
            post("/admin/login").with(csrf()).param("email", ADMIN_EMAIL).param("password", ADMIN_PASSWORD),
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/admin"))
            .andReturn()
        val access = result.response.getCookie(AdminAuthCookies.ACCESS_COOKIE)
        val refresh = result.response.getCookie(AdminAuthCookies.REFRESH_COOKIE)
        assertThat(access).describedAs("admin access cookie").isNotNull
        assertThat(refresh).describedAs("admin refresh cookie").isNotNull
        return arrayOf(access!!, refresh!!)
    }

    private fun memberLogin(): Array<Cookie> {
        val result = mockMvc.perform(
            post("/login").with(csrf()).param("email", MEMBER_EMAIL).param("password", MEMBER_PASSWORD),
        )
            .andExpect(status().isFound)
            .andReturn()
        return arrayOf(
            requireNotNull(result.response.getCookie("access_token")),
            requireNotNull(result.response.getCookie("refresh_token")),
        )
    }

    private fun accountId(email: String): Long =
        dsl.select(com.lookation.jooq.tables.Accounts.ACCOUNTS.ID).from(com.lookation.jooq.tables.Accounts.ACCOUNTS)
            .where(com.lookation.jooq.tables.Accounts.ACCOUNTS.EMAIL.eq(email)).fetchOne(com.lookation.jooq.tables.Accounts.ACCOUNTS.ID)!!

    private fun extractSanctionId(html: String): Long? {
        val match = Regex("""/admin/sanctions/(\d+)/revoke""").find(html) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    private fun extractId(html: String, prefix: String): Long? {
        // 첫 번째 상세 링크의 id 추출 (도움말/공지 등 중복 포함). 숫자가 없을 수 있다.
        val match = Regex(Regex.escape(prefix) + """(\d+)""").find(html) ?: return null
        return match.groupValues[1].toLongOrNull()
    }
}
