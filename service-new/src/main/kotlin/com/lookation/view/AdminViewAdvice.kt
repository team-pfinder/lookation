package com.lookation.view

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/**
 * 관리자 콘솔 화면에 공통으로 넣는 로그인 관리자 정보.
 * `admin-view` 패키지가 아니라 `view` 전체에 적용하되, 비관리자 화면에서는 null이다.
 */
@ControllerAdvice(basePackages = ["com.lookation.view"])
class AdminViewAdvice(
    private val adminViewerLookup: AdminViewerLookup,
) {

    @ModelAttribute("adminViewer")
    fun adminViewer(authentication: Authentication?): AdminViewer? = adminViewerLookup.resolve(authentication)
}
