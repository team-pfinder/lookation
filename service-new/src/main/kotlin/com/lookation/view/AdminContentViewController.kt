package com.lookation.view

import com.lookation.controller.form.HelpForm
import com.lookation.controller.form.NoticeForm
import com.lookation.global.error.ApiException
import com.lookation.service.ContentService
import com.lookation.view.form.HelpViewForm
import com.lookation.view.form.NoticeViewForm
import io.swagger.v3.oas.annotations.Hidden
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

@Hidden
@Controller
class AdminContentViewController(
    private val contentService: ContentService,
) {

    @GetMapping("/admin/notices")
    fun notices(@RequestParam(required = false) cursor: Long?, model: Model): String {
        model.addAttribute("page", contentService.notices(cursor, 20))
        return "admin/notices/list"
    }

    @GetMapping("/admin/notices/new")
    fun newNotice(model: Model): String {
        model.addAttribute("form", NoticeViewForm())
        return "admin/notices/form"
    }

    @PostMapping("/admin/notices")
    fun createNotice(
        @Valid @ModelAttribute("form") form: NoticeViewForm,
        bindingResult: BindingResult,
        model: Model,
    ): String {
        if (bindingResult.hasErrors()) return "admin/notices/form"
        return try {
            val created = contentService.createNotice(NoticeForm(form.title, form.content, form.important))
            "redirect:/admin/notices/${created.id}"
        } catch (exception: ApiException) {
            model.addAttribute("error", exception.message)
            model.addAttribute("renderedErrorLocally", true)
            "admin/notices/form"
        }
    }

    @GetMapping("/admin/notices/{id}")
    fun notice(@PathVariable id: Long, model: Model): String {
        model.addAttribute("notice", contentService.notice(id))
        return "admin/notices/detail"
    }

    @GetMapping("/admin/notices/{id}/edit")
    fun editNotice(@PathVariable id: Long, model: Model): String {
        val notice = contentService.notice(id)
        model.addAttribute("noticeId", id)
        model.addAttribute("form", NoticeViewForm(notice.title, notice.content, notice.important))
        return "admin/notices/form"
    }

    @PostMapping("/admin/notices/{id}")
    fun updateNotice(
        @PathVariable id: Long,
        @Valid @ModelAttribute("form") form: NoticeViewForm,
        bindingResult: BindingResult,
        model: Model,
    ): String {
        if (bindingResult.hasErrors()) {
            model.addAttribute("noticeId", id)
            return "admin/notices/form"
        }
        return try {
            contentService.updateNotice(id, NoticeForm(form.title, form.content, form.important))
            "redirect:/admin/notices/$id"
        } catch (exception: ApiException) {
            model.addAttribute("noticeId", id)
            model.addAttribute("error", exception.message)
            model.addAttribute("renderedErrorLocally", true)
            "admin/notices/form"
        }
    }

    @PostMapping("/admin/notices/{id}/delete")
    fun deleteNotice(@PathVariable id: Long, model: Model): String {
        return try {
            contentService.deleteNotice(id)
            "redirect:/admin/notices"
        } catch (exception: ApiException) {
            model.addAttribute("notice", contentService.notice(id))
            model.addAttribute("error", exception.message)
            model.addAttribute("renderedErrorLocally", true)
            "admin/notices/detail"
        }
    }

    @GetMapping("/admin/helps")
    fun helps(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) cursor: Long?,
        model: Model,
    ): String {
        model.addAttribute("page", contentService.helps(category, cursor, 20))
        model.addAttribute("categoryFilter", category.orEmpty())
        return "admin/helps/list"
    }

    @GetMapping("/admin/helps/new")
    fun newHelp(model: Model): String {
        model.addAttribute("form", HelpViewForm())
        return "admin/helps/form"
    }

    @PostMapping("/admin/helps")
    fun createHelp(
        @Valid @ModelAttribute("form") form: HelpViewForm,
        bindingResult: BindingResult,
        model: Model,
    ): String {
        if (bindingResult.hasErrors()) return "admin/helps/form"
        return try {
            val created = contentService.createHelp(HelpForm(form.category, form.title, form.content))
            "redirect:/admin/helps/${created.id}"
        } catch (exception: ApiException) {
            model.addAttribute("error", exception.message)
            model.addAttribute("renderedErrorLocally", true)
            "admin/helps/form"
        }
    }

    @GetMapping("/admin/helps/{id}")
    fun help(@PathVariable id: Long, model: Model): String {
        model.addAttribute("help", contentService.help(id))
        return "admin/helps/detail"
    }

    @GetMapping("/admin/helps/{id}/edit")
    fun editHelp(@PathVariable id: Long, model: Model): String {
        val help = contentService.help(id)
        model.addAttribute("helpId", id)
        model.addAttribute("form", HelpViewForm(help.category, help.title, help.content))
        return "admin/helps/form"
    }

    @PostMapping("/admin/helps/{id}")
    fun updateHelp(
        @PathVariable id: Long,
        @Valid @ModelAttribute("form") form: HelpViewForm,
        bindingResult: BindingResult,
        model: Model,
    ): String {
        if (bindingResult.hasErrors()) {
            model.addAttribute("helpId", id)
            return "admin/helps/form"
        }
        return try {
            contentService.updateHelp(id, HelpForm(form.category, form.title, form.content))
            "redirect:/admin/helps/$id"
        } catch (exception: ApiException) {
            model.addAttribute("helpId", id)
            model.addAttribute("error", exception.message)
            model.addAttribute("renderedErrorLocally", true)
            "admin/helps/form"
        }
    }

    @PostMapping("/admin/helps/{id}/delete")
    fun deleteHelp(@PathVariable id: Long, model: Model): String {
        return try {
            contentService.deleteHelp(id)
            "redirect:/admin/helps"
        } catch (exception: ApiException) {
            model.addAttribute("help", contentService.help(id))
            model.addAttribute("error", exception.message)
            model.addAttribute("renderedErrorLocally", true)
            "admin/helps/detail"
        }
    }
}
