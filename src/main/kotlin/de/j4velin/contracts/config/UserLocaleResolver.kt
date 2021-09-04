package de.j4velin.contracts.config

import de.j4velin.contracts.user.data.UsersRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.i18n.SessionLocaleResolver
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Configuration
class UserLocaleResolver : SessionLocaleResolver() {

    @Autowired
    private lateinit var usersRepository: UsersRepository

    override fun resolveLocale(request: HttpServletRequest): Locale {
        val user = SecurityContextHolder.getContext().authentication?.name?.let { usersRepository.findByUsername(it) }
        return if (user != null) {
            Locale.forLanguageTag(user.locale)
        } else {
            super.resolveLocale(request)
        }
    }

    override fun setLocale(request: HttpServletRequest, response: HttpServletResponse?, locale: Locale?) {
        super.setLocale(request, response, locale)
        SecurityContextHolder.getContext().authentication?.name?.let {
            usersRepository.findByUsername(it)?.copy(locale = locale?.language ?: "en")?.apply {
                usersRepository.save(this)
            }
        }
    }
}