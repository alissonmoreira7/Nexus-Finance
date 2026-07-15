package com.dev.nexusfinance.config;

import com.dev.nexusfinance.exceptions.UnauthorizedException;
import com.dev.nexusfinance.services.AuthService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class AuthFilter implements Filter {
    private final AuthService authService;
    public AuthFilter(AuthService authService) { this.authService = authService; }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        String path = http.getRequestURI();
        boolean publicRoute = "OPTIONS".equals(http.getMethod()) || path.equals("/api/v1/auth/login")
            || (path.equals("/api/v1/users") && "POST".equals(http.getMethod())) || !path.startsWith("/api/v1/");
        if (publicRoute) { chain.doFilter(request, response); return; }
        String header = http.getHeader("Authorization");
        try {
            if (header == null || !header.startsWith("Bearer ")) throw new UnauthorizedException("Autenticação obrigatória");
            http.setAttribute("authenticatedUserId", authService.validate(header.substring(7)));
            chain.doFilter(request, response);
        } catch (UnauthorizedException exception) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(401);
            httpResponse.setContentType("application/json");
            String safeMessage = exception.getMessage().replace("\\", "\\\\").replace("\"", "\\\"");
            httpResponse.getWriter().write("{\"status\":401,\"message\":\"" + safeMessage + "\"}");
        }
    }
}
