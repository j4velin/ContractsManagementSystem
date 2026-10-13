package de.j4velin.contracts.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
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
) : WebSecurityConfigurerAdapter() {

    private val logger = LoggerFactory.getLogger(WebSecurityConfig::class.java)

    override fun configure(http: HttpSecurity) {
        http.authorizeRequests()
            .antMatchers("/").permitAll()
            .antMatchers("/register").permitAll()
            .antMatchers("/login").permitAll()
            .antMatchers("/about").permitAll()
            .anyRequest().authenticated()
            .and()
            .formLogin()
            .loginPage("/login")
            .permitAll()
            .and()
            .rememberMe()
            .key(rememberMeKey())
            .userDetailsService(userDetailsService)
            .and()
            .logout()
            .deleteCookies("JSESSIONID")
            .permitAll()
    }

    override fun configure(web: WebSecurity) {
        web.ignoring()
            .antMatchers("/webjars/**", "/favicon.svg", "/images/*")
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    private fun rememberMeKey(): String = rememberMeKey.ifBlank {
        logger.warn("contracts.remember-me.key is not set: using a random key, remembered logins won't survive a restart")
        UUID.randomUUID().toString()
    }
}
