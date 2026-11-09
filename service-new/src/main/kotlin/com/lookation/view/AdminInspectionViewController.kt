package com.lookation.view

import com.lookation.global.error.ApiException
import com.lookation.service.SpaceService
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
class AdminInspectionViewController(
    private val spaceService: SpaceService,
) {

    @GetMapping("/admin/inspections")
    fun inspections(model: Model): String {
        model.addAttribute("inspections", spaceService.pendingInspections())
        return "admin/inspections/list"
    }

    @GetMapping("/admin/inspections/{id}")
    fun inspection(
        @PathVariable id: Long,
        model: Model,
    ): String {
        model.addAttribute("space", spaceService.getForAdmin(id))
        return "admin/inspections/detail"
    }

    @PostMapping("/admin/inspections/{id}/decision")
    fun decide(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
        @RequestParam approved: Boolean,
        @RequestParam(required = false) reason: String?,
        model: Model,
    ): String {
        return try {
            spaceService.decide(jwt.requireAdminId(), id, approved, reason)
            "redirect:/admin/inspections/$id"
        } catch (exception: ApiException) {
            model.addAttribute("space", spaceService.getForAdmin(id))
            model.addAttribute("error", exception.message)
            model.addAttribute("renderedErrorLocally", true)
            "admin/inspections/detail"
        }
    }
}
