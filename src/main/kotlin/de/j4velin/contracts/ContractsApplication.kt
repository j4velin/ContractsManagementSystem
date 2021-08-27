package de.j4velin.contracts

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.i18n.SessionLocaleResolver


@SpringBootApplication
class ContractsApplication {
    @Bean
    fun localeResolver(): LocaleResolver = SessionLocaleResolver()
}

fun main(args: Array<String>) {
    runApplication<ContractsApplication>(*args)
}


