DROP TABLE DeviceTag;

CREATE TABLE TaggedDevice (
  namespace   VARCHAR(255) NOT NULL,
  device_uuid CHAR(36)     NOT NULL,
  tag_id      VARCHAR(50)  NOT NULL,
  tag_value   VARCHAR(50)  NOT NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

  PRIMARY KEY (device_uuid, tag_id),
  INDEX (tag_id),
  CONSTRAINT fk_device_uuid FOREIGN KEY (device_uuid) REFERENCES Device(uuid)
) CHARACTER SET 'utf8' COLLATE 'utf8_bin';
