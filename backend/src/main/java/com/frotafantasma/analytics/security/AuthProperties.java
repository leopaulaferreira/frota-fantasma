package com.frotafantasma.analytics.security;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
        @DefaultValue("lider") @NotBlank String adminUsername,
        String adminPassword,
        @DefaultValue("") String viewerUsername,
        @DefaultValue("") String viewerPassword) {
    @AssertTrue(message="A conta de consulta precisa de usuário e senha de 16 a 72 caracteres; use nomes distintos.")
    public boolean isViewerValid() {
        return viewerUsername.isBlank() && viewerPassword.isBlank() ||
                !viewerUsername.isBlank() && !viewerUsername.equals(adminUsername) &&
                        viewerPassword.length()>=16 && viewerPassword.length()<=72;
    }
    @AssertTrue(message="Configure senhas únicas de 16 a 72 caracteres e até 72 bytes em UTF-8.")
    public boolean isPasswordLengthValid() {
        return adminPassword != null && adminPassword.length()>=16 && !adminPassword.startsWith("replace-") &&
                (viewerPassword.isBlank() || !viewerPassword.startsWith("replace-")) && adminPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72 &&
                viewerPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72;
    }
    @Override public String toString() { return "AuthProperties[credentials=REDACTED]"; }
}
