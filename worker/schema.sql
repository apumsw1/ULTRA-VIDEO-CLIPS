PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS jobs (
    id TEXT PRIMARY KEY,
    status TEXT NOT NULL
      CHECK (status IN (
        'QUEUED',
        'PROCESSING',
        'COMPLETED',
        'FAILED'
      )),
    source_url TEXT NOT NULL,
    format TEXT NOT NULL DEFAULT 'source',
    title TEXT,
    file_size INTEGER,
    object_key TEXT,
    download_url TEXT,
    error TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_jobs_status
ON jobs(status);

CREATE INDEX IF NOT EXISTS idx_jobs_created_at
ON jobs(created_at);
