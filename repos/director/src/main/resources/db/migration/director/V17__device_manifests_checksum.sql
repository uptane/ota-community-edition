
ALTER TABLE device_manifests CHANGE COLUMN sha256 checksum char(8)
;

CREATE INDEX device_manifests_received_at ON device_manifests(device_id, received_at)
;
