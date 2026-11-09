package com.lookation.view

import com.lookation.domain.ReportStatus
import com.lookation.service.ReportService
import com.lookation.service.SpaceService
import com.lookation.service.WalletService
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Hidden
@Controller
class AdminDashboardViewController(
    private val spaceService: SpaceService,
    private val reportService: ReportService,
    private val walletService: WalletService,
) {

    @GetMapping("/admin")
    fun dashboard(model: Model): String {
        model.addAttribute("pendingInspections", spaceService.pendingInspections().size)
        model.addAttribute("pendingReports", reportService.pendingCount())
        model.addAttribute("pendingCharges", walletService.pendingChargeCount())
        model.addAttribute("pendingWithdrawals", walletService.pendingWithdrawalCount())
        return "admin/dashboard"
    }
}
