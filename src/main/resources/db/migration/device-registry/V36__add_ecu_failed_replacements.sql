-- Need to drop the PK because former_ecu_id becomes nullable,
-- and we have to temporarily drop the FK first to drop the PK.
ALTER TABLE EcuReplacement
DROP FOREIGN KEY fk_ecu_replacement_device,
DROP PRIMARY KEY;

ALTER TABLE EcuReplacement
ADD CONSTRAINT fk_ecu_replacement_device FOREIGN KEY (device_uuid) REFERENCES Device(uuid) ON DELETE CASCADE;

ALTER TABLE EcuReplacement
MODIFY COLUMN former_ecu_id       CHAR(64)     NULL,
MODIFY COLUMN former_hardware_id  VARCHAR(200) NULL,
MODIFY COLUMN current_ecu_id      CHAR(64)     NULL,
MODIFY COLUMN current_hardware_id VARCHAR(200) NULL,
ADD    COLUMN success             BOOLEAN      NOT NULL;
