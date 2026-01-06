ALTER TABLE device_manifests DROP INDEX IF EXISTS device_manifests_device_id_received_at_idx;

ALTER TABLE device_manifests ADD INDEX device_manifests_device_id_received_at_idx (device_id, received_at);
