CREATE TABLE `EventJournal` (
    device_uuid char(36) COLLATE utf8_bin NOT NULL,
    event_id char(36) NOT NULL,
    device_time DATETIME(3) NOT NULL,
    event_type_id VARCHAR(100) NOT NULL,
    event_type_version TINYINT UNSIGNED NOT NULL,
    event LONGBLOB NOT NULL,
    received_at DATETIME NOT NULL,
    PRIMARY KEY (device_uuid, event_id),
    CONSTRAINT `fk_event_device`
        FOREIGN KEY (device_uuid) REFERENCES Device (uuid)
);
