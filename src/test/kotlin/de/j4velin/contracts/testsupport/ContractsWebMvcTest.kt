package de.j4velin.contracts.testsupport

import de.j4velin.contracts.config.AppInfo
import de.j4velin.contracts.config.WebSecurityConfig
import de.j4velin.contracts.user.data.UsersRepository
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.AliasFor
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.reflect.KClass

/**
 * The common setup of all `@WebMvcTest`s of this application. Use it instead of a plain [WebMvcTest].
 *
 * It exists because a plain [WebMvcTest] doesn't pick up our `SecurityFilterChain` and then silently tests against
 * Spring Boot's default security instead. This annotation therefore imports the real [WebSecurityConfig] and [AppInfo]
 * (shown in the footer of every page) and mocks the beans that are always needed in the web layer:
 * - [UserDetailsService], required by [WebSecurityConfig] (remember-me)
 * - [UsersRepository], required by `UserLocaleResolver`
 *
 * A test that needs to stub or verify one of these mocks gets it with `@Autowired`. Beans that only some tests need,
 * like the repository of the controller under test or a fixed `Clock`, are declared in the test class itself.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@WebMvcTest
@Import(WebSecurityConfig::class, AppInfo::class)
@MockitoBean(types = [UserDetailsService::class, UsersRepository::class])
annotation class ContractsWebMvcTest(
    /**
     * The controllers to test, see [WebMvcTest.controllers]. Empty means all controllers.
     */
    @get:AliasFor(annotation = WebMvcTest::class, attribute = "controllers")
    val controllers: Array<KClass<*>> = [],
)
