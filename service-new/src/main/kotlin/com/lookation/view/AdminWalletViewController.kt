package com.lookation.view

import com.lookation.domain.ChargeStatus
import com.lookation.domain.WithdrawalStatus
import com.lookation.global.error.ApiException
import com.lookation.service.WalletService
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
class AdminWalletViewController(
    private val walletService: WalletService,
) {

    @GetMapping("/admin/charges")
    fun charges(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) cursor: Long?,
        model: Model,
    ): String {
        model.addAttribute("page", walletService.chargesByStatus(status, cursor, 20))
        model.addAttribute("statusFilter", status.orEmpty())
        model.addAttribute("statuses", ChargeStatus.entries.map { it.name })
        return "admin/charges/list"
    }

    @GetMapping("/admin/charges/{id}")
    fun charge(
        @PathVariable id: Long,
        model: Model,
    ): String {
        model.addAttribute("charge", walletService.getCharge(id))
        return "admin/charges/detail"
    }

    @PostMapping("/admin/charges/{id}/decision")
    fun decideCharge(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
        @RequestParam approved: Boolean,
        model: Model,
    ): String {
        return try {
            walletService.decideCharge(jwt.requireAdminId(), id, approved)
            "redirect:/admin/charges/$id"
        } catch (exception: ApiException) {
            model.addAttribute("charge", walletService.getCharge(id))
            model.addAttribute("error", exception.message)
            model.addAttribute("renderedErrorLocally", true)
            "admin/charges/detail"
        }
    }

    @GetMapping("/admin/withdrawals")
    fun withdrawals(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) cursor: Long?,
        model: Model,
    ): String {
        model.addAttribute("page", walletService.withdrawalsByStatus(status, cursor, 20))
        model.addAttribute("statusFilter", status.orEmpty())
        model.addAttribute("statuses", WithdrawalStatus.entries.map { it.name })
        return "admin/withdrawals/list"
    }

    @GetMapping("/admin/withdrawals/{id}")
    fun withdrawal(
        @PathVariable id: Long,
        model: Model,
    ): String {
        model.addAttribute("withdrawal", walletService.getWithdrawal(id))
        return "admin/withdrawals/detail"
    }

    @PostMapping("/admin/withdrawals/{id}/decision")
    fun decideWithdrawal(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
        @RequestParam approved: Boolean,
        model: Model,
    ): String {
        return try {
            walletService.decideWithdrawal(jwt.requireAdminId(), id, approved)
            "redirect:/admin/withdrawals/$id"
        } catch (exception: ApiException) {
            model.addAttribute("withdrawal", walletService.getWithdrawal(id))
            model.addAttribute("error", exception.message)
            model.addAttribute("renderedErrorLocally", true)
            "admin/withdrawals/detail"
        }
    }
}
