package de.j4velin.contracts.user.controller

import de.j4velin.contracts.user.data.User
import de.j4velin.contracts.user.data.UserDTO
import de.j4velin.contracts.user.data.UsersRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.authentication.AuthenticationTrustResolverImpl
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping

@Controller
class UserController(
    private val repository: UsersRepository,
    private val passwordEncoder: PasswordEncoder,
    csrfTokenRepository: CsrfTokenRepository
) {

    private val logger = LoggerFactory.getLogger(UserController::class.java)

    private val trustResolver = AuthenticationTrustResolverImpl()

    /**
     * Applied after logging in a newly registered user. [HttpServletRequest.login] only authenticates, so this does
     * what the form login does by default: it changes the session id (protection against session fixation) and
     * replaces the CSRF token. The token is replaced in the [CsrfTokenRepository] bean, which the security filter chain
     * of WebSecurityConfig uses as well.
     */
    private val sessionAuthenticationStrategy = CompositeSessionAuthenticationStrategy(
        listOf(ChangeSessionIdAuthenticationStrategy(), CsrfAuthenticationStrategy(csrfTokenRepository))
    )

    @PostMapping("/register")
    fun register(request: HttpServletRequest, response: HttpServletResponse, dto: UserDTO, model: Model): String {
        if (isLoggedIn()) {
            return REDIRECT_LOGGED_IN
        }
        val data = dto.copy(username = dto.username.trim(), mail = dto.mail.trim())
        if (data.username.isEmpty() || data.mail.isEmpty() || data.password.isBlank()) {
            return registrationError(model, data, "register.error.missingFields")
        }
        if (repository.findByUsername(data.username) != null) {
            return registrationError(model, data, "register.error.userExists")
        }
        // encode() is declared nullable, but only returns null for a null password
        val encodedPassword = checkNotNull(passwordEncoder.encode(data.password))
        return try {
            repository.save(User(data, encodedPassword))
            request.login(data.username, data.password)
            val authentication = checkNotNull(SecurityContextHolder.getContext().authentication) {
                "No authentication after logging in '${data.username}'"
            }
            sessionAuthenticationStrategy.onAuthentication(authentication, request, response)
            "redirect:/"
        } catch (e: DuplicateKeyException) {
            // the unique index caught a concurrent registration with the same username
            logger.info("Could not register user '{}': {}", data.username, e.message)
            registrationError(model, data, "register.error.userExists")
        }
    }

    @GetMapping("/register")
    fun register() = if (isLoggedIn()) REDIRECT_LOGGED_IN else "register"

    /**
     * @return true, if the current user is logged in (also via remember-me), false for anonymous users
     */
    private fun isLoggedIn() = trustResolver.isAuthenticated(SecurityContextHolder.getContext().authentication)

    /**
     * Shows the registration form again with an error message, keeping the entered username and mail address
     *
     * @param messageKey the message key of the error to show
     */
    private fun registrationError(model: Model, data: UserDTO, messageKey: String): String {
        model.addAttribute("error", messageKey)
        model.addAttribute("username", data.username)
        model.addAttribute("mail", data.mail)
        return "register"
    }

    private companion object {
        /** Logged-in users can't register another account */
        const val REDIRECT_LOGGED_IN = "redirect:/contracts"
    }
}
