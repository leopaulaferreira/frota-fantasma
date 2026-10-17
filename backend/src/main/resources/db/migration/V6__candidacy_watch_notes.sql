ALTER TABLE leadership_note DROP CONSTRAINT leadership_note_kind_check;
ALTER TABLE leadership_note ADD CONSTRAINT leadership_note_kind_check
    CHECK (kind IN ('OBSERVATION','JUSTIFICATION','AGREEMENT','CANDIDACY'));
