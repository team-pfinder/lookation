package com.lookation.service

import com.lookation.domain.AccountRole
import com.lookation.dto.AccountDto
import com.lookation.dto.PagedDto
import com.lookation.global.error.BadRequestException
import com.lookation.global.error.NotFoundException
import com.lookation.repository.AccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 관리자 콘솔 회원 목록·조회. 역할 필터는 `MEMBER`·`HOST`만 받는다.
 */
@Service
class AdminAccountService(
    private val accountRepository: AccountRepository,
) {

    @Transactional(readOnly = true)
    fun list(role: String?, cursor: Long?, limit: Int): PagedDto<AccountDto> {
        if (limit !in 1..100) throw BadRequestException("limit은 1~100입니다")
        val filter = role?.takeIf { it.isNotBlank() }?.let {
            try {
                AccountRole.valueOf(it)
            } catch (_: IllegalArgumentException) {
                throw BadRequestException("알 수 없는 역할입니다")
            }
        }
        val rows = accountRepository.listByRole(filter, cursor, limit)
        val roles = accountRepository.rolesFor(rows.map { it.id })
        return PagedDto(
            items = rows.map {
                AccountDto(
                    id = it.id,
                    email = it.email,
                    name = it.name,
                    nickname = it.nickname,
                    roles = roles[it.id].orEmpty(),
                )
            },
            nextCursor = if (rows.size == limit) rows.last().id else null,
        )
    }

    @Transactional(readOnly = true)
    fun get(id: Long): AccountDto {
        val account = accountRepository.findById(id) ?: throw NotFoundException("계정을 찾을 수 없습니다")
        return AccountDto(
            id = account.id,
            email = account.email,
            name = account.name,
            nickname = account.nickname,
            roles = accountRepository.roles(account.id),
        )
    }
}
