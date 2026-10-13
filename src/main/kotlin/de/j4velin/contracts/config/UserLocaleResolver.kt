package de.j4velin.contracts.config

import de.j4velin.contracts.user.data.User
import de.j4velin.contracts.user.data.UsersRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.i18n.SessionLocaleResolver
import java.util.*

/**
 * Resolves the locale in this order: the locale chosen in the current session (e.g. via the `locale` request
 * parameter), the locale stored for the logged-in user, the `Accept-Language` header.
 *
 * The user's locale is taken from the authenticated [User] principal, which is loaded from the database on login, so
 * resolving the locale doesn't query the database.
 */
class UserLocaleResolver(private val usersRepository: UsersRepository) : SessionLocaleResolver() {

    init {
        // Used by both resolveLocale and resolveLocaleContext (and therefore also by LocaleContextHolder) when no
        // locale is set in the session
        setDefaultLocaleFunction { request ->
            currentUser()?.let { Locale.forLanguageTag(it.locale) } ?: defaultLocale ?: request.locale
        }
    }

    override fun setLocale(request: HttpServletRequest, response: HttpServletResponse?, locale: Locale?) {
        super.setLocale(request, response, locale)
        currentUser()?.let {
            usersRepository.findByUsername(it.username)?.copy(locale = locale?.language ?: "en")?.apply {
                usersRepository.save(this)
            }
        }
    }

    private fun currentUser() = SecurityContextHolder.getContext().authentication?.principal as? User
}
