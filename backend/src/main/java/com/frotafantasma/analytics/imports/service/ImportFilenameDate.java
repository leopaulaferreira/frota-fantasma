package com.frotafantasma.analytics.imports.service;

import java.time.*;
import java.util.regex.Pattern;

public final class ImportFilenameDate {
    private static final Pattern PATTERN = Pattern.compile("^clan-([A-Z0-9]{3,31})-(\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})-\\d+\\.csv$", Pattern.CASE_INSENSITIVE);
    private ImportFilenameDate() {}
    /** A local minute suggestion only: the final numeric component is deliberately ignored. */
    public static LocalDateTime suggest(String filename, String configuredTag) {
        var match = PATTERN.matcher(filename);
        if (!match.matches() || !configuredTag.equalsIgnoreCase("#" + match.group(1))) return null;
        try { return LocalDateTime.of(Integer.parseInt(match.group(2)), Integer.parseInt(match.group(3)),
                Integer.parseInt(match.group(4)), Integer.parseInt(match.group(5)), Integer.parseInt(match.group(6))); }
        catch (DateTimeException e) { return null; }
    }
}
