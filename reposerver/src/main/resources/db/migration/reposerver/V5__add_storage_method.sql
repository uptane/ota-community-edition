ALTER TABLE target_items ADD COLUMN `storage_method` ENUM('Managed', 'Unmanaged')
NOT NULL DEFAULT 'Managed'
;

ALTER TABLE target_items ALTER COLUMN `storage_method` DROP DEFAULT
;

