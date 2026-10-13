package com.frotafantasma.analytics.security;
import static org.assertj.core.api.Assertions.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
class AuthPropertiesTest {
    @Test void exigeCredenciaisFortesEContaDeConsultaCompleta() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            assertThat(validator.validate(new AuthProperties("lider","long-admin-password-123","",""))).isEmpty();
            assertThat(validator.validate(new AuthProperties("lider","short","",""))).isNotEmpty();
            assertThat(validator.validate(new AuthProperties("lider","replace-with-a-unique-long-password","",""))).isNotEmpty();
            assertThat(new AuthProperties("lider","long-admin-password-123","consulta","long-viewer-password-123").toString())
                    .doesNotContain("long-admin","long-viewer");
            assertThat(validator.validate(new AuthProperties("lider","á".repeat(40),"",""))).isNotEmpty();
            assertThat(validator.validate(new AuthProperties("lider","long-admin-password-123","consulta",""))).isNotEmpty();
            assertThat(validator.validate(new AuthProperties("lider","long-admin-password-123","lider","long-viewer-password-123"))).isNotEmpty();
        }
    }
}
