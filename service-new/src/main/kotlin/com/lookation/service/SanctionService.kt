package com.lookation.service

import com.lookation.controller.form.ApplySanctionForm
import com.lookation.dto.SanctionDto
import com.lookation.global.error.BadRequestException
import com.lookation.global.error.ConflictException
import com.lookation.global.error.ForbiddenException
import com.lookation.global.error.NotFoundException
import com.lookation.repository.SanctionRepository
import com.lookation.repository.SanctionRow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class SanctionService(private val sanctionRepository: SanctionRepository) {

    @Transactional(readOnly = true)
    fun checkWritable(accountId: Long) {
        val active = sanctionRepository.findActive(accountId, OffsetDateTime.now())
        if (active.isNotEmpty()) {
            val until = active.mapNotNull { it.endsAt }.minOrNull()
            val message = if (until == null) "이용이 제한된 계정입니다" else "이용이 제한된 계정입니다. 해제 예정: $until"
            throw ForbiddenException(message)
        }
    }

    @Transactional
    fun apply(adminId: Long, form: ApplySanctionForm): SanctionDto {
        if (form.level !in 1..3) throw BadRequestException("제재 단계는 1~3입니다")
        val now = OffsetDateTime.now()
        val endsAt = form.endsAt ?: defaultEndsAt(form.level, now)
        if (endsAt != null && !endsAt.isAfter(now)) {
            throw BadRequestException("종료 일시는 현재 이후여야 합니다")
        }
        val id = sanctionRepository.insert(
            accountId = form.accountId,
            reason = form.reason,
            level = form.level,
            endsAt = endsAt,
            adminId = adminId,
        )
        return requireNotNull(sanctionRepository.findById(id)).toResponse()
    }

    /** 제재 단계별 기본 기간. 1차 7일, 2차 30일, 3차 영구. */
    private fun defaultEndsAt(level: Int, now: OffsetDateTime): OffsetDateTime? =
        when (level) {
            1 -> now.plusDays(7)
            2 -> now.plusDays(30)
            else -> null
        }

    @Transactional(readOnly = true)
    fun listByAccount(accountId: Long): List<SanctionDto> =
        sanctionRepository.listByAccount(accountId).map { it.toResponse() }

    @Transactional
    fun revoke(id: Long) {
        sanctionRepository.findById(id) ?: throw NotFoundException("제재를 찾을 수 없습니다")
        if (sanctionRepository.revoke(id) != 1) {
            throw ConflictException("이미 해제된 제재입니다")
        }
    }

    private fun SanctionRow.toResponse() = SanctionDto(
        id = id,
        accountId = accountId,
        reason = reason,
        level = level,
        startsAt = startsAt,
        endsAt = endsAt,
        revokedAt = revokedAt,
    )
}
