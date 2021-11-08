-- Update charset needed for support emoji in release notes
ALTER TABLE campaign_metadata MODIFY COLUMN value TEXT
    CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL;
