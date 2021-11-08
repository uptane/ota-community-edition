CREATE TABLE IndexedEventsArchive (
  device_uuid    CHAR(36)     NOT NULL COLLATE utf8_bin,
  event_id       CHAR(36)     NOT NULL COLLATE utf8_unicode_ci ,
  correlation_id VARCHAR(256) NOT NULL,
  event_type     VARCHAR(256) DEFAULT NULL,
  created_at     DATETIME(3)  NOT NULL DEFAULT current_timestamp(3),
  updated_at     DATETIME(3)  NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),

  PRIMARY KEY (device_uuid, event_id),
  INDEX(correlation_id)
);
