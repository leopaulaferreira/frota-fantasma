package com.frotafantasma.analytics.imports.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import com.frotafantasma.analytics.dto.HistoryDtos.PageResponse;
import com.frotafantasma.analytics.imports.dto.ImportDtos.*;
import com.frotafantasma.analytics.imports.parser.RoyaleApiCsvParser;
import com.frotafantasma.analytics.imports.parser.RoyaleApiCsvParser.Parsed;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoyaleApiImportService {
    private final JdbcTemplate sql;
    private final ClashRoyaleProperties config;
    private final ObjectMapper json;
    private final Clock clock;
    private final HistoricalBackfillService backfill;
    public RoyaleApiImportService(JdbcTemplate sql, ClashRoyaleProperties config, ObjectMapper json, Clock clock, HistoricalBackfillService backfill) {
        this.sql = sql; this.config = config; this.json = json; this.clock = clock; this.backfill=backfill;
    }
    @Transactional(readOnly = true)
    public Preview preview(Parsed file) {
        Set<String> known = new HashSet<>(sql.queryForList("select player_tag from player", String.class));
        var rows = new ArrayList<RowResult>(); var identified = new HashSet<String>();
        for (var row : file.rows()) {
            if (known.contains(row.playerTag())) identified.add(row.playerTag());
            String reason;
            String status = "UNMATCHED";
            if (!row.errors().isEmpty()) { status = "INVALID"; reason = String.join(" ", row.errors()); }
            else if (file.format().equals("RIVER") && !config.clanTag().equals(RoyaleApiCsvParser.tag(row.values().get("clan_tag")))) {
                reason = "O clã da linha não corresponde ao clã configurado.";
            } else if (!known.contains(row.playerTag())) reason = "Jogador desconhecido no histórico local; nenhum jogador foi criado.";
            else reason = file.format().equals("RIVER")
                    ? "Faltam season_id e section_index: não é possível identificar uma guerra oficial com segurança."
                    : "Faltam clã e instante de captura no conteúdo. lastSeen não é a data da captura.";
            rows.add(new RowResult(row.line(), row.playerTag(), status, reason));
        }
        int invalid = (int) rows.stream().filter(r -> r.status().equals("INVALID")).count();
        var existing = find(file.checksum());
        return new Preview(file.filename(), file.checksum(), "ROYALEAPI_IMPORT", file.format(), rows.size(), identified.size(),
                rows.size() - invalid, 0, 0, 0, invalid, rows.size() - invalid,
                List.of("Este formato não contém identificação temporal suficiente. Nenhuma linha será incorporada às métricas.",
                        "Novos, duplicados e conflitos com a Supercell não podem ser determinados sem a identidade do registro.",
                        "Confirmar apenas guarda o lote e suas pendências para auditoria. Não altera guerras, membros ou doações."),
                invalid == 0 ? List.of() : List.of("Existem registros inválidos. O arquivo não pode ser confirmado; corrija e analise novamente."),
                List.copyOf(rows), existing == null ? null : existing.id());
    }
    @Transactional
    public Result confirm(Parsed file, String actor) {
        // Serializes checksum replays across instances; never locks or changes official collection tables.
        sql.queryForObject("select pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class,
                "royaleapi-import:" + config.clanTag() + ":" + file.checksum());
        Batch existing = find(file.checksum());
        if (existing != null) return new Result(existing, true);
        Preview analysis = preview(file);
        if (analysis.rowsInvalid() > 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV contém registros inválidos; nenhuma gravação realizada.");
        var batch = new Batch(UUID.randomUUID(), analysis.source(), file.filename(), clock.instant(),
                actor,
                file.checksum(), analysis.rowsReceived(), 0, 0, 0, 0, analysis.rowsUnmatched(), "PENDING_IDENTIFICATION", analysis);
        sql.update("""
            insert into import_batch(id,clan_tag,source,filename,imported_at,imported_by,checksum,format,
              rows_received,rows_imported,rows_skipped,rows_conflicted,rows_invalid,rows_unmatched,status,result)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb)
            """, batch.id(), config.clanTag(), batch.source(), batch.filename(), Timestamp.from(batch.importedAt()),
            batch.importedBy(), batch.checksum(), file.format(), batch.rowsReceived(), 0, 0, 0, 0,
            batch.rowsUnmatched(), batch.status(), encode(batch));
        for (int i = 0; i < file.rows().size(); i++) {
            var row = file.rows().get(i); var result = analysis.rows().get(i);
            sql.update("insert into import_row(batch_id,row_number,player_tag,status,reason,values) values(?,?,?,?,?,?::jsonb)",
                    batch.id(), row.line(), row.playerTag(), result.status(), result.reason(), encode(row.values()));
        }
        return new Result(batch, false);
    }
    @Transactional(readOnly = true)
    public PageResponse<Batch> history(int page, int size) {
        long count = sql.queryForObject("select count(*) from import_batch where clan_tag=?", Long.class, config.clanTag());
        var items = sql.query("""
                select b.result, coalesce((select r.status from import_resolution r where r.batch_id=b.id order by r.revision desc limit 1), b.status) as current_status
                from import_batch b where b.clan_tag=? order by b.imported_at desc,b.id limit ? offset ?
                """,
                (rs, n) -> {
                    var b = decode(rs.getString(1));
                    return new Batch(b.id(),b.source(),b.filename(),b.importedAt(),b.importedBy(),b.checksum(),b.rowsReceived(),
                        b.rowsImported(),b.rowsSkipped(),b.rowsConflicted(),b.rowsInvalid(),b.rowsUnmatched(),rs.getString(2),b.analysis(),backfill.summary(b.id()));
                }, config.clanTag(), size, (long) page * size);
        return new PageResponse<>(items, page, size, count, (int) ((count + size - 1) / size));
    }
    private Batch find(String checksum) {
        var matches = sql.query("select result from import_batch where clan_tag=? and checksum=?", (rs, n) -> decode(rs.getString(1)), config.clanTag(), checksum);
        return matches.isEmpty() ? null : matches.getFirst();
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException("Falha ao serializar auditoria.", e); }
    }
    private Batch decode(String value) {
        try { return json.readValue(value, Batch.class); } catch (JsonProcessingException e) { throw new IllegalStateException("Falha ao ler auditoria.", e); }
    }
}
