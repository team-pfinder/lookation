package com.lookation.integration

import com.lookation.domain.AccountRole
import com.lookation.domain.ChargeStatus
import com.lookation.domain.ReportStatus
import com.lookation.domain.ReportTarget
import com.lookation.domain.ReservationStatus
import com.lookation.domain.SpaceStatus
import com.lookation.domain.WithdrawalStatus
import com.lookation.global.security.AdminAuthCookies
import com.lookation.global.security.AuthCookies
import com.lookation.global.seed.InitializeService
import com.lookation.global.seed.SeedStep
import com.lookation.repository.AccountRepository
import com.lookation.repository.ChargeRepository
import com.lookation.repository.ContentRepository
import com.lookation.repository.QnaRepository
import com.lookation.repository.ReportRepository
import com.lookation.repository.ReservationRepository
import com.lookation.repository.ReviewRepository
import com.lookation.repository.SanctionRepository
import com.lookation.repository.SpaceRepository
import com.lookation.repository.WalletRepository
import com.lookation.repository.WithdrawalRepository
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
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
import java.time.OffsetDateTime
import java.util.UUID

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
    private lateinit var dsl: DSLContext

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var spaceRepository: SpaceRepository

    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var reviewRepository: ReviewRepository

    @Autowired
    private lateinit var qnaRepository: QnaRepository

    @Autowired
    private lateinit var reportRepository: ReportRepository

    @Autowired
    private lateinit var chargeRepository: ChargeRepository

    @Autowired
    private lateinit var withdrawalRepository: WithdrawalRepository

    @Autowired
    private lateinit var sanctionRepository: SanctionRepository

    @Autowired
    private lateinit var contentRepository: ContentRepository

    @Autowired
    private lateinit var walletRepository: WalletRepository

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
    fun `관리자 로그인 next에는 제어 문자를 허용하지 않는다`() {
        mockMvc.perform(get("/admin/login").param("next", "/admin/accounts\u0001"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("이동할 수 없는 경로입니다")))
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
    fun `잘못된 제재 종료 일시는 영구 제재로 저장되지 않는다`() {
        val cookies = adminLogin()
        val accountId = accountId(MEMBER_EMAIL)
        val reason = unique("형식 오류 제재")

        fun count(): Int = dsl.fetchCount(
            com.lookation.jooq.tables.Sanctions.SANCTIONS,
            com.lookation.jooq.tables.Sanctions.SANCTIONS.ACCOUNT_ID.eq(accountId)
                .and(com.lookation.jooq.tables.Sanctions.SANCTIONS.REASON.eq(reason)),
        )

        val before = count()
        mockMvc.perform(
            post("/admin/accounts/$accountId/sanctions").with(csrf()).cookie(*cookies)
                .param("level", "1").param("reason", reason).param("endsAt", "not-a-date"),
        )
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("종료 일시 형식이 올바르지 않습니다")))
        assertThat(count()).isEqualTo(before)

        mockMvc.perform(
            post("/admin/accounts/$accountId/sanctions").with(csrf()).cookie(*cookies)
                .param("level", "1").param("reason", reason).param("endsAt", "2020-01-01T00:00"),
        )
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("종료 일시는 현재 이후여야 합니다")))
        assertThat(count()).isEqualTo(before)
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
        val target = newAccount(AccountRole.MEMBER)
        val reason = unique("콘솔 제재")
        mockMvc.perform(get("/admin/accounts/${target.id}").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString(target.email)))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("제재 등록")))

        // 제재 등록: endsAt을 비우면 1차는 7일 뒤로 채워진다.
        val before = OffsetDateTime.now()
        mockMvc.perform(
            post("/admin/accounts/${target.id}/sanctions")
                .with(csrf()).cookie(*cookies)
                .param("level", "1").param("reason", reason),
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/admin/accounts/${target.id}"))

        val sanction = sanctionRepository.listByAccount(target.id).first { it.reason == reason }
        assertThat(sanction.endsAt)
            .isBetween(before.plusDays(7).minusMinutes(1), OffsetDateTime.now().plusDays(7).plusMinutes(1))

        mockMvc.perform(get("/admin/accounts/${target.id}").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString(reason)))

        mockMvc.perform(
            post("/admin/sanctions/${sanction.id}/revoke")
                .with(csrf()).cookie(*cookies).param("accountId", target.id.toString()),
        ).andExpect(status().isFound)
        assertThat(sanctionRepository.findById(sanction.id)!!.revokedAt).isNotNull()
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

        // 검수: 대기 공간을 직접 만들고 승인한 뒤 처리가 끝난 상세에서 결정 버튼이 사라진다.
        val host = newAccount(AccountRole.HOST)
        val spaceName = unique("검수 공간")
        val spaceId = newSpace(host.id, SpaceStatus.PENDING_INSPECTION, spaceName)
        val pendingDetail = mockMvc.perform(get("/admin/inspections/$spaceId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString(spaceName)))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("검수 대기")))
            .andReturn().response.contentAsString
        assertThat(pendingDetail).contains("/admin/inspections/$spaceId/decision")

        mockMvc.perform(
            post("/admin/inspections/$spaceId/decision").with(csrf()).cookie(*cookies)
                .param("approved", "true"),
        ).andExpect(status().isFound)
        assertThat(spaceRepository.findById(spaceId)!!.status).isEqualTo(SpaceStatus.PUBLISHED)

        val processedDetail = mockMvc.perform(get("/admin/inspections/$spaceId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("이미 처리된 검수입니다")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("게시 중")))
            .andReturn().response.contentAsString
        assertThat(processedDetail).doesNotContain("/admin/inspections/$spaceId/decision")

        // 충전: 승인하면 지갑 잔액이 늘고 상태가 승인으로 바뀐다.
        val member = newAccount(AccountRole.MEMBER)
        val chargeId = chargeRepository.insert(member.id, 12_345, unique("입금자"))
        mockMvc.perform(get("/admin/charges/$chargeId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("12,345원")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("신청 대기")))
        mockMvc.perform(
            post("/admin/charges/$chargeId/decision").with(csrf()).cookie(*cookies)
                .param("approved", "true"),
        ).andExpect(status().isFound)
        assertThat(chargeRepository.findById(chargeId)!!.status).isEqualTo(ChargeStatus.APPROVED)
        assertThat(walletRepository.balance(member.id)).isEqualTo(12_345)

        // 출금: 거절하면 상태가 거절로 바뀌고 금액이 지갑으로 돌아온다.
        walletRepository.ensureWallet(host.id)
        val withdrawalId = withdrawalRepository.insert(
            host.id,
            54_321,
            "테스트은행",
            ByteArray(1) { 1 },
            ByteArray(1) { 2 },
        )
        mockMvc.perform(get("/admin/withdrawals/$withdrawalId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("54,321원")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("신청 대기")))
        mockMvc.perform(
            post("/admin/withdrawals/$withdrawalId/decision").with(csrf()).cookie(*cookies)
                .param("approved", "false"),
        ).andExpect(status().isFound)
        assertThat(withdrawalRepository.findById(withdrawalId)!!.status)
            .isEqualTo(WithdrawalStatus.REJECTED)
        assertThat(walletRepository.balance(host.id)).isEqualTo(54_321)
        mockMvc.perform(get("/admin/withdrawals/$withdrawalId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("거절")))

        // 공지: 등록 경로와 수정 경로가 각각 올바른 URL로 렌더된다.
        val noticeTitle = unique("콘솔 공지")
        mockMvc.perform(get("/admin/notices/new").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/admin/notices\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("action=\"/admin/notices/new\""))))
        val created = mockMvc.perform(
            post("/admin/notices").with(csrf()).cookie(*cookies)
                .param("title", noticeTitle).param("content", "본문"),
        ).andExpect(status().isFound).andReturn()
        val noticeId = extractId(created.response.getRedirectedUrl().orEmpty(), "/admin/notices/")
        checkNotNull(noticeId) { "공지 생성 리다이렉트에서 id를 못 찾았다" }
        mockMvc.perform(get("/admin/notices/$noticeId/edit").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/admin/notices/$noticeId\"")))
        mockMvc.perform(get("/admin/notices/$noticeId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString(noticeTitle)))
        val updatedTitle = unique("콘솔 공지 수정")
        mockMvc.perform(
            post("/admin/notices/$noticeId").with(csrf()).cookie(*cookies)
                .param("title", updatedTitle).param("content", "수정본문"),
        ).andExpect(status().isFound)
        mockMvc.perform(get("/admin/notices/$noticeId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString(updatedTitle)))
        mockMvc.perform(
            post("/admin/notices/$noticeId/delete").with(csrf()).cookie(*cookies),
        ).andExpect(status().isFound)
        assertThat(contentRepository.findNotice(noticeId)).isNull()

        // 도움말: 등록/수정 폼 URL과 삭제까지 확인한다.
        val helpTitle = unique("콘솔 도움말")
        mockMvc.perform(get("/admin/helps/new").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/admin/helps\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("action=\"/admin/helps/new\""))))
        val helpCreated = mockMvc.perform(
            post("/admin/helps").with(csrf()).cookie(*cookies)
                .param("category", "예약").param("title", helpTitle).param("content", "도움말 본문"),
        ).andExpect(status().isFound).andReturn()
        val helpId = extractId(helpCreated.response.getRedirectedUrl().orEmpty(), "/admin/helps/")
        checkNotNull(helpId) { "도움말 생성 리다이렉트에서 id를 못 찾았다" }
        mockMvc.perform(get("/admin/helps/$helpId/edit").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/admin/helps/$helpId\"")))
        mockMvc.perform(get("/admin/helps/$helpId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString(helpTitle)))
        val updatedHelp = unique("콘솔 도움말 수정")
        mockMvc.perform(
            post("/admin/helps/$helpId").with(csrf()).cookie(*cookies)
                .param("category", "예약").param("title", updatedHelp).param("content", "수정 본문"),
        ).andExpect(status().isFound)
        mockMvc.perform(get("/admin/helps/$helpId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString(updatedHelp)))
        mockMvc.perform(
            post("/admin/helps/$helpId/delete").with(csrf()).cookie(*cookies),
        ).andExpect(status().isFound)
        assertThat(contentRepository.findHelp(helpId)).isNull()
    }

    @Test
    fun `qna 삭제와 리뷰 블라인드까지 연결된다`() {
        val cookies = adminLogin()
        val member = newAccount(AccountRole.MEMBER)
        val host = newAccount(AccountRole.HOST)
        val spaceId = newSpace(host.id, SpaceStatus.PUBLISHED)

        val qnaTitle = unique("삭제할 문의")
        val qnaId = qnaRepository.insert(spaceId, member.id, qnaTitle, "문의 본문")
        mockMvc.perform(get("/admin/qnas/$qnaId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString(qnaTitle)))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("답변 대기")))
        mockMvc.perform(
            post("/admin/qnas/$qnaId/delete").with(csrf()).cookie(*cookies),
        ).andExpect(status().isFound)
        assertThat(qnaRepository.findById(qnaId)).isNull()

        val reviewId = newReview(member.id, host.id, spaceId, "블라인드 대상 리뷰")
        mockMvc.perform(get("/admin/reviews").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("공개")))
        val before = mockMvc.perform(get("/admin/reviews/$reviewId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("블라인드 대상 리뷰")))
            .andReturn().response.contentAsString
        assertThat(before).contains("/admin/reviews/$reviewId/blind")
        assertThat(before).doesNotContain("블라인드된 리뷰입니다")

        mockMvc.perform(
            post("/admin/reviews/$reviewId/blind").with(csrf()).cookie(*cookies),
        ).andExpect(status().isFound)
        assertThat(reviewRepository.findById(reviewId)!!.blindedAt).isNotNull()

        val after = mockMvc.perform(get("/admin/reviews/$reviewId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("블라인드된 리뷰입니다")))
            .andReturn().response.contentAsString
        assertThat(after).doesNotContain("/admin/reviews/$reviewId/blind")
        mockMvc.perform(get("/admin/reviews").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("블라인드")))
    }

    @Test
    fun `신고 상세는 신고자와 대상 요약을 보여주고 판정까지 연결된다`() {
        val cookies = adminLogin()
        val reporter = newAccount(AccountRole.MEMBER)
        val host = newAccount(AccountRole.HOST)
        val spaceName = unique("신고 대상 공간")
        val spaceId = newSpace(host.id, SpaceStatus.PUBLISHED, spaceName)
        val reason = unique("신고 사유")
        val reportId = reportRepository.insert(reporter.id, ReportTarget.SPACE, spaceId, reason)

        mockMvc.perform(get("/admin/reports/$reportId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString(spaceName)))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(reporter.email)))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(reason)))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("접수")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/admin/reports/$reportId/decision")))

        mockMvc.perform(
            post("/admin/reports/$reportId/decision").with(csrf()).cookie(*cookies).param("accepted", "true"),
        ).andExpect(status().isFound)
        assertThat(reportRepository.findById(reportId)!!.status).isEqualTo(ReportStatus.ACCEPTED)

        val decided = mockMvc.perform(get("/admin/reports/$reportId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("ACCEPTED")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("인정")))
            .andReturn().response.contentAsString
        assertThat(decided).doesNotContain("/admin/reports/$reportId/decision")
    }

    @Test
    fun `신고 상세는 예약 리뷰 문의 계정 대상 요약도 보여준다`() {
        val cookies = adminLogin()
        val reporter = newAccount(AccountRole.MEMBER)
        val host = newAccount(AccountRole.HOST)
        val member = newAccount(AccountRole.MEMBER)
        val spaceId = newSpace(host.id, SpaceStatus.PUBLISHED)
        val startAt = OffsetDateTime.now().plusDays(1)
        val reservationId = reservationRepository.insert(
            memberId = member.id,
            hostId = host.id,
            spaceId = spaceId,
            startAt = startAt,
            endAt = startAt.plusHours(2),
            guestCount = 3,
            price = 12_000,
            status = ReservationStatus.CONFIRMED,
            requestMessage = null,
        )
        val reservationReport = reportRepository.insert(
            reporter.id, ReportTarget.RESERVATION, reservationId, unique("예약 신고"),
        )
        mockMvc.perform(get("/admin/reports/$reservationReport").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("12,000원")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("예약 확정")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("3명")))

        val reviewContent = unique("리뷰 내용")
        val reviewId = reviewRepository.insert(reservationId, spaceId, member.id, 4, reviewContent)
        val reviewReport = reportRepository.insert(
            reporter.id, ReportTarget.REVIEW, reviewId, unique("리뷰 신고"),
        )
        mockMvc.perform(get("/admin/reports/$reviewReport").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString(reviewContent)))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("공개")))

        val qnaTitle = unique("문의 제목")
        val qnaId = qnaRepository.insert(spaceId, member.id, qnaTitle, "문의 본문")
        val qnaReport = reportRepository.insert(reporter.id, ReportTarget.QNA, qnaId, unique("문의 신고"))
        mockMvc.perform(get("/admin/reports/$qnaReport").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString(qnaTitle)))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("답변 대기")))

        val accountReport = reportRepository.insert(
            reporter.id, ReportTarget.ACCOUNT, member.id, unique("계정 신고"),
        )
        mockMvc.perform(get("/admin/reports/$accountReport").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString(member.email)))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("활동 중")))
    }

    @Test
    fun `삭제된 신고 대상은 500 대신 찾을 수 없다고 표시한다`() {
        val cookies = adminLogin()
        val reporter = newAccount(AccountRole.MEMBER)
        val host = newAccount(AccountRole.HOST)
        val spaceId = newSpace(host.id, SpaceStatus.PUBLISHED)
        val qnaId = qnaRepository.insert(spaceId, reporter.id, unique("삭제 대상 문의"), "본문")
        val reportId = reportRepository.insert(reporter.id, ReportTarget.QNA, qnaId, unique("삭제 대상 신고"))
        qnaRepository.deleteForAdmin(qnaId)

        mockMvc.perform(get("/admin/reports/$reportId").cookie(*cookies))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("신고 대상을 찾을 수 없습니다")))
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

    private data class FixtureAccount(val id: Long, val email: String)

    private fun unique(prefix: String): String = "$prefix-${UUID.randomUUID()}"

    private fun newAccount(role: AccountRole): FixtureAccount {
        val email = "${unique("fixture")}@lookation.test"
        val nickname = "fx-${UUID.randomUUID().toString().take(8)}"
        val id = accountRepository.insert(email, "unused", nickname, nickname)
        accountRepository.addRole(id, role)
        return FixtureAccount(id, email)
    }

    private fun newSpace(
        hostId: Long,
        status: SpaceStatus,
        name: String = unique("공간"),
    ): Long {
        val id = spaceRepository.insert(
            hostId = hostId,
            name = name,
            shortIntro = null,
            description = "테스트 설명",
            address = "서울 마포구",
            addressDetail = null,
            sido = "서울",
            sigungu = "마포구",
            spaceType = "PARTY_ROOM",
            minCapacity = 1,
            maxCapacity = 10,
            useHours = null,
            caution = null,
            contactEmail = null,
            contactPhone = null,
            websiteUrl = null,
            approvalRequired = false,
        )
        if (status != SpaceStatus.DRAFT) {
            spaceRepository.updateStatus(id, SpaceStatus.DRAFT, status)
        }
        return id
    }

    private fun newReview(memberId: Long, hostId: Long, spaceId: Long, content: String): Long {
        val startAt = OffsetDateTime.now().plusDays(1)
        val reservationId = reservationRepository.insert(
            memberId = memberId,
            hostId = hostId,
            spaceId = spaceId,
            startAt = startAt,
            endAt = startAt.plusHours(2),
            guestCount = 4,
            price = 10_000,
            status = ReservationStatus.COMPLETED,
            requestMessage = null,
        )
        return reviewRepository.insert(reservationId, spaceId, memberId, 5, content)
    }

    private fun accountId(email: String): Long =
        dsl.select(com.lookation.jooq.tables.Accounts.ACCOUNTS.ID).from(com.lookation.jooq.tables.Accounts.ACCOUNTS)
            .where(com.lookation.jooq.tables.Accounts.ACCOUNTS.EMAIL.eq(email)).fetchOne(com.lookation.jooq.tables.Accounts.ACCOUNTS.ID)!!

    private fun extractId(html: String, prefix: String): Long? {
        // 첫 번째 상세 링크의 id 추출 (도움말/공지 등 중복 포함). 숫자가 없을 수 있다.
        val match = Regex(Regex.escape(prefix) + """(\d+)""").find(html) ?: return null
        return match.groupValues[1].toLongOrNull()
    }
}
