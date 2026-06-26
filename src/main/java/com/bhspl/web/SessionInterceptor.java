package com.bhspl.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.bhspl.db.DatabaseManager;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.Map;

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
            handleUnauthorized(request, response, "timeout=true");
            return false;
        }

        // Validate that the session's password hash matches the latest DB hash
        // This ensures old sessions are invalidated immediately if the password is changed
        String username = (String) session.getAttribute("user");
        String sessionHash = (String) session.getAttribute("passwordHash");
        if (username != null && sessionHash != null) {
            try {
                DatabaseManager db = DatabaseManager.getInstance();
                Map<String, Object> user = db.queryOne("SELECT password_hash FROM users WHERE username=?", username);
                if (user == null || !sessionHash.equals(user.get("password_hash"))) {
                    session.invalidate();
                    handleUnauthorized(request, response, "reason=password_changed");
                    return false;
                }
            } catch (Exception e) {
                // If DB fails, we cannot verify session safely. Deny access.
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database unavailable");
                return false;
            }
        } else {
            // Missing password hash in session (legacy session), force re-login
            session.invalidate();
            handleUnauthorized(request, response, "timeout=true");
            return false;
        }

        // Extract logical path relative to context path
        String checkPath = path;
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && checkPath.startsWith(contextPath)) {
            checkPath = checkPath.substring(contextPath.length());
        }

        // Role authorization check
        String sessionRole = (String) session.getAttribute("role");
        if (sessionRole == null || !"Admin".equalsIgnoreCase(sessionRole)) {
            // 1. Block restricted URLs (both GET and POST, and any other HTTP method)
            if (isRestrictedPath(checkPath)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied");
                return false;
            }

            // 2. Check permitted modules
            String requiredModule = getRequiredModule(checkPath);
            if (requiredModule != null) {
                String allowedModulesStr = (String) session.getAttribute("allowed_modules");
                boolean hasAccess = false;
                if (allowedModulesStr != null) {
                    String[] modules = allowedModulesStr.split(",");
                    for (String m : modules) {
                        if (m.trim().equalsIgnoreCase(requiredModule)) {
                            hasAccess = true;
                            break;
                        }
                    }
                }
                if (!hasAccess) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied");
                    return false;
                }
            }
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

    private boolean isRestrictedPath(String path) {
        String p = path;
        if (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        
        // Restricted URLs like /devices, /system/users, /leave/policy, /masters/*, /system/settings must return 403 for Operator.
        if (p.equals("/devices") || p.startsWith("/devices/") || p.startsWith("/api/devices/")) {
            return true;
        }
        if (p.equals("/system/users") || p.startsWith("/system/users/")) {
            return true;
        }
        if (p.equals("/leave/policy") || p.startsWith("/leave/policy/")) {
            return true;
        }
        if (p.equals("/masters") || p.startsWith("/masters/")) {
            return true;
        }
        if (p.equals("/system/settings") || p.startsWith("/system/settings/")) {
            return true;
        }
        
        // Additional settings submenus only for Admin
        if (p.equals("/system/backup") || p.startsWith("/system/backup/")) {
            return true;
        }
        if (p.equals("/system/activity-logs") || p.startsWith("/system/activity-logs/")) {
            return true;
        }
        if (p.equals("/system/about") || p.startsWith("/system/about/")) {
            return true;
        }
        if (p.equals("/system/debug") || p.startsWith("/system/debug/")) {
            return true;
        }
        if (p.equals("/system/process-logs") || p.startsWith("/system/process-logs/")) {
            return true;
        }
        if (p.equals("/system/sync") || p.startsWith("/system/sync/")) {
            return true;
        }
        if (p.equals("/system")) {
            return true;
        }
        
        return false;
    }

    private String getRequiredModule(String path) {
        String p = path;
        if (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        if (p.equals("/dashboard") || p.isEmpty()) {
            return "Dashboard";
        }
        if (p.equals("/employees") || p.startsWith("/employees/") || p.startsWith("/api/employees/")) {
            return "Employee Directory";
        }
        if (p.equals("/attendance") || p.startsWith("/attendance/")) {
            return "Attendance Management";
        }
        if (p.equals("/reports") || p.startsWith("/reports/") || p.startsWith("/api/reports/")) {
            return "Attendance Reports";
        }
        if (p.equals("/raw-logs") || p.startsWith("/raw-logs/") || p.startsWith("/api/raw-logs/")) {
            return "Activity Logs";
        }
        if (p.equals("/devices") || p.startsWith("/devices/") || p.startsWith("/api/devices/")) {
            return "Device Management";
        }
        if (p.equals("/leave") || p.startsWith("/leave/") || p.startsWith("/api/leave/")) {
            return "Leave Management";
        }
        if (p.equals("/masters") || p.startsWith("/masters/")) {
            return "Administration";
        }
        if (p.equals("/system") || p.startsWith("/system/")) {
            return "Settings";
        }
        return null;
    }

    private void handleUnauthorized(HttpServletRequest request, HttpServletResponse response, String query) throws Exception {
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            response.sendRedirect("/login?" + query);
        }
    }
}
