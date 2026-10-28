package com.frotafantasma.analytics.imports;

import com.frotafantasma.analytics.imports.parser.RoyaleApiCsvParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

class RoyaleApiCsvParserTest {
    private final RoyaleApiCsvParser parser = new RoyaleApiCsvParser();
    private MockMultipartFile file(String text) { return new MockMultipartFile("file", "example.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8)); }
    @Test void observedMemberHeaderHandlesBomQuotesAndPreservesMissingValues() {
        var parsed = parser.parse(file("\uFEFF" + RoyaleApiCsvParser.MEMBERS + "\r\n#P01,\"Nome, com \"\"aspas\"\"\",member,20261026T205414.000Z,0,14000,1,Arena,Arena,1,3,,50,0\r\n"));
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().getFirst().values()).containsEntry("name", "Nome, com \"aspas\"").containsEntry("donations", "");
        assertThat(parsed.rows().getFirst().errors()).isEmpty();
        assertThat(parsed.checksum()).hasSize(64);
    }
    @Test void observedRiverHeaderNormalizesTagWithoutInventingWarIdentity() {
        var parsed = parser.parse(file(RoyaleApiCsvParser.RIVER + "\nfull,RQJGC9GV,Frota,16000029,2500,0,,Nome,P01,3100,0,3100,0,16,4\n"));
        assertThat(parsed.format()).isEqualTo("RIVER");
        assertThat(parsed.rows().getFirst().playerTag()).isEqualTo("#P01");
        assertThat(parsed.rows().getFirst().values()).doesNotContainKeys("season_id", "section_index", "captured_at");
    }
    @Test void invalidCellsDoNotTurnIntoZero() {
        var row = parser.parse(file(RoyaleApiCsvParser.MEMBERS + "\n#P01,Nome,member,20260230T205414.000Z,0,14000,1,Arena,Arena,1,3,-1,50,0\n")).rows().getFirst();
        assertThat(row.errors()).hasSize(2);
        assertThat(row.values().get("donations")).isEqualTo("-1");
    }
    @Test void unknownHeaderEmptyBadQuotesAndColumnCountsAreRejected() {
        for (String text : new String[]{"", RoyaleApiCsvParser.MEMBERS + "\n", "tag,points\n#P01,100\n",
                RoyaleApiCsvParser.MEMBERS + "\n#P01,Nome\n", RoyaleApiCsvParser.MEMBERS + "\n\"unclosed",
                RoyaleApiCsvParser.MEMBERS + "\n\"closed\"garbage"}) {
            assertThatThrownBy(() -> parser.parse(file(text))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
    }
    @Test void limitsEncodingAndFilenameAreCheckedIndependentlyOfMime() {
        assertThatThrownBy(() -> parser.parse(file("x".repeat(RoyaleApiCsvParser.MAX_BYTES + 1)))).hasMessageContaining("413");
        assertThatThrownBy(() -> parser.parse(file(RoyaleApiCsvParser.MEMBERS + "\n" + "x\n".repeat(2001)))).hasMessageContaining("2.000");
        assertThatThrownBy(() -> parser.parse(new MockMultipartFile("file", "../bad.csv", "text/csv", new byte[]{1}))).hasMessageContaining("Nome");
        assertThatThrownBy(() -> parser.parse(new MockMultipartFile("file", "bad.csv", "text/csv", new byte[]{(byte)0xff}))).hasMessageContaining("UTF-8");
        assertThatThrownBy(() -> parser.parse(file("a\0b"))).hasMessageContaining("binário");
    }
}
