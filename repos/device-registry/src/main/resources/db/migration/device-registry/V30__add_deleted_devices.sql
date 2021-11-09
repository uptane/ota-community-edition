CREATE TABLE DeletedDevice (
    namespace CHAR(200) NOT NULL,
    device_uuid char(36) NOT NULL,
    device_id varchar(200) NOT NULL,
    `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
    PRIMARY KEY(namespace, device_uuid, device_id)
);

