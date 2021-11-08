ALTER TABLE `campaigns` ADD COLUMN
`auto_accept` BOOL NOT NULL DEFAULT TRUE
;

ALTER TABLE `device_updates` MODIFY COLUMN
`status` ENUM('scheduled', 'accepted', 'successful', 'cancelled', 'failed') NOT NULL
;
