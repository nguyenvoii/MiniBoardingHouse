package com.group7.minihouse.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
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
    private final UserStatusChecker statusChecker;

    public JwtAuthFilter(JwtService jwtService, UserStatusChecker statusChecker) {
        this.jwtService = jwtService;
        this.statusChecker = statusChecker;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            AuthUser token = jwtService.parse(header.substring(7).trim());
            // Token hop le nhung tai khoan da bi khoa/vo hieu hoa/xoa thi coi nhu chua dang nhap (401).
            // Vai tro lay tu DB moi request (khong lay tu token) nen giang chuc/doi vai tro co hieu luc ngay.
            Optional<List<String>> dbRoles = token == null ? Optional.empty() : statusChecker.activeRoles(token.userId());
            if (dbRoles.isPresent()) {
                AuthUser user = new AuthUser(token.userId(), token.email(), token.fullName(), dbRoles.get(),
                        token.tenantId());
                var authorities = user.roles().stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, authorities));
            }
        }
        chain.doFilter(request, response);
    }
}
