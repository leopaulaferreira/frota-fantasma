package com.frotafantasma.analytics;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import java.time.Instant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties={"collection.enabled=false","clash-royale.api-token=test-token"})
@AutoConfigureMockMvc
@Testcontainers
class SecurityIntegrationIT {
    @Container static PostgreSQLContainer<?> db=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",db::getJdbcUrl);p.add("spring.datasource.username",db::getUsername);p.add("spring.datasource.password",db::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate sql;
    @org.junit.jupiter.api.BeforeEach void clear() {
        sql.execute("TRUNCATE recommendation_rule_version,clan,player,collection_run CASCADE");
    }
    MockHttpSession login(String username,String password) throws Exception {
        var token=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session=(MockHttpSession)token.getRequest().getSession();
        String old=session.getId();
        var body=json.readTree(token.getResponse().getContentAsString());
        mvc.perform(post("/api/auth/login").session(session).header(body.path("headerName").asText(),body.path("token").asText())
                .param("username",username).param("password",password)).andExpect(status().isOk());
        assertThat(session.getId()).isNotEqualTo(old);
        return session;
    }
    @Test void exigeLoginEPermiteSaudeSemDetalhes() throws Exception {
        mvc.perform(get("/api/analytics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/operations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP")).andExpect(jsonPath("$.components").doesNotExist());
    }
    @Test void loginLogoutECsrfProtegemSessao() throws Exception {
        mvc.perform(post("/api/auth/login").param("username","lider").param("password","integration-admin-password-123")).andExpect(status().isForbidden());
        var session=login("lider","integration-admin-password-123");
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ADMIN"));
        mvc.perform(post("/api/recommendations/evaluations").session(session).contentType("application/json")
                .content("{\"idempotencyKey\":\"missing-csrf\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isOk());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
    @Test void consultaLeDadosMasNaoExecutaMutacoes() throws Exception {
        var session=login("consulta","integration-viewer-password-123");
        mvc.perform(get("/api/analytics").session(session)).andExpect(status().isOk());
        for(String path:new String[]{"/api/recommendations/rules","/api/recommendations/evaluations","/api/recommendations/evaluations/00000000-0000-0000-0000-000000000001/finalize-awards","/api/collections/sync"}) {
            mvc.perform(post(path).session(session).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        }
        mvc.perform(get("/v3/api-docs").session(session)).andExpect(status().isForbidden());
        assertThat(sql.queryForObject("select count(*) from recommendation_evaluation",Long.class)).isZero();
    }
    @Test void liderPodeRegistrarAvaliacao() throws Exception {
        var session=login("lider","integration-admin-password-123");
        mvc.perform(post("/api/recommendations/evaluations").session(session).with(csrf()).contentType("application/json")
                .content("{\"idempotencyKey\":\"admin-test\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.id").exists());
    }
    @Test void rejeitaSenhaInvalidaSemDetalhesSensíveis() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).param("username","lider").param("password","wrong"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Usuário ou senha inválidos."));
    }
    @Test void operacoesSemSnapshotNaoInventamSucesso() throws Exception {
        mvc.perform(get("/api/operations").with(user("viewer").roles("VIEWER"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].lastSuccess").isEmpty()).andExpect(jsonPath("$.sources[0].stale").value(true));
        sql.update("insert into collection_run(id,clan_tag,kind,status,started_at,completed_at,error_code) values (?,?,?,'FAILED',?,?,?)",
                java.util.UUID.randomUUID(),"#RQJGC9GV","MEMBERS",java.sql.Timestamp.from(Instant.now()),java.sql.Timestamp.from(Instant.now()),"UPSTREAM");
        mvc.perform(get("/api/operations").with(user("viewer").roles("VIEWER"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].failuresLast24Hours").value(1));
    }
    @Test void notesArePrivateToLeadershipAndRequireCsrf() throws Exception {
        mvc.perform(get("/api/leadership/notes/active")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/leadership/notes/active").with(user("consulta").roles("VIEWER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/leadership/players/{tag}/notes","#P01").with(user("consulta").roles("VIEWER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/leadership/players/{tag}/notes","#P01").with(user("consulta").roles("VIEWER")).with(csrf())
            .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/leadership/players/{tag}/notes","#P01").with(user("lider").roles("ADMIN"))
            .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/leadership/notes/active").with(user("lider").roles("ADMIN"))).andExpect(status().isOk());
    }

}
