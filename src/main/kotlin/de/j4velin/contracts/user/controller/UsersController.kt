package de.j4velin.contracts.user.controller

import de.j4velin.contracts.user.data.User
import de.j4velin.contracts.user.data.UsersRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import javax.servlet.http.HttpServletRequest


@Controller
class UserController(private val repository: UsersRepository) {

    @PostMapping("/register")
    fun register(request: HttpServletRequest, data: User, model: Model): String {
        val user = data.copy(password = BCryptPasswordEncoder().encode(data.password))
        return try {
            repository.save(user)
            request.login(data.username, data.password)
            "redirect:/"
        } catch (e: DuplicateKeyException) {
            println("exception while trying to register user: ${e.message}")
            model.addAttribute("error", "User already exists")
            model.addAttribute("data", data)
            "register"
        }
    }

    @GetMapping("/register")
    fun register() = "register"

}