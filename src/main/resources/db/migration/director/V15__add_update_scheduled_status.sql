ALTER TABLE Device MODIFY device_status
 ENUM('NotSeen', 'Error', 'UpToDate', 'UpdatePending', 'Outdated', 'UpdateScheduled')
 NOT NULL;
