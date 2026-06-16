package com.bhspl.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SessionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        
        // Exclude login, logout, static resources, and API sync endpoints
        if (path.equals("/login") || path.equals("/logout") || path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/img/") || path.startsWith("/api/sync/")) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            // For AJAX requests, return 401 instead of redirecting to login page HTML
            if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            } else {
                response.sendRedirect("/login?timeout=true");
            }
            return false;
        }

        // Add Cache-Control headers to prevent browser back-button access after logout
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        // Fetch active tab tokens for this HTTP session
        @SuppressWarnings("unchecked")
        java.util.Set<String> activeTabTokens = (java.util.Set<String>) session.getAttribute("activeTabTokens");
        if (activeTabTokens == null) {
            activeTabTokens = new java.util.HashSet<>();
            session.setAttribute("activeTabTokens", activeTabTokens);
        }

        String loginToken = request.getParameter("loginToken");
        String tabToken = request.getParameter("tabToken");
        if (tabToken == null) {
            tabToken = request.getHeader("X-Tab-Token");
        }

        // Validate loginToken redirect
        if (loginToken != null && loginToken.equals(session.getAttribute("loginToken"))) {
            activeTabTokens.add(loginToken);
            return true;
        }

        // Validate tabToken
        if (tabToken != null && activeTabTokens.contains(tabToken)) {
            return true;
        }

        // Token is missing or invalid.
        // For AJAX/SPA API requests, return 401 Unauthorized
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With")) || 
            (request.getHeader("Accept") != null && !request.getHeader("Accept").contains("text/html"))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // For main page requests, return bootstrapping script to check sessionStorage
        response.setContentType("text/html;charset=UTF-8");
        try (java.io.PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("<head>");
            out.println("<script>");
            out.println("  const token = sessionStorage.getItem('tabToken');");
            out.println("  if (token) {");
            out.println("    const url = new URL(window.location.href);");
            out.println("    url.searchParams.set('tabToken', token);");
            out.println("    window.location.replace(url.toString());");
            out.println("  } else {");
            out.println("    window.location.replace('/login?reason=missing');");
            out.println("  }");
            out.println("</script>");
            out.println("</head>");
            out.println("<body></body>");
            out.println("</html>");
            out.flush();
        }
        return false;
    }
}
