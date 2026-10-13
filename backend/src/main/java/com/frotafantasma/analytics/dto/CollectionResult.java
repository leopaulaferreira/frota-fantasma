package com.frotafantasma.analytics.dto;

import java.time.Instant;
import java.util.UUID;

public record CollectionResult(UUID id, String kind, String status, Instant startedAt,
                               Instant completedAt, Integer recordCount, String errorCode) {}
