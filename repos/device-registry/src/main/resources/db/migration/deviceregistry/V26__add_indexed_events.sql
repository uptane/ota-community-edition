CREATE TABLE IndexedEvents (
  device_uuid char(36) COLLATE utf8_bin NOT NULL,
  event_id char(36) COLLATE utf8_unicode_ci NOT NULL,
  correlation_id varchar(256) NOT NULL,
  `event_type` varchar(256) DEFAULT NULL,

  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),

  PRIMARY KEY (device_uuid, event_id),
  CONSTRAINT `fk_indexed_event_device`  FOREIGN KEY (device_uuid) REFERENCES Device(uuid),
  CONSTRAINT `fk_indexed_event` FOREIGN KEY (device_uuid, event_id) REFERENCES EventJournal(device_uuid, event_id),
  INDEX(correlation_id)
);
