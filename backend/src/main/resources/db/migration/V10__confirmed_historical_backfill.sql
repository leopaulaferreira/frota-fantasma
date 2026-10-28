CREATE TABLE import_application (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES import_batch(id),
    resolution_id BIGINT NOT NULL REFERENCES import_resolution(revision),
    applied_at TIMESTAMPTZ NOT NULL,
    applied_by VARCHAR(100) NOT NULL,
    result JSONB NOT NULL,
    UNIQUE(batch_id,resolution_id)
);
CREATE TABLE import_application_row (
    application_id UUID NOT NULL REFERENCES import_application(id),
    row_number INTEGER NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    evidence JSONB NOT NULL,
    PRIMARY KEY(application_id,row_number)
);
CREATE TRIGGER immutable_import_application BEFORE UPDATE OR DELETE ON import_application
    FOR EACH ROW EXECUTE FUNCTION reject_recommendation_history_change();
CREATE TRIGGER immutable_import_application_row BEFORE UPDATE OR DELETE ON import_application_row
    FOR EACH ROW EXECUTE FUNCTION reject_recommendation_history_change();

ALTER TABLE war ADD COLUMN source VARCHAR(40) NOT NULL DEFAULT 'DIRECT_API',
    ADD COLUMN batch_id UUID REFERENCES import_batch(id),
    ADD COLUMN resolution_id BIGINT REFERENCES import_resolution(revision),
    ADD COLUMN imported_at TIMESTAMPTZ;
ALTER TABLE war_participant ADD COLUMN source VARCHAR(40) NOT NULL DEFAULT 'DIRECT_API',
    ADD COLUMN batch_id UUID REFERENCES import_batch(id),
    ADD COLUMN resolution_id BIGINT REFERENCES import_resolution(revision),
    ADD COLUMN imported_at TIMESTAMPTZ;
ALTER TABLE clan_membership_history ADD COLUMN source VARCHAR(40) NOT NULL DEFAULT 'DIRECT_API',
    ADD COLUMN batch_id UUID REFERENCES import_batch(id),
    ADD COLUMN resolution_id BIGINT REFERENCES import_resolution(revision),
    ADD COLUMN imported_at TIMESTAMPTZ;
ALTER TABLE clan_membership_history ALTER COLUMN run_id DROP NOT NULL;
ALTER TABLE clan_membership_history ADD CONSTRAINT official_membership_requires_run CHECK(source <> 'DIRECT_API' OR run_id IS NOT NULL);
DO $$ BEGIN
    FOR i IN 1..3 LOOP
        EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I CHECK ((source = ''DIRECT_API'' AND batch_id IS NULL AND resolution_id IS NULL AND imported_at IS NULL) OR (source = ''ROYALEAPI_IMPORT'' AND batch_id IS NOT NULL AND resolution_id IS NOT NULL AND imported_at IS NOT NULL))',
            (ARRAY['war','war_participant','clan_membership_history'])[i], (ARRAY['war_source','participant_source','membership_source'])[i]);
    END LOOP;
END $$;
CREATE INDEX membership_identity_source ON clan_membership_history(clan_id,player_id,captured_at,source);
CREATE VIEW active_import_resolution AS
    SELECT r.* FROM import_resolution r
    WHERE r.status='IDENTIFIED' AND r.manually_confirmed
      AND NOT EXISTS(SELECT 1 FROM import_resolution newer WHERE newer.batch_id=r.batch_id AND newer.revision>r.revision)
      AND EXISTS(SELECT 1 FROM import_application a WHERE a.batch_id=r.batch_id AND a.resolution_id=r.revision);
CREATE VIEW effective_war AS SELECT w.* FROM war w WHERE w.source='DIRECT_API'
    OR EXISTS(SELECT 1 FROM active_import_resolution r WHERE r.revision=w.resolution_id AND r.batch_id=w.batch_id);
CREATE VIEW effective_war_participant AS SELECT p.* FROM war_participant p
    WHERE EXISTS(SELECT 1 FROM effective_war w WHERE w.id=p.war_id)
      AND (p.source='DIRECT_API' OR EXISTS(SELECT 1 FROM active_import_resolution r WHERE r.revision=p.resolution_id AND r.batch_id=p.batch_id));
CREATE VIEW effective_membership_history AS
    SELECT DISTINCT ON (clan_id,player_id,captured_at) h.* FROM clan_membership_history h
    WHERE h.source='DIRECT_API' OR EXISTS(SELECT 1 FROM active_import_resolution r WHERE r.revision=h.resolution_id AND r.batch_id=h.batch_id)
    ORDER BY clan_id,player_id,captured_at,(source='DIRECT_API') DESC,id DESC;
