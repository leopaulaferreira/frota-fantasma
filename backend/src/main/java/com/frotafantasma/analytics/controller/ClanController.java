package com.frotafantasma.analytics.controller;

import com.frotafantasma.analytics.service.ClanService;
import com.frotafantasma.analytics.dto.ClanMemberResponse;
import com.frotafantasma.analytics.dto.ClanResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clan")
@Tag(name = "Clã", description = "Últimos dados coletados do clã definido por CLAN_TAG")
@ApiResponses({
        @ApiResponse(responseCode = "404", description = "Clã não encontrado", content = @Content(
                mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "502", description = "Falha de integração ou resposta inválida", content = @Content(
                mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "503", description = "Indisponibilidade ou rate limit; Retry-After indica a espera", content = @Content(
                mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "504", description = "Tempo limite da API externa excedido", content = @Content(
                mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
})
public class ClanController {
    private final ClanService service;

    public ClanController(ClanService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Consultar o clã", description = "Retorna o último retrato persistido; a coleta é feita pelos jobs ou POST /api/collections/sync.")
    @ApiResponse(responseCode = "200", description = "Dados atuais do clã", content = @Content(
            mediaType = "application/json", schema = @Schema(implementation = ClanResponse.class)))
    public ClanResponse getClan() {
        return service.getClan();
    }

    @GetMapping("/members")
    @Operation(summary = "Listar membros atuais", description = "Retorna os membros da última coleta completa persistida, sem chamar a Supercell.")
    @ApiResponse(responseCode = "200", description = "Lista dos membros atuais", content = @Content(
            mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = ClanMemberResponse.class))))
    public List<ClanMemberResponse> getMembers() {
        return service.getMembers();
    }
}
