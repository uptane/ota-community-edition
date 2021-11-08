CREATE TABLE DeviceInstallationResult (
    correlation_id      VARCHAR(256) NOT NULL,
    result_code         VARCHAR(256) NOT NULL,
    device_uuid         CHAR(36)     NOT NULL COLLATE utf8_bin,
    received_at          DATETIME(3) NOT NULL DEFAULT current_timestamp(3),
    installation_report JSON,

    PRIMARY KEY (correlation_id, device_uuid)
);

CREATE TABLE EcuInstallationResult (
    correlation_id VARCHAR(256) NOT NULL,
    result_code    VARCHAR(256) NOT NULL,
    device_uuid    CHAR(36)     NOT NULL COLLATE utf8_bin,
    ecu_id         VARCHAR(64)  NOT NULL,

    PRIMARY KEY (correlation_id, device_uuid, ecu_id),
    CONSTRAINT fk_ecu_report_device_report FOREIGN KEY (correlation_id, device_uuid) REFERENCES DeviceInstallationResult(correlation_id, device_uuid)
);