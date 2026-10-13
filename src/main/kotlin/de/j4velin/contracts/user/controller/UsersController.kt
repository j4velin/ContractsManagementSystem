package de.j4velin.contracts.user.controller

import de.j4velin.contracts.user.data.User
import de.j4velin.contracts.user.data.UserDTO
import de.j4velin.contracts.user.data.UsersRepository
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping

@Controller
class UserController(private val repository: UsersRepository) {

    private val logger = LoggerFactory.getLogger(UserController::class.java)

    @PostMapping("/register")
    fun register(request: HttpServletRequest, dto: UserDTO, model: Model): String {
        val data = dto.copy(username = dto.username.trim(), mail = dto.mail.trim())
        if (data.username.isEmpty() || data.mail.isEmpty() || data.password.isBlank()) {
            return registrationError(model, data, "register.error.missingFields")
        }
        if (repository.findByUsername(data.username) != null) {
            return registrationError(model, data, "register.error.userExists")
        }
        return try {
            repository.save(User(data))
            request.login(data.username, data.password)
            "redirect:/"
        } catch (e: DuplicateKeyException) {
            // the unique index caught a concurrent registration with the same username
            logger.info("Could not register user '{}': {}", data.username, e.message)
            registrationError(model, data, "register.error.userExists")
        }
    }

    @GetMapping("/register")
    fun register() = "register"

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
}
