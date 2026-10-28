-- Quarantined exports are not snapshots, official wars or proof of association.
CREATE TABLE import_batch (
    id UUID PRIMARY KEY,
    clan_tag VARCHAR(32) NOT NULL,
    source VARCHAR(40) NOT NULL CHECK (source = 'ROYALEAPI_IMPORT'),
    filename VARCHAR(180) NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL,
    imported_by VARCHAR(100) NOT NULL,
    checksum VARCHAR(64) NOT NULL CHECK (checksum ~ '^[a-f0-9]{64}$'),
    format VARCHAR(20) NOT NULL,
    rows_received INTEGER NOT NULL CHECK (rows_received > 0),
    rows_imported INTEGER NOT NULL CHECK (rows_imported >= 0),
    rows_skipped INTEGER NOT NULL CHECK (rows_skipped >= 0),
    rows_conflicted INTEGER NOT NULL CHECK (rows_conflicted >= 0),
    rows_invalid INTEGER NOT NULL CHECK (rows_invalid >= 0),
    rows_unmatched INTEGER NOT NULL CHECK (rows_unmatched >= 0),
    status VARCHAR(40) NOT NULL,
    result JSONB NOT NULL,
    UNIQUE(clan_tag, checksum),
    CHECK (rows_received = rows_imported + rows_skipped + rows_conflicted + rows_invalid + rows_unmatched)
);
CREATE INDEX import_batch_recent ON import_batch(clan_tag, imported_at DESC, id);
CREATE TABLE import_row (
    batch_id UUID NOT NULL REFERENCES import_batch(id),
    row_number INTEGER NOT NULL,
    player_tag VARCHAR(32),
    status VARCHAR(40) NOT NULL,
    reason TEXT NOT NULL,
    values JSONB NOT NULL,
    PRIMARY KEY(batch_id, row_number)
);
CREATE TRIGGER immutable_import_batch BEFORE UPDATE OR DELETE ON import_batch
    FOR EACH ROW EXECUTE FUNCTION reject_recommendation_history_change();
CREATE TRIGGER immutable_import_row BEFORE UPDATE OR DELETE ON import_row
    FOR EACH ROW EXECUTE FUNCTION reject_recommendation_history_change();
