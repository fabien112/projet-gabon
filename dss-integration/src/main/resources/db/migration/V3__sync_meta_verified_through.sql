-- Curseur de synchro : dernier jour calendaire clos aligné sur DSS.
ALTER TABLE sync_meta ADD COLUMN IF NOT EXISTS verified_through DATE;
