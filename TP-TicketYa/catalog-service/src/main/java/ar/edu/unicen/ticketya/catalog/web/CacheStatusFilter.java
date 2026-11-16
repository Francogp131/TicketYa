package ar.edu.unicen.ticketya.catalog.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CacheStatusFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE = "ticketya.cacheStatus";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);
        Object status = request.getAttribute(ATTRIBUTE);
        if (status instanceof String value && !response.isCommitted()) {
            response.setHeader("X-Cache", value);
        }
    }

    public static void mark(HttpServletRequest request, String value) {
        if (request != null && value != null) {
            request.setAttribute(ATTRIBUTE, value);
        }
    }
}
