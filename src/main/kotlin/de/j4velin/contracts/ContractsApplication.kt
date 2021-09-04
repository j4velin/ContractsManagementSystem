package de.j4velin.contracts

import de.j4velin.contracts.config.UserLocaleResolver
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.LocaleResolver


@SpringBootApplication
class ContractsApplication {
    @Bean
    fun localeResolver(): LocaleResolver = UserLocaleResolver()
}

fun main(args: Array<String>) {
    runApplication<ContractsApplication>(*args)
}