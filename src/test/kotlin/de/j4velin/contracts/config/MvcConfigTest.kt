package de.j4velin.contracts.config

import de.j4velin.contracts.data.ContractsRepository
import de.j4velin.contracts.testsupport.ContractsWebMvcTest
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@ContractsWebMvcTest
// required by ContractsController, which is loaded as this test covers all controllers
@MockitoBean(types = [ContractsRepository::class])
class MvcConfigTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val englishPromo = "A simple contracts management system"
    private val germanPromo = "Ein einfaches Vertragsverwaltungs-Tool"

    @Test
    fun `malformed locale parameter is ignored`() {
        mockMvc.perform(get("/").param("locale", "!!"))
            .andExpect(status().isOk)
            .andExpect(view().name("index"))
            .andExpect(content().string(containsString(englishPromo)))
    }

    @Test
    fun `locale parameter switches the language`() {
        mockMvc.perform(get("/").param("locale", "de"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString(germanPromo)))
    }

    @Test
    fun `malformed locale parameter keeps the previously chosen language`() {
        val session = MockHttpSession()
        mockMvc.perform(get("/").param("locale", "de").session(session))
            .andExpect(status().isOk)
        mockMvc.perform(get("/").param("locale", "!!").session(session))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString(germanPromo)))
    }
}
