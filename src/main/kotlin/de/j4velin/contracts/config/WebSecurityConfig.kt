package de.j4velin.contracts.config

import jakarta.servlet.DispatcherType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import java.util.*

@Configuration
@EnableWebSecurity
class WebSecurityConfig(
    private val userDetailsService: UserDetailsService,
    /**
     * Secret used to sign remember-me cookies. Must stay the same across restarts, otherwise all remembered logins
     * become invalid on every restart
     */
    @Value("\${contracts.remember-me.key:}") private val rememberMeKey: String
) {

    private val logger = LoggerFactory.getLogger(WebSecurityConfig::class.java)

    /**
     * Static resources: public, without session and CSRF handling, and without Spring Security's default
     * "Cache-Control: no-store" header, so browsers can cache them
     */
    @Bean
    @Order(1)
    fun staticResourcesFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher("/webjars/**", "/favicon.svg", "/images/*")
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .csrf { it.disable() }
            .requestCache { it.disable() }
            .securityContext { it.disable() }
            .headers { it.cacheControl { cacheControl -> cacheControl.disable() } }
        return http.build()
    }

    @Bean
    @Order(2)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.authorizeHttpRequests {
            // forwards and error dispatches render the response of a request that was already authorized (or the
            // error page for one that wasn't)
            it.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                .requestMatchers("/", "/register", "/login", "/about").permitAll()
                .anyRequest().authenticated()
        }
            .formLogin { it.loginPage("/login").permitAll() }
            .rememberMe { it.key(rememberMeKey()).userDetailsService(userDetailsService) }
            .logout { it.deleteCookies("JSESSIONID").permitAll() }
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    private fun rememberMeKey(): String = rememberMeKey.ifBlank {
        logger.warn("contracts.remember-me.key is not set: using a random key, remembered logins won't survive a restart")
        UUID.randomUUID().toString()
    }
}
