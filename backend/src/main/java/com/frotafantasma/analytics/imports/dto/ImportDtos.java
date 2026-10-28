package com.frotafantasma.analytics.imports.dto;

import java.time.Instant;
import java.util.*;

public final class ImportDtos {
    private ImportDtos() {}
    public record RowResult(int line, String playerTag, String status, String reason) {}
    public record Preview(String filename, String checksum, String source, String format,
            int rowsReceived, int playersIdentified, int rowsValid, int rowsNew, int rowsExisting,
            int rowsConflicted, int rowsInvalid, int rowsUnmatched, List<String> warnings,
            List<String> errors, List<RowResult> rows, UUID existingBatchId) {}
    public record PreviewResponse(Preview analysis, String previewToken) {}
    public record Batch(UUID id, String source, String filename, Instant importedAt, String importedBy,
            String checksum, int rowsReceived, int rowsImported, int rowsSkipped,
            int rowsConflicted, int rowsInvalid, int rowsUnmatched, String status, Preview analysis) {}
    public record Result(Batch batch, boolean alreadyProcessed) {}
}
