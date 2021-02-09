ALTER TABLE `device_updates`
    MODIFY COLUMN `status` ENUM('requested', 'rejected', 'scheduled', 'accepted', 'successful', 'cancelled', 'failed') NOT NULL DEFAULT 'requested'
;
