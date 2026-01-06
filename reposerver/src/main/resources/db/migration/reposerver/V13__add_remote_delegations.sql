ALTER TABLE delegations
  ADD COLUMN `uri` VARCHAR(2048) NULL,
  ADD COLUMN `remote_headers` TEXT NOT NULL DEFAULT '{}',
  ADD COLUMN `last_fetched_at` datetime(3) NULL
;

