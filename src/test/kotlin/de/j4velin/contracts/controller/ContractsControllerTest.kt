package de.j4velin.contracts.controller

import de.j4velin.contracts.data.Bonus
import de.j4velin.contracts.data.Contract
import de.j4velin.contracts.data.ContractsRepository
import de.j4velin.contracts.user.data.User
import de.j4velin.contracts.user.data.UsersRepository
import org.hamcrest.Matchers.matchesPattern
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Optional

@WebMvcTest(ContractsController::class)
class ContractsControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var repository: ContractsRepository

    // required by WebSecurityConfig (remember-me)
    @MockBean
    private lateinit var userDetailsService: UserDetailsService

    // required by UserLocaleResolver
    @MockBean
    private lateinit var usersRepository: UsersRepository

    private val testUser = User(id = "user1", username = "alice", password = "secret", mail = "alice@example.com")

    private fun testContract() =
        Contract(testUser.id, "type", "company", bonus = Bonus(description = "bonus")).withId("c1")

    @ParameterizedTest
    @ValueSource(strings = ["delete", "toggleCancel", "toggleCancelAck", "toggleBonus"])
    fun `state changing actions are not reachable via GET`(action: String) {
        mockMvc.perform(get("/contracts/c1/$action").with(user(testUser)))
            .andExpect(status().isMethodNotAllowed)
        verifyNoInteractions(repository)
    }

    @ParameterizedTest
    @ValueSource(strings = ["delete", "toggleCancel", "toggleCancelAck", "toggleBonus"])
    fun `state changing actions require a CSRF token`(action: String) {
        mockMvc.perform(post("/contracts/c1/$action").with(user(testUser)))
            .andExpect(status().isForbidden)
        verifyNoInteractions(repository)
    }

    @Test
    fun `delete via POST with CSRF token deletes the contract`() {
        given(repository.findById("c1")).willReturn(Optional.of(testContract()))
        mockMvc.perform(post("/contracts/c1/delete").with(user(testUser)).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/contracts"))
        verify(repository).deleteById("c1")
    }

    @Test
    fun `contract page renders delete as POST form with CSRF token`() {
        given(repository.findById("c1")).willReturn(Optional.of(testContract()))
        mockMvc.perform(get("/contracts/c1").with(user(testUser)))
            .andExpect(status().isOk)
            .andExpect(content().string(postFormWithCsrf("/contracts/c1/delete")))
    }

    @Test
    fun `contracts page renders toggles as POST forms with CSRF token`() {
        given(repository.findByUserId(testUser.id)).willReturn(listOf(testContract()))
        val result = mockMvc.perform(get("/contracts").with(user(testUser)))
            .andExpect(status().isOk)
        listOf("toggleBonus", "toggleCancel", "toggleCancelAck").forEach {
            result.andExpect(content().string(postFormWithCsrf("/contracts/c1/$it")))
        }
    }

    /**
     * @return a matcher for a page containing a POST form with the given action, which has a CSRF token as
     * first child element
     */
    private fun postFormWithCsrf(action: String) = matchesPattern(
        "(?s).*<form[^>]*method=\"post\"[^>]*action=\"$action\"[^>]*>\\s*<input type=\"hidden\" name=\"_csrf\" value=\"[^\"]+\"\\s*/?>.*"
    )
}
