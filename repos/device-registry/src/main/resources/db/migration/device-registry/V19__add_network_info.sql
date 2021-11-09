ALTER TABLE DeviceSystem
ADD COLUMN local_ipv4 CHAR(15),
ADD COLUMN mac_address CHAR(17),
ADD COLUMN hostname VARCHAR(255),
MODIFY COLUMN system_info longtext COLLATE utf8_unicode_ci DEFAULT "{}" ;