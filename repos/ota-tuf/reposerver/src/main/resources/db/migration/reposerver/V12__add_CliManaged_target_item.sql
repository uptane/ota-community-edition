
ALTER TABLE target_items MODIFY COLUMN `storage_method` ENUM('Managed', 'Unmanaged', 'CliManaged')
;