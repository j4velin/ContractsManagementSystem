package de.j4velin.contracts

import de.j4velin.contracts.config.UserLocaleResolver
import de.j4velin.contracts.user.data.UsersRepository
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.LocaleResolver
import java.time.Clock


@SpringBootApplication
class ContractsApplication {
    @Bean
    fun localeResolver(usersRepository: UsersRepository): LocaleResolver = UserLocaleResolver(usersRepository)

    /**
     * The source of "today" for all date calculations. Uses the system time zone, which the container sets via `TZ`
     */
    @Bean
    fun clock(): Clock = Clock.systemDefaultZone()
}

fun main(args: Array<String>) {
    runApplication<ContractsApplication>(*args)
}