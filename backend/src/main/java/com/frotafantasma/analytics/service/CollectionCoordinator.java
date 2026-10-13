package com.frotafantasma.analytics.service;

import com.frotafantasma.analytics.client.ClashRoyaleClient;
import com.frotafantasma.analytics.client.ClashRoyaleException;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import com.frotafantasma.analytics.config.CollectionProperties;
import com.frotafantasma.analytics.dto.CollectionResult;
import com.frotafantasma.analytics.entity.CollectionRun;
import com.frotafantasma.analytics.repository.CollectionRunRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CollectionCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(CollectionCoordinator.class);
    private final DataSource database;
    private final CollectionRunRepository runs;
    private final ClashRoyaleClient client;
    private final HistoryStore store;
    private final ClashRoyaleProperties api;
    private final CollectionProperties settings;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CollectionCoordinator(DataSource database, CollectionRunRepository runs, ClashRoyaleClient client,
                                 HistoryStore store, ClashRoyaleProperties api, CollectionProperties settings,
                                 Clock clock, PlatformTransactionManager transactions) {
        this.database = database; this.runs = runs; this.client = client; this.store = store;
        this.api = api; this.settings = settings; this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    public List<CollectionResult> sync() {
        return List.of(collect(CollectionKind.MEMBERS, null), collect(CollectionKind.RIVER, null),
                collect(CollectionKind.WAR_LOG, null));
    }

    public CollectionResult collect(CollectionKind kind, String key) {
        // A session advisory lock spans the HTTP calls without opening a SQL transaction.
        // It is shared by all replicas connected to this database and released in finally.
        try (Connection connection = database.getConnection()) {
            if (!lock(connection, true)) return skipped(kind, "COLLECTION_IN_PROGRESS");
            try {
                for (CollectionRun orphan : runs.findByClanTagAndStatus(api.clanTag(), "RUNNING")) {
                    runs.save(new CollectionRun(orphan.getId(), orphan.getClanTag(), orphan.getKind(), "FAILED",
                            orphan.getStartedAt(), clock.instant(), "COLLECTION_INTERRUPTED", null, orphan.getDeduplicationKey()));
                }
                if (key != null && runs.existsByDeduplicationKeyAndStatus(key, "SUCCESS")) {
                    return skipped(kind, "ALREADY_COLLECTED");
                }
                UUID id = UUID.randomUUID();
                Instant started = clock.instant();
                runs.save(new CollectionRun(id, api.clanTag(), kind.name(), "RUNNING", started, null, null, null, key));
                try {
                    IntSupplier write = prepare(kind, id);
                    return transaction.execute(status -> {
                        int count = write.getAsInt();
                        Instant completed = clock.instant();
                        runs.save(new CollectionRun(id, api.clanTag(), kind.name(), "SUCCESS", started, completed, null, count, key));
                        return new CollectionResult(id, kind.name(), "SUCCESS", started, completed, count, null);
                    });
                } catch (RuntimeException error) {
                    String code = error instanceof ClashRoyaleException apiError ? apiError.code() : "COLLECTION_STORAGE_ERROR";
                    Instant completed = clock.instant();
                    runs.save(new CollectionRun(id, api.clanTag(), kind.name(), "FAILED", started, completed, code, null, key));
                    LOG.warn("Collection {} failed with code {}", kind, code);
                    return new CollectionResult(id, kind.name(), "FAILED", started, completed, null, code);
                }
            } finally {
                lock(connection, false);
            }
        } catch (SQLException error) {
            throw new ClashRoyaleException(HttpStatus.SERVICE_UNAVAILABLE, "COLLECTION_STORAGE_ERROR",
                    "Não foi possível iniciar a coleta no banco de dados.");
        }
    }

    private IntSupplier prepare(CollectionKind kind, UUID runId) {
        return switch (kind) {
            case MEMBERS, DAILY_MEMBERS -> {
                var clan = client.getClan();
                var members = client.getAllMembers(settings.maxPages());
                Instant captured = clock.instant();
                yield () -> store.saveMembers(runId, api.clanTag(), clan, members, captured);
            }
            case RIVER, DAILY_RIVER -> {
                var river = client.getCurrentRiverRace();
                Instant captured = clock.instant();
                yield () -> store.saveRiver(runId, api.clanTag(), river, captured);
            }
            case WAR_LOG, WEEKLY_CLOSE -> {
                var history = client.getRiverRaceLog(settings.maxPages());
                Instant captured = clock.instant();
                yield () -> store.saveWarLog(api.clanTag(), history, captured);
            }
        };
    }

    private boolean lock(Connection connection, boolean acquire) throws SQLException {
        String function = acquire ? "pg_try_advisory_lock" : "pg_advisory_unlock";
        try (var statement = connection.prepareStatement("select " + function + "(hashtextextended(?, 0))")) {
            statement.setString(1, "frota:collection:" + api.clanTag());
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private CollectionResult skipped(CollectionKind kind, String reason) {
        return new CollectionResult(null, kind.name(), "SKIPPED", clock.instant(), clock.instant(), 0, reason);
    }
}
