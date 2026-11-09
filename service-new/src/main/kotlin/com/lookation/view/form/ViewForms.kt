package com.lookation.view.form

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

data class LoginViewForm(
    @field:Email @field:NotBlank
    val email: String = "",

    @field:NotBlank
    val password: String = "",

    val next: String? = null,
)

data class SignupRequestViewForm(
    @field:Email @field:NotBlank
    val email: String = "",
)

data class SignupViewForm(
    @field:Email @field:NotBlank
    val email: String = "",

    @field:NotBlank @field:Size(min = 8, max = 72)
    val password: String = "",

    @field:NotBlank @field:Size(max = 50)
    val name: String = "",

    @field:NotBlank @field:Size(max = 30)
    val nickname: String = "",

    @field:NotBlank @field:Pattern(regexp = "\\d{6}")
    val code: String = "",
)

data class PasswordResetRequestViewForm(
    @field:Email @field:NotBlank
    val email: String = "",
)

data class PasswordResetViewForm(
    @field:Email @field:NotBlank
    val email: String = "",

    @field:NotBlank @field:Pattern(regexp = "\\d{6}")
    val code: String = "",

    @field:NotBlank @field:Size(min = 8, max = 72)
    val newPassword: String = "",
)

data class SanctionViewForm(
    @field:Positive @field:Max(3)
    val level: Int? = null,

    @field:NotBlank @field:Size(max = 300)
    val reason: String = "",

    val endsAt: String? = null,
)

data class NoticeViewForm(
    @field:NotBlank @field:Size(max = 200)
    val title: String = "",

    @field:NotBlank
    val content: String = "",

    val important: Boolean = false,
)

data class HelpViewForm(
    @field:NotBlank @field:Size(max = 50)
    val category: String = "",

    @field:NotBlank @field:Size(max = 200)
    val title: String = "",

    @field:NotBlank
    val content: String = "",
)

data class ReservationViewForm(
    @field:NotNull
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val date: LocalDate? = null,

    @field:NotNull @field:Positive
    val priceRuleId: Long? = null,

    @field:NotNull @field:Positive @field:Max(32767)
    val guestCount: Int? = null,

    @field:Size(max = 300)
    val requestMessage: String? = null,
)
