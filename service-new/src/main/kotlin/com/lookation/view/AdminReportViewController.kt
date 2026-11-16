package com.lookation.view

import com.lookation.domain.ReportStatus
import com.lookation.domain.ReportTarget
import com.lookation.global.error.ApiException
import com.lookation.service.ReportService
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

@Hidden
@Controller
class AdminReportViewController(
    private val reportService: ReportService,
) {

    @GetMapping("/admin/reports")
    fun reports(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) targetType: String?,
        @RequestParam(required = false) cursor: Long?,
        model: Model,
    ): String {
        model.addAttribute("page", reportService.list(status, targetType, cursor, 20))
        model.addAttribute("statusFilter", status.orEmpty())
        model.addAttribute("targetFilter", targetType.orEmpty())
        model.addAttribute("statuses", ReportStatus.entries.map { it.name })
        model.addAttribute("targets", ReportTarget.entries.map { it.name })
        return "admin/reports/list"
    }

    @GetMapping("/admin/reports/{id}")
    fun report(
        @PathVariable id: Long,
        model: Model,
    ): String {
        addDetail(model, id)
        return "admin/reports/detail"
    }

    @PostMapping("/admin/reports/{id}/decision")
    fun decide(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
        @RequestParam accepted: Boolean,
        model: Model,
    ): String {
        return try {
            reportService.decide(jwt.requireAdminId(), id, accepted)
            "redirect:/admin/reports/$id"
        } catch (exception: ApiException) {
            addDetail(model, id)
            model.addAttribute("error", exception.message)
            model.addAttribute("renderedErrorLocally", true)
            "admin/reports/detail"
        }
    }

    private fun addDetail(model: Model, id: Long) {
        val detail = reportService.detail(id)
        model.addAttribute("report", detail.report)
        model.addAttribute("detail", detail)
    }
}
