ALTER TABLE `Device` MODIFY COLUMN `device_status` enum('NotSeen', 'Error', 'UpToDate', 'UpdatePending', 'Outdated') DEFAULT 'NotSeen'
;
