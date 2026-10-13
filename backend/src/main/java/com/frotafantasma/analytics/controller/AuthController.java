package com.frotafantasma.analytics.controller;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public record Csrf(String headerName,String token) {}
    public record Account(String username,List<String> roles) {}
    @GetMapping("/csrf") public Csrf csrf(CsrfToken token) {return new Csrf(token.getHeaderName(),token.getToken());}
    @GetMapping("/me") public Account me(Authentication a) {
        return new Account(a.getName(),a.getAuthorities().stream().map(x->x.getAuthority().replace("ROLE_","")).toList());
    }
}
