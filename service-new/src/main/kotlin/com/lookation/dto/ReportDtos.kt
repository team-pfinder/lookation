package com.lookation.dto

import com.lookation.domain.AccountRole
import com.lookation.domain.ReportStatus
import com.lookation.domain.ReportTarget
import com.lookation.domain.SpaceType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "신고 한 건")
data class ReportDto(
    @field:Schema(description = "신고 식별자입니다.", example = "1")
    val id: Long,

    @field:Schema(description = "신고한 계정의 식별자입니다.", example = "42")
    val reporterId: Long,

    @field:Schema(
        description = "신고 대상 종류. `SPACE`, `RESERVATION`, `REVIEW`, `QNA`, `ACCOUNT` 중 하나입니다.",
        example = "REVIEW",
    )
    val targetType: ReportTarget,

    @field:Schema(description = "신고 대상의 식별자입니다.", example = "42")
    val targetId: Long,

    @field:Schema(description = "신고 사유입니다.", example = "예약과 무관한 광고성 내용이 포함되어 있습니다.")
    val reason: String,

    @field:Schema(description = "처리 상태. `RECEIVED`, `ACCEPTED`, `REJECTED` 중 하나입니다.", example = "RECEIVED")
    val status: ReportStatus,

    @field:Schema(description = "접수 일시입니다.", example = "2026-11-20T18:00:00+09:00")
    val createdAt: OffsetDateTime,
)

@Schema(description = "관리자 신고 상세. 신고 본문에 신고자와 대상 요약을 덧붙인 화면용 모델입니다.")
data class ReportDetailDto(
    @field:Schema(description = "신고 본문입니다.")
    val report: ReportDto,

    @field:Schema(description = "신고자 계정 요약. 계정을 찾을 수 없으면 null입니다.")
    val reporter: ReportReporterDto?,

    @field:Schema(description = "신고 대상 요약. 대상이 삭제됐거나 없으면 null입니다.")
    val target: ReportTargetSummaryDto?,
)

@Schema(description = "신고자 계정 요약")
data class ReportReporterDto(
    @field:Schema(description = "계정 식별자입니다.", example = "42")
    val id: Long,

    @field:Schema(description = "가입 이메일입니다.", example = "member@lookation.local")
    val email: String,

    @field:Schema(description = "실명입니다.", example = "김하늘")
    val name: String,

    @field:Schema(description = "닉네임입니다.", example = "공간지기")
    val nickname: String,
)

/**
 * 신고 대상 종류별 요약. 화면에서 종류에 맞는 값만 골라 렌더한다.
 * 대상이 삭제되면 서비스가 null을 반환하므로 모든 필드는 nullable이다.
 */
@Schema(description = "신고 대상 요약")
data class ReportTargetSummaryDto(
    @field:Schema(description = "신고 대상 종류입니다.", example = "SPACE")
    val targetType: ReportTarget,

    @field:Schema(description = "신고 대상 식별자입니다.", example = "42")
    val targetId: Long,

    @field:Schema(description = "공간명 또는 계정 실명입니다.", example = "루프탑 파티룸 한강")
    val name: String? = null,

    @field:Schema(description = "대상 상태 값입니다.", example = "PUBLISHED")
    val status: String? = null,

    @field:Schema(description = "공간 호스트 계정 식별자입니다.", example = "1")
    val hostId: Long? = null,

    @field:Schema(description = "공간 기본 주소입니다.", example = "서울 마포구 양화로 45")
    val address: String? = null,

    @field:Schema(description = "공간 유형입니다.", example = "PARTY_ROOM")
    val spaceType: SpaceType? = null,

    @field:Schema(description = "최소 수용 인원입니다.", example = "4")
    val minCapacity: Int? = null,

    @field:Schema(description = "최대 수용 인원입니다.", example = "20")
    val maxCapacity: Int? = null,

    @field:Schema(description = "공간 설명입니다.", example = "한강이 보이는 루프탑 공간입니다.")
    val description: String? = null,

    @field:Schema(description = "예약·리뷰·문의가 가리키는 공간 식별자입니다.", example = "42")
    val spaceId: Long? = null,

    @field:Schema(description = "예약자 계정 식별자입니다.", example = "7")
    val memberId: Long? = null,

    @field:Schema(description = "이용 시작 일시입니다.", example = "2026-11-20T18:00:00+09:00")
    val startAt: OffsetDateTime? = null,

    @field:Schema(description = "이용 종료 일시입니다.", example = "2026-11-20T22:00:00+09:00")
    val endAt: OffsetDateTime? = null,

    @field:Schema(description = "결제 금액입니다.", example = "120000")
    val price: Long? = null,

    @field:Schema(description = "이용 인원입니다.", example = "8")
    val guestCount: Int? = null,

    @field:Schema(description = "리뷰 블라인드 일시. 공개 상태면 null입니다.", example = "2026-11-21T09:00:00+09:00")
    val blindedAt: OffsetDateTime? = null,

    @field:Schema(description = "리뷰·문의 작성자 닉네임입니다.", example = "공간지기")
    val author: String? = null,

    @field:Schema(description = "리뷰 평점입니다.", example = "5")
    val rating: Int? = null,

    @field:Schema(description = "리뷰·문의 본문입니다.", example = "조명과 음향이 좋았습니다.")
    val content: String? = null,

    @field:Schema(description = "리뷰·문의 답글입니다.", example = "이용해 주셔서 감사합니다.")
    val reply: String? = null,

    @field:Schema(description = "문의 제목입니다.", example = "주차 공간이 있나요")
    val title: String? = null,

    @field:Schema(description = "계정 이메일입니다.", example = "member@lookation.local")
    val email: String? = null,

    @field:Schema(description = "계정 닉네임입니다.", example = "공간지기")
    val nickname: String? = null,

    @field:Schema(description = "계정 역할 목록입니다.")
    val roles: List<AccountRole> = emptyList(),
)
