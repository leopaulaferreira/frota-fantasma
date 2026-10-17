package com.frotafantasma.analytics.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfiguration {
    public static void problem(HttpServletResponse r,int code,String detail) throws IOException {
        r.setStatus(code);r.setContentType("application/problem+json");r.getWriter().write("{\"status\":"+code+",\"detail\":\""+detail+"\"}");
    }
    @Bean UserDetailsService accounts(AuthProperties p) {
        var encoder=new BCryptPasswordEncoder(12);
        var users=new InMemoryUserDetailsManager(User.withUsername(p.adminUsername()).password(encoder.encode(p.adminPassword())).roles("ADMIN").build());
        if(!p.viewerUsername().isBlank()) users.createUser(User.withUsername(p.viewerUsername()).password(encoder.encode(p.viewerPassword())).roles("VIEWER").build());
        return users;
    }
    @Bean BCryptPasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityFilterChain security(HttpSecurity http,LoginThrottle throttle) throws Exception {
        http.authorizeHttpRequests(a->a
                .requestMatchers(HttpMethod.GET,"/api/auth/csrf","/actuator/health","/actuator/health/**").permitAll()
                .requestMatchers("/api/auth/login","/api/auth/logout","/error").permitAll()
                .requestMatchers("/api/leadership/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/api/**").authenticated()
                .requestMatchers("/swagger-ui/**","/swagger-ui.html","/v3/api-docs/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,"/api/**").hasRole("ADMIN")
                .anyRequest().denyAll());
        http.exceptionHandling(e->e
                .authenticationEntryPoint((q,r,x)->problem(r,401,"Entre na sua conta para consultar a Frota."))
                .accessDeniedHandler((q,r,x)->problem(r,403,"Acesso negado ou sessão de segurança expirada. Atualize a página e confira suas permissões.")));
        http.formLogin(f->f.loginProcessingUrl("/api/auth/login")
                .successHandler((q,r,a)->{throttle.succeeded(q.getRemoteAddr());r.setContentType("application/json");r.getWriter().write("{\"authenticated\":true}");})
                .failureHandler((q,r,e)->{throttle.failed(q.getRemoteAddr());problem(r,401,"Usuário ou senha inválidos.");}));
        http.logout(l->l.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((q,r,a)->{r.setContentType("application/json");r.getWriter().write("{\"loggedOut\":true}");}));
        http.addFilterBefore(new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest q,HttpServletResponse r,FilterChain chain) throws ServletException,IOException {
                if("POST".equals(q.getMethod()) && "/api/auth/login".equals(q.getServletPath())) {
                    long wait=throttle.retryAfter(q.getRemoteAddr());
                    if(wait>0) {r.setHeader("Retry-After",Long.toString(wait));problem(r,429,"Muitas tentativas de login. Aguarde alguns minutos.");return;}
                }
                chain.doFilter(q,r);
            }
        },UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
