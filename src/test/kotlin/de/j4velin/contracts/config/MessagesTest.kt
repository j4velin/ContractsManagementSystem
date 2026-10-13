package de.j4velin.contracts.config

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.*

class MessagesTest {

    private val contextRunner =
        ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MessageSourceAutoConfiguration::class.java))

    private lateinit var systemLocale: Locale

    @BeforeEach
    fun simulateGermanServer() {
        systemLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
    }

    @AfterEach
    fun restoreSystemLocale() = Locale.setDefault(systemLocale)

    @Test
    fun `English is served from messages properties even on a server with a German system locale`() {
        contextRunner.run {
            assertEquals("Home", it.getMessage("nav.home", null, Locale.ENGLISH))
            assertEquals("Home", it.getMessage("nav.home", null, Locale.US))
        }
    }

    @Test
    fun `German is served from messages_de properties`() {
        contextRunner.run {
            assertEquals("Registrieren", it.getMessage("nav.register", null, Locale.GERMAN))
        }
    }
}
