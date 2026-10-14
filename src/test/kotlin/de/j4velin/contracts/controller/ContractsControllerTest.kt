package de.j4velin.contracts.controller

import de.j4velin.contracts.config.WebSecurityConfig
import de.j4velin.contracts.data.Bonus
import de.j4velin.contracts.data.Cancellation
import de.j4velin.contracts.data.Contract
import de.j4velin.contracts.data.ContractsRepository
import de.j4velin.contracts.user.data.User
import de.j4velin.contracts.user.data.UsersRepository
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.matchesPattern
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Period
import java.util.Optional

@WebMvcTest(ContractsController::class)
// SecurityFilterChain beans aren't picked up by @WebMvcTest automatically
@Import(WebSecurityConfig::class)
class ContractsControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var repository: ContractsRepository

    // required by WebSecurityConfig (remember-me)
    @MockitoBean
    private lateinit var userDetailsService: UserDetailsService

    // required by UserLocaleResolver
    @MockitoBean
    private lateinit var usersRepository: UsersRepository

    private val testUserId = "user1"
    private val testUser = User(id = testUserId, username = "alice", password = "secret", mail = "alice@example.com")

    private fun testContract() =
        Contract(testUserId, "type", "company", bonus = Bonus(description = "bonus")).withId("c1")

    private fun otherUsersContract() = Contract("user2", "type", "company").withId("c1")

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
    fun `contract of another user is not found`() {
        given(repository.findById("c1")).willReturn(Optional.of(otherUsersContract()))
        mockMvc.perform(get("/contracts/c1").with(user(testUser)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `contract of another user is not deleted`() {
        given(repository.findById("c1")).willReturn(Optional.of(otherUsersContract()))
        mockMvc.perform(post("/contracts/c1/delete").with(user(testUser)).with(csrf()))
            .andExpect(status().isNotFound)
        verify(repository, never()).deleteById(anyString())
        verify(repository, never()).save(any(Contract::class.java))
    }

    @ParameterizedTest
    @ValueSource(strings = ["toggleCancel", "toggleCancelAck", "toggleBonus"])
    fun `contract of another user is not toggled`(action: String) {
        given(repository.findById("c1")).willReturn(Optional.of(otherUsersContract()))
        mockMvc.perform(post("/contracts/c1/$action").with(user(testUser)).with(csrf()))
            .andExpect(status().isNotFound)
        verify(repository, never()).save(any(Contract::class.java))
    }

    @Test
    fun `contract of another user is not updated`() {
        given(repository.findById("c1")).willReturn(Optional.of(otherUsersContract()))
        mockMvc.perform(
            post("/contracts/c1").with(user(testUser)).with(csrf())
                .param("type", "type").param("company", "company")
        ).andExpect(status().isNotFound)
        verify(repository, never()).save(any(Contract::class.java))
    }

    @Test
    fun `adding a contract stores trimmed type and company`() {
        mockMvc.perform(
            post("/contracts").with(user(testUser)).with(csrf())
                .param("type", "  Rent ").param("company", " ACME  ")
        ).andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/contracts"))
        val captor = ArgumentCaptor.forClass(Contract::class.java)
        verify(repository).save(captor.capture())
        assertEquals("Rent", captor.value.type)
        assertEquals("ACME", captor.value.company)
        assertEquals(testUserId, captor.value.userId)
    }

    @ParameterizedTest
    @CsvSource("'', company", "'   ', company", "type, ''", "type, '  '")
    fun `adding a contract with blank type or company is rejected`(type: String, company: String) {
        mockMvc.perform(
            post("/contracts").with(user(testUser)).with(csrf())
                .param("type", type).param("company", company)
        ).andExpect(status().isBadRequest)
        verify(repository, never()).save(any(Contract::class.java))
    }

    @Test
    fun `updating a contract stores trimmed type and company`() {
        given(repository.findById("c1")).willReturn(Optional.of(testContract()))
        mockMvc.perform(
            post("/contracts/c1").with(user(testUser)).with(csrf())
                .param("type", " Rent").param("company", "ACME ")
        ).andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/contracts"))
        val captor = ArgumentCaptor.forClass(Contract::class.java)
        verify(repository).save(captor.capture())
        assertEquals("c1", captor.value.id)
        assertEquals("Rent", captor.value.type)
        assertEquals("ACME", captor.value.company)
    }

    @ParameterizedTest
    @CsvSource("'', company", "'   ', company", "type, ''", "type, '  '")
    fun `updating a contract with blank type or company is rejected`(type: String, company: String) {
        given(repository.findById("c1")).willReturn(Optional.of(testContract()))
        mockMvc.perform(
            post("/contracts/c1").with(user(testUser)).with(csrf())
                .param("type", type).param("company", company)
        ).andExpect(status().isBadRequest)
        verify(repository, never()).save(any(Contract::class.java))
    }

    @Test
    fun `contract page renders delete as POST form with CSRF token`() {
        given(repository.findById("c1")).willReturn(Optional.of(testContract()))
        mockMvc.perform(get("/contracts/c1").with(user(testUser)))
            .andExpect(status().isOk)
            .andExpect(content().string(postFormWithCsrf("/contracts/c1/delete")))
    }

    @Test
    fun `contracts page explains how cancel dates are calculated`() {
        given(repository.findByUserId(testUserId)).willReturn(listOf(testContract()))
        mockMvc.perform(get("/contracts").with(user(testUser)))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Cancel dates leave the full notice period before the end date")))
        mockMvc.perform(get("/contracts").param("locale", "de").with(user(testUser)))
            .andExpect(content().string(containsString("volle Kündigungsfrist vor dem Enddatum")))
    }

    @Test
    fun `contracts page renders toggles as POST forms with CSRF token`() {
        given(repository.findByUserId(testUserId)).willReturn(listOf(testContract()))
        val result = mockMvc.perform(get("/contracts").with(user(testUser)))
            .andExpect(status().isOk)
        listOf("toggleBonus", "toggleCancel", "toggleCancelAck").forEach {
            result.andExpect(content().string(postFormWithCsrf("/contracts/c1/$it")))
        }
    }

    @Test
    fun `contract page shows stored periods in their unit, whole weeks as weeks`() {
        val cancellation = Cancellation(
            minDuration = Period.ofYears(2), noticePeriod = Period.ofWeeks(3), extendPeriod = Period.ofMonths(1)
        )
        given(repository.findById("c1"))
            .willReturn(Optional.of(Contract(testUserId, "type", "company", cancellation = cancellation).withId("c1")))
        mockMvc.perform(get("/contracts/c1").with(user(testUser)))
            .andExpect(status().isOk)
            .andExpect(content().string(periodField("minDuration", "2", "Y")))
            .andExpect(content().string(periodField("noticePeriod", "3", "W")))
            .andExpect(content().string(periodField("extendPeriod", "1", "M")))
    }

    @Test
    fun `contract page shows days which are not whole weeks as days`() {
        val cancellation = Cancellation(noticePeriod = Period.ofDays(10))
        given(repository.findById("c1"))
            .willReturn(Optional.of(Contract(testUserId, "type", "company", cancellation = cancellation).withId("c1")))
        mockMvc.perform(get("/contracts/c1").with(user(testUser)))
            .andExpect(content().string(periodField("noticePeriod", "10", "D")))
    }

    @Test
    fun `add page renders empty period fields`() {
        mockMvc.perform(get("/contracts/add").with(user(testUser)))
            .andExpect(status().isOk)
            .andExpect(content().string(matchesPattern("(?s).*<input[^>]*id=\"noticePeriod\"[^>]*value=\"\".*")))
            .andExpect(content().string(not(containsString("selected=\"selected\""))))
    }

    /**
     * @return a matcher for a page containing the period input [id] with the given value and selected unit
     */
    private fun periodField(id: String, value: String, unit: String) = matchesPattern(
        "(?s).*<input[^>]*id=\"$id\"[^>]*value=\"$value\".*" +
                "<select[^>]*id=\"${id}Unit\"[^>]*>(?:(?!</select>).)*<option value=\"$unit\" selected=\"selected\".*"
    )

    /**
     * @return a matcher for a page containing a POST form with the given action, which has a CSRF token as
     * first child element
     */
    private fun postFormWithCsrf(action: String) = matchesPattern(
        "(?s).*<form[^>]*method=\"post\"[^>]*action=\"$action\"[^>]*>\\s*<input type=\"hidden\" name=\"_csrf\" value=\"[^\"]+\"\\s*/?>.*"
    )
}
