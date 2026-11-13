package com.group7.minihouse.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Doc header "Authorization: Bearer <token>", neu hop le thi dat nguoi dung vao SecurityContext.
 * Role "MANAGER" thanh quyen "ROLE_MANAGER" (dung hasRole('MANAGER') trong @PreAuthorize).
 * Token sai/het han: bo qua, request di tiep chua dang nhap va se bi tra 401 neu API can dang nhap.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            AuthUser user = jwtService.parse(header.substring(7).trim());
            if (user != null) {
                var authorities = user.roles().stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, authorities));
            }
        }
        chain.doFilter(request, response);
    }
}
