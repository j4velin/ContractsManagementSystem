package de.j4velin.contracts.config

import de.j4velin.contracts.data.ContractsRepository
import de.j4velin.contracts.testsupport.ContractsWebMvcTest
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
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

    @ParameterizedTest
    @ValueSource(strings = ["/", "/login", "/register", "/about"])
    fun `pages load the shared script after Bootstrap and have no inline scripts or handlers`(page: String) {
        val html = mockMvc.perform(get(page))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertSharedScriptWithoutInlineJs(html)
    }

    @Test
    fun `shared script is served to anonymous users, cacheable and without a session`() {
        val result = mockMvc.perform(get("/js/app.js"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", containsString("javascript")))
            .andExpect(content().string(containsString("needs-validation")))
            .andReturn()
        assertFalse(result.response.getHeaders("Cache-Control").any { it.contains("no-store") },
            "static resources must be cacheable")
        assertNull(result.request.getSession(false), "static resources must not create a session")
    }
}

/**
 * Asserts that the rendered [html] loads /js/app.js after Bootstrap's bundle and contains no inline scripts, inline
 * event handlers or javascript: URLs, which a strict Content-Security-Policy would block
 */
internal fun assertSharedScriptWithoutInlineJs(html: String) {
    val bootstrap = html.indexOf("bootstrap.bundle.min.js")
    val app = html.indexOf("<script src=\"/js/app.js\"></script>")
    assertTrue(bootstrap >= 0 && app > bootstrap, "app.js must be loaded after Bootstrap's bundle")
    assertFalse(Regex("""<script(?![^>]*\ssrc=)[^>]*>""").containsMatchIn(html), "inline <script> without src")
    assertFalse(Regex("""\son[a-z]+\s*=""", RegexOption.IGNORE_CASE).containsMatchIn(html), "inline event handler")
    assertFalse(html.contains("javascript:", ignoreCase = true), "javascript: URL")
}
