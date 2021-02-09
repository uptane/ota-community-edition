UPDATE DeviceSystem SET hostname='' WHERE hostname IS NULL;
UPDATE DeviceSystem SET local_ipv4='' WHERE local_ipv4 IS NULL;
UPDATE DeviceSystem SET mac_address='' WHERE mac_address IS NULL;
