CREATE TABLE EcuReplacement (
    device_uuid         CHAR(36)     NOT NULL COLLATE utf8_bin,
    former_ecu_id       CHAR(64)     NOT NULL,
    former_hardware_id  VARCHAR(200) NOT NULL,
    current_ecu_id      CHAR(64)     NOT NULL,
    current_hardware_id VARCHAR(200) NOT NULL,
    replaced_at         DATETIME(3)  NOT NULL DEFAULT current_timestamp(3),

    PRIMARY KEY (device_uuid, former_ecu_id),
    CONSTRAINT fk_ecu_replacement_device FOREIGN KEY (device_uuid) REFERENCES Device(uuid)
);
