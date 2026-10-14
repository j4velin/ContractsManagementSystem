package de.j4velin.contracts.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.util.unit.DataSize
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Rejects uploads, which exceed `spring.servlet.multipart.max-request-size` (see defaults.properties), with a redirect
 * to the import/export page, which then shows the limit. The only upload is the contracts import.
 *
 * Needed, because Tomcat otherwise rejects such an upload while Spring Security's CSRF filter reads the form
 * parameters: that fails the request before it reaches any controller or exception handler, and the client never
 * gets a response. This filter runs before Spring Security and only checks the declared size, without reading the
 * body. Tomcat then reads and discards the body (up to `server.tomcat.max-swallow-size`, see defaults.properties), so
 * that the browser gets the redirect.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class UploadSizeFilter(
    @Value("\${spring.servlet.multipart.max-request-size}") private val maxRequestSize: DataSize
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest) =
        request.contentType?.startsWith("multipart/", ignoreCase = true) != true

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (request.contentLengthLong > maxRequestSize.toBytes()) {
            response.sendRedirect(request.contextPath + TOO_LARGE_REDIRECT)
            return
        }
        chain.doFilter(request, response)
    }

    internal companion object {
        /**
         * The import/export page, which shows the "too large" error for this parameter
         */
        const val TOO_LARGE_REDIRECT = "/import-export?tooLarge"
    }
}
