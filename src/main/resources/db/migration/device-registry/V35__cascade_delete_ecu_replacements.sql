ALTER TABLE EcuReplacement
DROP FOREIGN KEY fk_ecu_replacement_device;

ALTER TABLE EcuReplacement
ADD CONSTRAINT fk_ecu_replacement_device FOREIGN KEY (device_uuid) REFERENCES Device(uuid) ON DELETE CASCADE;
