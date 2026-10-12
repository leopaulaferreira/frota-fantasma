package com.frotafantasma.analytics.controller;

import com.frotafantasma.analytics.service.ClanService;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.frotafantasma.analytics.dto.ClanMemberResponse;
import com.frotafantasma.analytics.dto.ClanResponse;
import com.frotafantasma.analytics.client.ClashRoyaleException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ClanControllerTest {
    private ClanService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(ClanService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ClanController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void retornaClaEMembros() throws Exception {
        when(service.getClan()).thenReturn(new ClanResponse(
                "#RQJGC9GV", "Frota Fantasma", null, "open", 100, 200, 0, 30, 1));
        when(service.getMembers()).thenReturn(List.of(new ClanMemberResponse(
                "#PLAYER", "Player", "leader", 15, 9000, 1, 1, 10, 0)));

        mvc.perform(get("/api/clan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tag").value("#RQJGC9GV"));
        mvc.perform(get("/api/clan/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Player"));
    }

    @Test
    void retornaErroEstruturado() throws Exception {
        when(service.getClan()).thenThrow(new ClashRoyaleException(
                HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", "Falha ao consultar a API do Clash Royale."));

        mvc.perform(get("/api/clan"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"))
                .andExpect(jsonPath("$.detail").value("Falha ao consultar a API do Clash Royale."));
    }
}

