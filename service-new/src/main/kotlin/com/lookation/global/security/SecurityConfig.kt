package com.lookation.global.security

import com.lookation.service.AdminTokenService
import com.lookation.service.TokenService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.savedrequest.NullRequestCache

/**
 * 체인 세 개로 나눈다.
 *
 * - API 체인: 기존 계약 그대로. `Authorization: Bearer` 헤더만 읽고 CSRF를 끈다.
 * - 관리자 체인: 관리자 전용 쿠키 JWT + CSRF. 관리자 경로는 `ROLE_ADMIN`만.
 * - HTML 체인(나머지): 쿠키 JWT + CSRF. 뷰가 서비스를 직접 호출하는 SSR 화면용이다.
 *
 * 세 체인 모두 `STATELESS`다. HTML 인증은 세션이 아니라 쿠키로 유지한다.
 */
@Configuration
@EnableConfigurationProperties(HtmlProperties::class)
class SecurityConfig(
    private val authCookies: AuthCookies,
    private val adminAuthCookies: AdminAuthCookies,
    private val cookieBearerTokenResolver: CookieBearerTokenResolver,
    private val adminCookieBearerTokenResolver: AdminCookieBearerTokenResolver,
    private val tokenService: TokenService,
    private val adminTokenService: AdminTokenService,
    private val adminAccessDeniedHandler: AdminAccessDeniedHandler,
    private val jwtDecoder: JwtDecoder,
    private val htmlProperties: HtmlProperties,
) {

    @Bean
    @Order(1)
    fun apiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            securityMatcher(
                "/api/**",
                "/actuator/**",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
            )
            csrf { disable() }
            httpBasic { disable() }
            formLogin { disable() }
            logout { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            requestCache { requestCache = NullRequestCache() }
            authorizeHttpRequests {
                authorize(HttpMethod.GET, "/actuator/health", permitAll)
                authorize("/v3/api-docs/**", permitAll)
                // springdoc은 /swagger-ui.html을 /swagger-ui/index.html로 리다이렉트한다.
                authorize("/swagger-ui.html", permitAll)
                authorize("/swagger-ui/**", permitAll)
                authorize(HttpMethod.GET, "/api/v1/spaces", permitAll)
                authorize(HttpMethod.GET, "/api/v1/spaces/*", permitAll)
                authorize(HttpMethod.GET, "/api/v1/spaces/*/images", permitAll)
                authorize(HttpMethod.GET, "/api/v1/spaces/*/reviews", permitAll)
                authorize(HttpMethod.GET, "/api/v1/spaces/*/qnas", permitAll)
                authorize(HttpMethod.GET, "/api/v1/spaces/*/prices", permitAll)
                authorize(HttpMethod.GET, "/api/v1/spaces/*/availability", permitAll)
                authorize(HttpMethod.GET, "/api/v1/qnas/*", permitAll)
                authorize(HttpMethod.GET, "/api/v1/notices", permitAll)
                authorize(HttpMethod.GET, "/api/v1/notices/*", permitAll)
                authorize(HttpMethod.GET, "/api/v1/helps", permitAll)
                authorize(HttpMethod.GET, "/api/v1/helps/*", permitAll)
                authorize("/api/v1/auth/**", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer {
                bearerTokenResolver = DefaultBearerTokenResolver()
                jwt { jwtAuthenticationConverter = claimRolesConverter() }
            }
        }
        return http.build()
    }

    @Bean
    @Order(2)
    fun adminSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            securityMatcher("/admin/**")
            csrf { csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse() }
            httpBasic { disable() }
            formLogin { disable() }
            logout { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            requestCache { requestCache = NullRequestCache() }
            authorizeHttpRequests {
                authorize(HttpMethod.GET, "/admin/login", permitAll)
                authorize(HttpMethod.POST, "/admin/login", permitAll)
                authorize("/admin/**", hasAuthority("ROLE_ADMIN"))
            }
            exceptionHandling {
                authenticationEntryPoint = AdminLoginRedirectEntryPoint()
                accessDeniedHandler = adminAccessDeniedHandler
            }
            oauth2ResourceServer {
                bearerTokenResolver = adminCookieBearerTokenResolver
                jwt { jwtAuthenticationConverter = claimRolesConverter() }
            }
            addFilterBefore<BearerTokenAuthenticationFilter>(
                AdminAccessTokenRefreshFilter(adminTokenService, jwtDecoder, adminAuthCookies, htmlProperties),
            )
        }
        return http.build()
    }

    @Bean
    @Order(3)
    fun htmlSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            securityMatcher("/**")
            csrf { csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse() }
            httpBasic { disable() }
            formLogin { disable() }
            logout { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            requestCache { requestCache = NullRequestCache() }
            authorizeHttpRequests {
                authorize(HttpMethod.GET, "/", permitAll)
                authorize(HttpMethod.GET, "/spaces", permitAll)
                authorize(HttpMethod.GET, "/spaces/*", permitAll)
                authorize(HttpMethod.GET, "/login", permitAll)
                authorize(HttpMethod.POST, "/login", permitAll)
                authorize(HttpMethod.GET, "/signup", permitAll)
                authorize(HttpMethod.POST, "/signup", permitAll)
                authorize(HttpMethod.POST, "/signup/email-verification", permitAll)
                authorize(HttpMethod.GET, "/password-reset", permitAll)
                authorize(HttpMethod.POST, "/password-reset", permitAll)
                authorize(HttpMethod.POST, "/password-reset/confirm", permitAll)
                authorize("/css/**", permitAll)
                authorize("/js/**", permitAll)
                authorize("/vendor/**", permitAll)
                authorize("/images/**", permitAll)
                authorize("/fonts/**", permitAll)
                authorize("/uploads/**", permitAll)
                authorize("/favicon.ico", permitAll)
                authorize("/error", permitAll)
                // 회원 화면은 MEMBER/HOST만 쓴다. 관리자 토큰이 회원 쿠키에 섞여 들어와도
                // 관리자 권한으로 회원 데이터를 조작하지 못하게 한다.
                authorize(anyRequest, hasAnyRole("MEMBER", "HOST"))
            }
            exceptionHandling { authenticationEntryPoint = LoginRedirectEntryPoint() }
            oauth2ResourceServer {
                bearerTokenResolver = cookieBearerTokenResolver
                jwt { jwtAuthenticationConverter = claimRolesConverter() }
            }
            addFilterBefore<BearerTokenAuthenticationFilter>(
                AccessTokenRefreshFilter(tokenService, jwtDecoder, authCookies, htmlProperties),
            )
        }
        return http.build()
    }

    private fun claimRolesConverter(): JwtAuthenticationConverter {
        val rolesConverter = JwtGrantedAuthoritiesConverter()
        rolesConverter.setAuthoritiesClaimName("roles")
        rolesConverter.setAuthorityPrefix("ROLE_")
        return JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(rolesConverter)
        }
    }
}
