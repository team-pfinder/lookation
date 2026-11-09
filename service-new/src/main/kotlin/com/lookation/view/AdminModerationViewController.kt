package com.lookation.view

import com.lookation.global.error.ApiException
import com.lookation.service.QnaService
import com.lookation.service.ReviewService
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

@Hidden
@Controller
class AdminModerationViewController(
    private val qnaService: QnaService,
    private val reviewService: ReviewService,
) {

    @GetMapping("/admin/qnas")
    fun qnas(@RequestParam(required = false) cursor: Long?, model: Model): String {
        model.addAttribute("page", qnaService.listAll(cursor, 20))
        return "admin/qnas/list"
    }

    @GetMapping("/admin/qnas/{id}")
    fun qna(@PathVariable id: Long, model: Model): String {
        model.addAttribute("qna", qnaService.get(id))
        return "admin/qnas/detail"
    }

    @PostMapping("/admin/qnas/{id}/delete")
    fun deleteQna(@PathVariable id: Long, model: Model): String {
        return try {
            qnaService.deleteByAdmin(id)
            "redirect:/admin/qnas"
        } catch (exception: ApiException) {
            model.addAttribute("qna", qnaService.get(id))
            model.addAttribute("error", exception.message)
            model.addAttribute("renderedErrorLocally", true)
            "admin/qnas/detail"
        }
    }

    @GetMapping("/admin/reviews")
    fun reviews(@RequestParam(required = false) cursor: Long?, model: Model): String {
        model.addAttribute("page", reviewService.listAll(cursor, 20))
        return "admin/reviews/list"
    }

    @GetMapping("/admin/reviews/{id}")
    fun review(@PathVariable id: Long, model: Model): String {
        model.addAttribute("review", reviewService.get(id))
        return "admin/reviews/detail"
    }

    @PostMapping("/admin/reviews/{id}/blind")
    fun blind(@PathVariable id: Long, model: Model): String {
        return try {
            reviewService.blind(id)
            "redirect:/admin/reviews/$id"
        } catch (exception: ApiException) {
            model.addAttribute("review", reviewService.get(id))
            model.addAttribute("error", exception.message)
            model.addAttribute("renderedErrorLocally", true)
            "admin/reviews/detail"
        }
    }
}
