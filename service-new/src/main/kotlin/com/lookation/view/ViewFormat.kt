package com.lookation.view

import com.lookation.domain.AccountStatus
import com.lookation.domain.ChargeStatus
import com.lookation.domain.FacilityCode
import com.lookation.domain.QnaStatus
import com.lookation.domain.ReportStatus
import com.lookation.domain.ReservationStatus
import com.lookation.domain.SpaceStatus
import com.lookation.domain.SpaceType
import com.lookation.domain.WithdrawalStatus
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Thymeleaf 템플릿에서 쓰는 표시 형식. 뷰마다 같은 포맷을 반복하지 않도록 모은다. */
@Component("fmt")
class ViewFormat {

    fun won(value: Long?): String =
        if (value == null) "-" else "%,d원".format(value)

    fun won(value: Number?): String =
        if (value == null) "-" else "%,d원".format(value.toLong())

    fun dateTime(value: OffsetDateTime?): String =
        value?.atZoneSameInstant(KST)?.format(DATE_TIME) ?: "-"

    fun date(value: LocalDate?): String =
        value?.format(DATE) ?: "-"

    fun time(value: LocalTime?): String =
        value?.format(TIME) ?: "-"

    fun rating(value: Double?): String =
        value?.let { "%.1f".format(it) } ?: "신규"

    fun status(value: String?): String =
        when (value) {
            "PENDING_APPROVAL" -> "승인 대기"
            "PENDING_PAYMENT" -> "결제 대기"
            "CONFIRMED" -> "예약 확정"
            "COMPLETED" -> "이용 완료"
            "CANCELLED" -> "취소됨"
            "REFUNDED" -> "환불됨"
            "REJECTED" -> "거절됨"
            "EXPIRED" -> "만료됨"
            else -> value ?: "-"
        }

    fun reportStatus(value: String?): String =
        when (value) {
            "RECEIVED" -> "접수"
            "ACCEPTED" -> "인정"
            "REJECTED" -> "기각"
            else -> value ?: "-"
        }

    fun spaceStatus(value: String?): String =
        when (value) {
            "DRAFT" -> "작성 중"
            "PENDING_INSPECTION" -> "검수 대기"
            "INSPECTION_REJECTED" -> "검수 반려"
            "PUBLISHED" -> "게시 중"
            "SUSPENDED" -> "게시 중단"
            "CLOSED" -> "종료"
            else -> value ?: "-"
        }

    fun chargeStatus(value: String?): String =
        when (value) {
            "REQUESTED" -> "신청 대기"
            "APPROVED" -> "승인"
            "REJECTED" -> "거절"
            else -> value ?: "-"
        }

    fun withdrawalStatus(value: String?): String =
        when (value) {
            "REQUESTED" -> "신청 대기"
            "APPROVED" -> "승인"
            "PAID" -> "지급 완료"
            "REJECTED" -> "거절"
            else -> value ?: "-"
        }

    fun qnaStatus(value: String?): String =
        when (value) {
            "OPEN" -> "답변 대기"
            "ANSWERED" -> "답변 완료"
            else -> value ?: "-"
        }

    fun accountStatus(value: String?): String =
        when (value) {
            "ACTIVE" -> "활동 중"
            "WITHDRAWN" -> "탈퇴"
            else -> value ?: "-"
        }

    fun spaceType(value: String?): String =
        when (value) {
            "PARTY_ROOM" -> "파티룸"
            "EMPTY_SPACE" -> "빈 공간"
            "ROOFTOP" -> "루프탑"
            "BRIDAL_SHOWER" -> "브라이덜샤워"
            "CLUB" -> "클럽"
            "WORKSHOP" -> "워크샵"
            else -> value ?: "-"
        }

    fun facility(value: FacilityCode?): String = facility(value?.name)

    fun facility(value: String?): String =
        when (value) {
            "PARKING" -> "주차"
            "WIFI" -> "와이파이"
            "PROJECTOR" -> "프로젝터"
            "SOUND" -> "음향"
            "KITCHEN" -> "주방"
            "REFRIGERATOR" -> "냉장고"
            "MICROWAVE" -> "전자레인지"
            "AIR_CONDITIONER" -> "에어컨"
            "TABLE" -> "테이블"
            "CHAIR" -> "의자"
            "BATHROOM" -> "화장실"
            "ELEVATOR" -> "엘리베이터"
            else -> value ?: "-"
        }

    fun badgeClass(status: String?): String =
        when (status) {
            "CONFIRMED", "COMPLETED", "PUBLISHED" -> "badge-success"
            "PENDING_APPROVAL", "PENDING_PAYMENT" -> "badge-warning"
            "CANCELLED", "REJECTED", "EXPIRED", "REFUNDED" -> "badge-secondary"
            else -> "badge-light"
        }

    fun status(value: ReservationStatus?): String = status(value?.name)

    fun reportStatus(value: ReportStatus?): String = reportStatus(value?.name)

    fun spaceStatus(value: SpaceStatus?): String = spaceStatus(value?.name)

    fun chargeStatus(value: ChargeStatus?): String = chargeStatus(value?.name)

    fun withdrawalStatus(value: WithdrawalStatus?): String = withdrawalStatus(value?.name)

    fun qnaStatus(value: QnaStatus?): String = qnaStatus(value?.name)

    fun accountStatus(value: AccountStatus?): String = accountStatus(value?.name)

    fun spaceType(value: SpaceType?): String = spaceType(value?.name)

    fun badgeClass(status: ReservationStatus?): String = badgeClass(status?.name)

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
