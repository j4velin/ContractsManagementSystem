package de.j4velin.contracts.controller

import de.j4velin.contracts.config.assertSharedScriptWithoutInlineJs
import de.j4velin.contracts.data.Bonus
import de.j4velin.contracts.data.Cancellation
import de.j4velin.contracts.data.Contract
import de.j4velin.contracts.data.ContractsRepository
import de.j4velin.contracts.testsupport.ContractsWebMvcTest
import de.j4velin.contracts.user.data.User
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.matchesPattern
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.convention.TestBean
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.util.unit.DataSize
import java.time.Clock
import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset
import java.util.Optional

@ContractsWebMvcTest(controllers = [ContractsController::class])
class ContractsControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var repository: ContractsRepository

    // replaces the system clock with the fixed one of the clock() factory method below
    @TestBean
    private lateinit var clock: Clock

    companion object {
        private val today = LocalDate.of(2026, 10, 13)

        @JvmStatic
        fun clock(): Clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
    }

    private val testUserId = "user1"
    private val testUser = User(id = testUserId, username = "alice", password = "secret", mail = "alice@example.com")

    private fun testContract() =
        Contract(testUserId, "type", "company", bonus = Bonus(description = "bonus"), id = "c1")

    private fun otherUsersContract() = Contract("user2", "type", "company", id = "c1")

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
    fun `anonymous users are redirected to the login page`() {
        mockMvc.perform(get("/contracts"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/login"))
    }

    @Test
    fun `anonymous users can open the public home page`() {
        // guards the shared test setup: only WebSecurityConfig makes "/" public. Spring Boot's default security
        // (if the setup didn't import it) would redirect to its login page here, just like for "/contracts"
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
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
        val field = if (type.isBlank()) "type" else "company"
        mockMvc.perform(
            post("/contracts").with(user(testUser)).with(csrf())
                .param("type", type).param("company", company)
        ).andExpect(status().isBadRequest)
            .andExpect(view().name("contract"))
            .andExpect(model().attributeHasFieldErrorCode("contractForm", field, "contract.error.required"))
            .andExpect(content().string(fieldWithError(field, Regex.escape(if (field == "type") type else company), "Please fill in this field")))
        verify(repository, never()).save(any(Contract::class.java))
    }

    @Test
    fun `adding a contract with invalid input shows the errors at their fields and keeps the entered values`() {
        mockMvc.perform(
            post("/contracts").with(user(testUser)).with(csrf())
                .param("type", "Rent").param("company", " ").param("notes", "my notes")
                .param("startDate", "2020-01-15")
                .param("minDurationValue", "2").param("minDurationUnit", "Y")
                .param("noticePeriodValue", "1.5").param("noticePeriodUnit", "M")
                .param("endDate", "2021-02-30")
                .param("bonusDescription", "cashback").param("bonusReceived", "true")
        ).andExpect(status().isBadRequest)
            .andExpect(view().name("contract"))
            .andExpect(model().attributeErrorCount("contractForm", 3))
            .andExpect(model().attributeHasFieldErrorCode("contractForm", "company", "contract.error.required"))
            .andExpect(model().attributeHasFieldErrorCode("contractForm", "noticePeriodValue", "contract.error.periodAmount"))
            .andExpect(model().attributeHasFieldErrorCode("contractForm", "endDate", "contract.error.date"))
            .andExpect(content().string(fieldWithError("company", " ", "Please fill in this field")))
            .andExpect(content().string(fieldWithError("noticePeriod", "1\\.5", "Please enter a whole number between 0 and 999")))
            .andExpect(content().string(fieldWithError("endDate", "2021-02-30", "This is not a valid date")))
            // the valid fields are kept as entered
            .andExpect(content().string(matchesPattern("(?s).*<input(?=[^>]*id=\"type\")(?![^>]*is-invalid)[^>]*value=\"Rent\".*")))
            .andExpect(content().string(matchesPattern("(?s).*<textarea[^>]*id=\"notes\"[^>]*>my notes</textarea>.*")))
            .andExpect(content().string(matchesPattern("(?s).*<input[^>]*id=\"startDate\"[^>]*value=\"2020-01-15\".*")))
            .andExpect(content().string(periodField("minDuration", "2", "Y")))
            .andExpect(content().string(periodField("noticePeriod", "1\\.5", "M")))
            .andExpect(content().string(matchesPattern("(?s).*<textarea[^>]*id=\"bonusDescription\"[^>]*>cashback</textarea>.*")))
            .andExpect(content().string(matchesPattern("(?s).*<input[^>]*id=\"bonusReceived\"[^>]*checked=\"checked\".*")))
            // still the add form: posts to the add URL and has no delete button
            .andExpect(content().string(matchesPattern("(?s).*<form[^>]*action=\"/contracts\".*")))
            .andExpect(content().string(not(containsString("deleteForm"))))
        verify(repository, never()).save(any(Contract::class.java))
    }

    @Test
    fun `invalid input shows the errors in German with locale de`() {
        mockMvc.perform(
            post("/contracts?locale=de").with(user(testUser)).with(csrf())
                .param("type", "").param("company", "ACME")
                .param("extendPeriodValue", "1000").param("extendPeriodUnit", "M")
                .param("startDate", "0999-12-31")
        ).andExpect(status().isBadRequest)
            .andExpect(content().string(fieldWithError("type", "", "Bitte fülle dieses Feld aus")))
            .andExpect(content().string(fieldWithError("extendPeriod", "1000", "Bitte gib eine ganze Zahl zwischen 0 und 999 ein")))
            .andExpect(content().string(fieldWithError("startDate", "0999-12-31", "Bitte gib ein Datum mit vierstelliger Jahreszahl ein")))
        verify(repository, never()).save(any(Contract::class.java))
    }

    @Test
    fun `an unknown period unit is shown at the unit field`() {
        mockMvc.perform(
            post("/contracts").with(user(testUser)).with(csrf())
                .param("type", "type").param("company", "company")
                .param("minDurationValue", "1").param("minDurationUnit", "X")
        ).andExpect(status().isBadRequest)
            .andExpect(model().attributeHasFieldErrorCode("contractForm", "minDurationUnit", "contract.error.periodUnit"))
            .andExpect(content().string(matchesPattern(
                "(?s).*<select(?=[^>]*id=\"minDurationUnit\")[^>]*is-invalid[^>]*>(?:(?!</div>).)*" +
                        "<div class=\"invalid-feedback\">Please choose one of the listed units</div>.*"
            )))
        verify(repository, never()).save(any(Contract::class.java))
    }

    @Test
    fun `adding a contract saves all submitted values`() {
        mockMvc.perform(
            post("/contracts").with(user(testUser)).with(csrf())
                .param("type", "Internet").param("company", "ACME").param("notes", "notes")
                .param("startDate", "2020-01-15")
                .param("minDurationValue", "2").param("minDurationUnit", "Y")
                .param("noticePeriodValue", "3").param("noticePeriodUnit", "W")
                .param("extendPeriodValue", "").param("extendPeriodUnit", "")
                // a checked box counts whatever its value, the form used to send an empty one
                .param("canceled", "").param("_canceled", "on")
                .param("cancelAck", "true").param("_cancelAck", "on")
                .param("endDate", "2022-01-14")
                .param("bonusDescription", "cashback").param("_bonusReceived", "on")
        ).andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/contracts"))
        val captor = ArgumentCaptor.forClass(Contract::class.java)
        verify(repository).save(captor.capture())
        assertEquals(
            Contract(
                testUserId, "Internet", "ACME", notes = "notes", startDate = LocalDate.of(2020, 1, 15),
                cancellation = Cancellation(
                    minDuration = Period.ofYears(2), noticePeriod = Period.ofWeeks(3), extendPeriod = null,
                    endDate = LocalDate.of(2022, 1, 14), canceled = true, ack = true
                ),
                bonus = Bonus("cashback", received = false)
            ),
            captor.value
        )
    }

    @Test
    fun `updating a contract with invalid input shows the edit form again`() {
        given(repository.findById("c1")).willReturn(Optional.of(testContract()))
        mockMvc.perform(
            post("/contracts/c1").with(user(testUser)).with(csrf())
                .param("type", "Rent").param("company", "ACME")
                .param("noticePeriodValue", "-1").param("noticePeriodUnit", "M")
        ).andExpect(status().isBadRequest)
            .andExpect(view().name("contract"))
            .andExpect(content().string(fieldWithError("noticePeriod", "-1", "Please enter a whole number between 0 and 999")))
            .andExpect(content().string(matchesPattern("(?s).*<form[^>]*action=\"/contracts/c1\".*")))
            .andExpect(content().string(containsString("Edit Contract")))
            .andExpect(content().string(postFormWithCsrf("/contracts/c1/delete")))
        verify(repository, never()).save(any(Contract::class.java))
    }

    @Test
    fun `contract of another user is not found even with invalid input`() {
        given(repository.findById("c1")).willReturn(Optional.of(otherUsersContract()))
        mockMvc.perform(
            post("/contracts/c1").with(user(testUser)).with(csrf())
                .param("type", "").param("company", "")
        ).andExpect(status().isNotFound)
        verify(repository, never()).save(any(Contract::class.java))
    }

    @Test
    fun `contract page prefills the form with the stored contract`() {
        val contract = Contract(
            testUserId, "Rent", "ACME", notes = "some notes", startDate = LocalDate.of(2020, 1, 15),
            cancellation = Cancellation(endDate = LocalDate.of(2027, 1, 14), canceled = true, ack = true),
            bonus = Bonus("cashback", received = true), id = "c1"
        )
        given(repository.findById("c1")).willReturn(Optional.of(contract))
        mockMvc.perform(get("/contracts/c1").with(user(testUser)))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Edit Contract")))
            .andExpect(content().string(matchesPattern("(?s).*<form[^>]*action=\"/contracts/c1\".*")))
            .andExpect(content().string(matchesPattern("(?s).*<input[^>]*id=\"type\"[^>]*value=\"Rent\".*")))
            .andExpect(content().string(matchesPattern("(?s).*<input[^>]*id=\"company\"[^>]*value=\"ACME\".*")))
            .andExpect(content().string(matchesPattern("(?s).*<textarea[^>]*id=\"notes\"[^>]*>some notes</textarea>.*")))
            .andExpect(content().string(matchesPattern("(?s).*<input[^>]*id=\"startDate\"[^>]*value=\"2020-01-15\".*")))
            .andExpect(content().string(matchesPattern("(?s).*<input[^>]*id=\"endDate\"[^>]*value=\"2027-01-14\".*")))
            .andExpect(content().string(matchesPattern("(?s).*<input[^>]*id=\"canceled\"[^>]*checked=\"checked\".*")))
            .andExpect(content().string(matchesPattern("(?s).*<input[^>]*id=\"cancelAck\"[^>]*checked=\"checked\".*")))
            .andExpect(content().string(matchesPattern("(?s).*<textarea[^>]*id=\"bonusDescription\"[^>]*>cashback</textarea>.*")))
            .andExpect(content().string(matchesPattern("(?s).*<input[^>]*id=\"bonusReceived\"[^>]*checked=\"checked\".*")))
            .andExpect(content().string(not(containsString("is-invalid"))))
    }

    @Test
    fun `add page renders an empty form without delete button`() {
        mockMvc.perform(get("/contracts/add").with(user(testUser)))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Add Contract")))
            .andExpect(content().string(matchesPattern("(?s).*<form[^>]*action=\"/contracts\".*")))
            .andExpect(content().string(matchesPattern("(?s).*<input[^>]*id=\"company\"[^>]*value=\"\".*")))
            .andExpect(content().string(not(containsString("checked=\"checked\""))))
            .andExpect(content().string(not(containsString("deleteForm"))))
            .andExpect(content().string(not(containsString("is-invalid"))))
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
    @ValueSource(strings = ["", "/toggleCancel", "/toggleCancelAck", "/toggleBonus"])
    fun `changing a contract saves it under its id, so that it is updated instead of inserted again`(action: String) {
        given(repository.findById("c1")).willReturn(Optional.of(testContract()))
        mockMvc.perform(
            post("/contracts/c1$action").with(user(testUser)).with(csrf())
                .param("type", "type").param("company", "company")
        ).andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/contracts"))
        val captor = ArgumentCaptor.forClass(Contract::class.java)
        verify(repository).save(captor.capture())
        assertEquals("c1", captor.value.id)
        assertEquals(testUserId, captor.value.userId)
    }

    @Test
    fun `toggling cancel uses the injected clock for the end date`() {
        val contract = Contract(
            testUserId, "type", "company", cancellation = Cancellation(noticePeriod = Period.ofMonths(1)), id = "c1"
        )
        given(repository.findById("c1")).willReturn(Optional.of(contract))
        mockMvc.perform(post("/contracts/c1/toggleCancel").with(user(testUser)).with(csrf()))
            .andExpect(status().is3xxRedirection)
        val captor = ArgumentCaptor.forClass(Contract::class.java)
        verify(repository).save(captor.capture())
        assertTrue(captor.value.cancellation.canceled)
        assertEquals(LocalDate.of(2026, 11, 13), captor.value.cancellation.endDate)
    }

    @Test
    fun `contracts page uses the injected clock`() {
        val ended = Contract(
            testUserId, "type", "ended", cancellation = Cancellation(endDate = today), id = "c1"
        )
        val running = Contract(
            testUserId, "type", "running", cancellation = Cancellation(noticePeriod = Period.ofDays(3)), id = "c2"
        )
        given(repository.findByUserId(testUserId)).willReturn(listOf(ended, running))
        mockMvc.perform(get("/contracts").with(user(testUser)))
            .andExpect(status().isOk)
            // ends today -> no longer active
            .andExpect(content().string(matchesPattern("(?s).*<tr class=\"inactive\" style=\"display: none;\">\\s*<td>type</td>\\s*<td>ended</td>.*")))
            // the running contract can be canceled today, which is shown as its date
            .andExpect(content().string(matchesPattern("(?s).*<tr>\\s*<td>type</td>\\s*<td>running</td>.*Oct 13, 2026.*")))
    }

    @ParameterizedTest
    @CsvSource("'', company", "'   ', company", "type, ''", "type, '  '")
    fun `updating a contract with blank type or company is rejected`(type: String, company: String) {
        given(repository.findById("c1")).willReturn(Optional.of(testContract()))
        mockMvc.perform(
            post("/contracts/c1").with(user(testUser)).with(csrf())
                .param("type", type).param("company", company)
        ).andExpect(status().isBadRequest)
            .andExpect(view().name("contract"))
            .andExpect(model().attributeHasFieldErrorCode("contractForm", if (type.isBlank()) "type" else "company", "contract.error.required"))
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

    @ParameterizedTest
    @ValueSource(strings = ["/contracts", "/contracts/c1", "/contracts/add"])
    fun `contract pages load the shared script and have no inline scripts or handlers`(page: String) {
        given(repository.findByUserId(testUserId)).willReturn(listOf(testContract()))
        given(repository.findById("c1")).willReturn(Optional.of(testContract()))
        val html = mockMvc.perform(get(page).with(user(testUser)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertSharedScriptWithoutInlineJs(html)
    }

    @Test
    fun `logout is a submit button in a POST form with CSRF token`() {
        given(repository.findByUserId(testUserId)).willReturn(listOf(testContract()))
        mockMvc.perform(get("/contracts").with(user(testUser)))
            .andExpect(status().isOk)
            .andExpect(content().string(matchesPattern(
                "(?s).*<form[^>]*action=\"/logout\"[^>]*method=\"post\"[^>]*>\\s*" +
                        "<input type=\"hidden\" name=\"_csrf\" value=\"[^\"]+\"\\s*/?>\\s*" +
                        "<button type=\"submit\"[^>]*>Logout</button>\\s*</form>.*"
            )))
    }

    @Test
    fun `delete button carries the localized confirmation text for app js`() {
        given(repository.findById("c1")).willReturn(Optional.of(testContract()))
        mockMvc.perform(get("/contracts/c1").with(user(testUser)))
            .andExpect(content().string(matchesPattern(
                "(?s).*<button[^>]*form=\"deleteForm\"[^>]*data-confirm=\"Do you really want to delete this contract\\?\".*"
            )))
        mockMvc.perform(get("/contracts/c1").param("locale", "de").with(user(testUser)))
            .andExpect(content().string(containsString("data-confirm=\"Soll dieser Vertrag wirklich gelöscht werden?\"")))
    }

    @Test
    fun `contract page shows stored periods in their unit, whole weeks as weeks`() {
        val cancellation = Cancellation(
            minDuration = Period.ofYears(2), noticePeriod = Period.ofWeeks(3), extendPeriod = Period.ofMonths(1)
        )
        given(repository.findById("c1"))
            .willReturn(Optional.of(Contract(testUserId, "type", "company", cancellation = cancellation, id = "c1")))
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
            .willReturn(Optional.of(Contract(testUserId, "type", "company", cancellation = cancellation, id = "c1")))
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

    @Test
    fun `export downloads the user's contracts as CSV`() {
        given(repository.findByUserId(testUserId)).willReturn(listOf(testContract()))
        mockMvc.perform(get("/contracts/export").with(user(testUser)))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"contracts-2026-10-13.csv\""))
            .andExpect(content().bytes(ContractsCsv.write(listOf(testContract())).toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `import adds the contracts of the file to the user's contracts`() {
        mockMvc.perform(importRequest("type,company\r\nRent,ACME\r\nGym,Muscles\r\n"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/contracts"))
            .andExpect(flash().attribute("importedCount", 2))
        verify(repository).saveAll(listOf(Contract(testUserId, "Rent", "ACME"), Contract(testUserId, "Gym", "Muscles")))
        verify(repository, never()).deleteAll(any())
    }

    @Test
    fun `import with replace deletes the user's existing contracts first`() {
        given(repository.findByUserId(testUserId)).willReturn(listOf(testContract()))
        mockMvc.perform(importRequest("type,company\r\nRent,ACME\r\n").param("replace", "true"))
            .andExpect(flash().attribute("importedCount", 1))
        val order = inOrder(repository)
        order.verify(repository).deleteAll(listOf(testContract()))
        order.verify(repository).saveAll(listOf(Contract(testUserId, "Rent", "ACME")))
    }

    @Test
    fun `import errors show row numbers without grouping, but the contract limit with it`() {
        val csv = "type,company\r\n" + "Rent,ACME\r\n".repeat(ContractsCsv.MAX_CONTRACTS + 1)
        mockMvc.perform(importRequest(csv))
            .andExpect(flash().attribute("importErrors", listOf("Row 10002: The file has more than 10,000 contracts, please split it")))
        verifyNoInteractions(repository)
    }

    @Test
    fun `invalid import changes nothing and shows the localized errors`() {
        given(repository.findByUserId(testUserId)).willReturn(listOf(testContract()))
        val errors = mockMvc.perform(importRequest("type,company\r\nRent,\r\n").param("replace", "true").param("locale", "de"))
            .andExpect(redirectedUrl("/import-export"))
            .andExpect(flash().attribute("importErrors", listOf("Zeile 2, Spalte company: Bitte fülle dieses Feld aus")))
            .andReturn().flashMap["importErrors"]
        verify(repository, never()).deleteAll(any())
        verify(repository, never()).saveAll(any<Iterable<Contract>>())

        mockMvc.perform(get("/import-export").with(user(testUser)).flashAttr("importErrors", errors!!))
            // the errors are flashed already localized, in the language of the import request
            .andExpect(content().string(containsString("<li>Zeile 2, Spalte company: Bitte fülle dieses Feld aus</li>")))
    }

    @Test
    fun `too large import is redirected to the import-export page, which shows the size limit`() {
        // the filter only checks the declared size, so the body doesn't need to be a valid multipart request
        mockMvc.perform(
            importRequest("type,company\r\nRent,ACME\r\n").with { it.apply { setContent(ByteArray(2112 * 1024 + 1)) } }
        ).andExpect(redirectedUrl("/import-export?tooLarge"))
        verifyNoInteractions(repository)

        mockMvc.perform(get("/import-export").param("tooLarge", "").with(user(testUser)))
            .andExpect(content().string(matchesPattern(
                "(?s).*<div class=\"alert alert-danger\" role=\"alert\">The file is too large, it may have at most 2 MB</div>.*"
            )))
    }

    @Test
    fun `import of a 2 MB file is accepted, including the rest of the upload request`() {
        // the CSRF token, the form fields and the multipart boundaries take less than 1 KB
        mockMvc.perform(
            importRequest("type,company\r\nRent,ACME\r\n").with { it.apply { setContent(ByteArray(2 * 1024 * 1024 + 1024)) } }
        ).andExpect(redirectedUrl("/contracts"))
        verify(repository).saveAll(listOf(Contract(testUserId, "Rent", "ACME")))
    }

    @Test
    fun `import-export page shows no size error without the parameter`() {
        mockMvc.perform(get("/import-export").with(user(testUser)))
            .andExpect(content().string(not(containsString("too large"))))
    }

    @Test
    fun `uploads may have 2 MB plus headroom, the same for the file and the whole request`(@Autowired environment: Environment) {
        // only UploadSizeFilter may reject an upload: Tomcat's own file size check fails without any response
        assertEquals(DataSize.ofKilobytes(2112), environment.getProperty("spring.servlet.multipart.max-file-size", DataSize::class.java))
        assertEquals(DataSize.ofKilobytes(2112), environment.getProperty("spring.servlet.multipart.max-request-size", DataSize::class.java))
    }

    @Test
    fun `import requires a CSRF token`() {
        mockMvc.perform(multipart("/contracts/import").file(csvFile("type,company\r\nRent,ACME\r\n")).with(user(testUser)))
            .andExpect(status().isForbidden)
        verifyNoInteractions(repository)
    }

    @Test
    fun `import-export page has an export link and an import form with CSRF token`() {
        mockMvc.perform(get("/import-export").with(user(testUser)))
            .andExpect(status().isOk)
            .andExpect(content().string(matchesPattern(
                "(?s).*<a class=\"nav-link active\" href=\"/import-export\">Backup</a>.*"
            )))
            .andExpect(content().string(containsString("href=\"/contracts/export\"")))
            .andExpect(content().string(matchesPattern(
                "(?s).*<form[^>]*method=\"post\"[^>]*enctype=\"multipart/form-data\"[^>]*action=\"/contracts/import\"[^>]*>" +
                        "\\s*<input type=\"hidden\" name=\"_csrf\" value=\"[^\"]+\"\\s*/?>.*"
            )))
    }

    @Test
    fun `import-export menu item is only shown to logged in users`() {
        mockMvc.perform(get("/contracts").with(user(testUser)))
            .andExpect(content().string(containsString("href=\"/import-export\"")))
        mockMvc.perform(get("/"))
            .andExpect(content().string(not(containsString("href=\"/import-export\""))))
        mockMvc.perform(get("/import-export"))
            .andExpect(redirectedUrl("/login"))
    }

    private fun csvFile(csv: String) = MockMultipartFile("file", "contracts.csv", "text/csv", csv.toByteArray())

    private fun importRequest(csv: String) =
        multipart("/contracts/import").file(csvFile(csv)).with(user(testUser)).with(csrf())

    /**
     * @return a matcher for a page containing the period input [id] with the given value and selected unit
     */
    private fun periodField(id: String, value: String, unit: String) = matchesPattern(
        "(?s).*<input[^>]*id=\"$id\"[^>]*value=\"$value\".*" +
                "<select[^>]*id=\"${id}Unit\"[^>]*>(?:(?!</select>).)*<option value=\"$unit\" selected=\"selected\".*"
    )

    /**
     * @param id the id of the input (for a period: the period's name, its value input has that id)
     * @param value a regex for the value the input shows
     * @param message the error text, which the page has to show right after the input (for a period: in the same
     * input group)
     * @return a matcher for a page containing the input marked as invalid, followed by the given error text
     */
    private fun fieldWithError(id: String, value: String, message: String) = matchesPattern(
        "(?s).*<input(?=[^>]*id=\"$id\")(?=[^>]*value=\"$value\")[^>]*is-invalid[^>]*>(?:(?!</div>).)*" +
                "<div class=\"invalid-feedback\">${Regex.escape(message)}</div>.*"
    )

    /**
     * @return a matcher for a page containing a POST form with the given action, which has a CSRF token as
     * first child element
     */
    private fun postFormWithCsrf(action: String) = matchesPattern(
        "(?s).*<form[^>]*method=\"post\"[^>]*action=\"$action\"[^>]*>\\s*<input type=\"hidden\" name=\"_csrf\" value=\"[^\"]+\"\\s*/?>.*"
    )
}
