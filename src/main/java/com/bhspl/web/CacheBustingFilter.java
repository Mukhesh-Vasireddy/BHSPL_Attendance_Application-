package com.bhspl.web;

import com.bhspl.util.CacheManager;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.stereotype.Component;

/**
 * Filter that automatically invalidates/busts the cache on any state-changing HTTP POST request.
 */
@Component
public class CacheBustingFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            String method = ((HttpServletRequest) request).getMethod();
            if ("POST".equalsIgnoreCase(method)) {
                // Bust cache on any POST modifications
                CacheManager.getInstance().clear();
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {}
}
