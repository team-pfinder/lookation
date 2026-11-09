package com.lookation.repository

import com.lookation.domain.ReportStatus
import com.lookation.domain.ReportTarget
import com.lookation.jooq.tables.Reports.REPORTS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

data class ReportRow(
    val id: Long,
    val reporterId: Long,
    val targetType: ReportTarget,
    val targetId: Long,
    val reason: String,
    val status: ReportStatus,
    val createdAt: OffsetDateTime,
)

@Repository
class ReportRepository(private val dsl: DSLContext) {

    fun insert(reporterId: Long, targetType: ReportTarget, targetId: Long, reason: String): Long =
        dsl.insertInto(REPORTS)
            .set(REPORTS.REPORTER_ID, reporterId)
            .set(REPORTS.TARGET_TYPE, targetType.name)
            .set(REPORTS.TARGET_ID, targetId)
            .set(REPORTS.REASON, reason)
            .returningResult(REPORTS.ID)
            .fetchOne()!!
            .value1()

    fun findById(id: Long): ReportRow? =
        dsl.selectFrom(REPORTS).where(REPORTS.ID.eq(id)).fetchOne()?.let {
            ReportRow(
                it.get(REPORTS.ID), it.get(REPORTS.REPORTER_ID), ReportTarget.valueOf(it.get(REPORTS.TARGET_TYPE)),
                it.get(REPORTS.TARGET_ID), it.get(REPORTS.REASON), ReportStatus.valueOf(it.get(REPORTS.STATUS)), it.get(REPORTS.CREATED_AT),
            )
        }

    fun list(status: ReportStatus?, cursor: Long?, limit: Int): List<ReportRow> =
        list(status, null, cursor, limit)

    fun list(status: ReportStatus?, targetType: ReportTarget?, cursor: Long?, limit: Int): List<ReportRow> {
        var condition: org.jooq.Condition = org.jooq.impl.DSL.trueCondition()
        if (status != null) condition = condition.and(REPORTS.STATUS.eq(status.name))
        if (targetType != null) condition = condition.and(REPORTS.TARGET_TYPE.eq(targetType.name))
        if (cursor != null) condition = condition.and(REPORTS.ID.lt(cursor))
        return dsl.selectFrom(REPORTS)
            .where(condition)
            .orderBy(REPORTS.ID.desc())
            .limit(limit)
            .fetch()
            .map {
                ReportRow(
                    it.get(REPORTS.ID), it.get(REPORTS.REPORTER_ID), ReportTarget.valueOf(it.get(REPORTS.TARGET_TYPE)),
                    it.get(REPORTS.TARGET_ID), it.get(REPORTS.REASON), ReportStatus.valueOf(it.get(REPORTS.STATUS)), it.get(REPORTS.CREATED_AT),
                )
            }
    }

    fun countByStatus(status: ReportStatus): Long =
        dsl.fetchCount(REPORTS, REPORTS.STATUS.eq(status.name)).toLong()

    fun decide(id: Long, accepted: Boolean, adminId: Long): Int =
        dsl.update(REPORTS)
            .set(REPORTS.STATUS, (if (accepted) ReportStatus.ACCEPTED else ReportStatus.REJECTED).name)
            .set(REPORTS.ADMIN_ID, adminId)
            .set(REPORTS.PROCESSED_AT, OffsetDateTime.now())
            .where(REPORTS.ID.eq(id))
            .and(REPORTS.STATUS.eq(ReportStatus.RECEIVED.name))
            .execute()
}
