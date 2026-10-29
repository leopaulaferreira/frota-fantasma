package com.frotafantasma.analytics.imports.parser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Only schemas observed in the supplied RoyaleAPI exports. Never infers time from filenames. */
@Component
public class RoyaleApiCsvParser {
    public static final int MAX_BYTES = 512 * 1024, MAX_ROWS = 2000;
    public static final String MEMBERS = "tag,name,role,lastSeen,expLevel,trophies,arena_id,arena_name,arena_rawName,clanRank,previousClanRank,donations,donationsReceived,clanChestPoints";
    public static final String RIVER = "state,clan_tag,clan_name,clan_badge_id,clan_fame,clan_repair_points,clan_finish_time,player_name,player_tag,player_fame,player_repair_points,player_contribution,player_boat_attacks,player_decks_used,player_decks_used_today";
    private static final List<String> WAR_HISTORY_PREFIX = List.of(("clan_tag,clan_name,player_name,player_tag,player_participation_ratio,player_contribution_count,player_participating_count,player_total_fame,player_total_repair_points,player_total_contribution,player_total_decks_used,player_is_current_member").split(","));
    public record Row(int line, String playerTag, Map<String,String> values, List<String> errors) {}
    public record Parsed(String filename, String checksum, String format, List<Row> rows) {}
    public Parsed parse(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.length() > 180 || !filename.toLowerCase(Locale.ROOT).endsWith(".csv")
                || filename.contains("/") || filename.contains("\\") || filename.chars().anyMatch(c -> Character.isISOControl(c)))
            throw bad("Nome de arquivo inválido. Selecione um arquivo .csv sem caminhos.");
        if (file.getSize() > MAX_BYTES) throw large();
        byte[] bytes;
        try (var input = file.getInputStream()) { bytes = input.readNBytes(MAX_BYTES + 1); }
        catch (IOException e) { throw bad("Não foi possível ler o CSV."); }
        if (bytes.length > MAX_BYTES) throw large();
        if (bytes.length == 0) throw bad("O arquivo está vazio.");
        String text;
        try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { throw bad("O CSV precisa estar codificado em UTF-8."); }
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        if (text.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
            throw bad("Conteúdo binário ou caracteres de controle não são aceitos.");
        List<List<String>> records = records(text);
        if (records.size() < 2) throw bad("O CSV não contém registros.");
        List<String> header = records.getFirst();
        String format = header.equals(List.of(MEMBERS.split(","))) ? "MEMBERS" : header.equals(List.of(RIVER.split(","))) ? "RIVER" : null;
        if (format == null) {
            if (isWarHistoryHeader(header)) throw unsupportedWarHistory();
            throw bad("Cabeçalho desconhecido. São aceitos somente os formatos de membros e corrida RoyaleAPI verificados.");
        }
        var rows = new ArrayList<Row>();
        for (int i = 1; i < records.size(); i++) {
            var cells = records.get(i);
            if (cells.size() != header.size()) throw bad("Estrutura inválida no registro " + (i + 1) + ": quantidade de colunas diferente do cabeçalho.");
            Map<String,String> values = new LinkedHashMap<>();
            for (int j = 0; j < header.size(); j++) values.put(header.get(j), cells.get(j));
            var errors = new ArrayList<String>();
            String tag = tag(values.get(format.equals("MEMBERS") ? "tag" : "player_tag"));
            if (tag == null) errors.add("Tag de jogador inválida.");
            if (format.equals("RIVER") && tag(values.get("clan_tag")) == null) errors.add("Tag de clã inválida.");
            for (String column : header) {
                if (Set.of("expLevel","trophies","arena_id","clanRank","previousClanRank","donations","donationsReceived","clanChestPoints",
                        "clan_badge_id","clan_fame","clan_repair_points","player_fame","player_repair_points","player_contribution","player_boat_attacks","player_decks_used","player_decks_used_today").contains(column)) {
                    String value = values.get(column);
                    if (!value.isEmpty()) try { if (!value.matches("[0-9]+") || Integer.parseInt(value) < 0) throw new NumberFormatException(); }
                    catch (NumberFormatException e) { errors.add("Número inteiro não negativo inválido: " + column + "."); }
                }
            }
            if (format.equals("MEMBERS")) {
                String seen = values.get("lastSeen");
                if (!seen.isEmpty()) try { Instant.from(DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss.SSSX").withResolverStyle(java.time.format.ResolverStyle.STRICT).parse(seen)); }
                catch (DateTimeException e) { errors.add("lastSeen inválido."); }
                if (!values.get("role").isEmpty() && !Set.of("member","elder","coLeader","leader").contains(values.get("role"))) errors.add("Cargo desconhecido.");
            }
            rows.add(new Row(i + 1, tag, Collections.unmodifiableMap(values), List.copyOf(errors)));
        }
        try { return new Parsed(filename, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), format, List.copyOf(rows)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String tag(String value) {
        if (value == null) return null;
        String result = value.startsWith("#") ? value : "#" + value;
        return result.matches("#[A-Z0-9]{3,31}") ? result : null;
    }
    // Strict RFC-style quoted fields, embedded line breaks and escaped quotes; bounded before allocation.
    private List<List<String>> records(String text) {
        var result = new ArrayList<List<String>>(); var row = new ArrayList<String>(); var cell = new StringBuilder();
        boolean quoted = false, closed = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else { quoted = false; closed = true; }
                } else cell.append(c);
            } else if (c == '"') {
                if (cell.length() != 0 || closed) throw bad("Aspas malformadas no CSV.");
                quoted = true;
            } else if (c == ',' || c == '\n' || c == '\r') {
                row.add(cell.toString()); cell.setLength(0); closed = false;
                checkColumns(row, result.isEmpty());
                if (c != ',') {
                    if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                    result.add(List.copyOf(row)); row.clear();
                    if (result.size() > MAX_ROWS + 1) throw bad("Limite de 2.000 registros excedido.");
                }
            } else {
                if (closed) throw bad("Conteúdo após fechamento de aspas no CSV.");
                cell.append(c);
            }
            if (cell.length() > 4096) throw bad("Campo excede o limite de 4.096 caracteres.");
        }
        if (quoted) throw bad("Aspas não fechadas no CSV.");
        if (closed || cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            checkColumns(row, result.isEmpty());
            result.add(List.copyOf(row));
        }
        if (result.size() > MAX_ROWS + 1) throw bad("Limite de 2.000 registros excedido.");
        return result;
    }
    private static boolean isWarHistoryHeader(List<String> header) {
        return header.size() > WAR_HISTORY_PREFIX.size()
                && header.subList(0, WAR_HISTORY_PREFIX.size()).equals(WAR_HISTORY_PREFIX)
                && header.subList(WAR_HISTORY_PREFIX.size(), header.size()).stream()
                    .allMatch(column -> column.matches("s_[0-9]+-[0-9]+_(fame|repair_points|contribution|decks_used)"));
    }
    private static void checkColumns(List<String> row, boolean header) {
        if (row.size() <= 30) return;
        if (header && isWarHistoryHeader(row)) throw unsupportedWarHistory();
        throw bad("Quantidade de colunas excessiva. O limite dos formatos suportados é 30 colunas.");
    }
    private static ResponseStatusException unsupportedWarHistory() {
        return bad("Este CSV contém histórico de várias semanas de guerra (colunas s_temporada-seção). "
                + "Esse formato do RoyaleAPI ainda não é suportado: a identificação atual associa cada lote a uma única corrida. "
                + "Nenhum dado foi importado. Use um CSV de membros ou de uma corrida; não remova colunas para tentar adaptar este histórico.");
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException large() { return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "O CSV deve ter no máximo 512 KiB."); }
}
