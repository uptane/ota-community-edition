ALTER TABLE `static_deltas`
CHANGE `status` `status`
ENUM('uploading', 'available', 'deleted') NOT NULL
;