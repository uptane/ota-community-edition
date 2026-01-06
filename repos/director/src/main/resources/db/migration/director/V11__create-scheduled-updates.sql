CREATE TABLE `scheduled_updates` (
  `namespace` varchar(200) NOT NULL,
  `id` char(36) NOT NULL,
  `device_id` char(36) NOT NULL,
  `hardware_update_id` char(36) NOT NULL,
  `scheduled_at` datetime(3) NOT NULL,
  `status` Enum('Scheduled', 'Assigned', 'Completed', 'PartiallyCompleted', 'Cancelled'),
  `status_info` JSON,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),

  CONSTRAINT `fk_hardware_update_id`
    FOREIGN KEY (hardware_update_id) REFERENCES hardware_updates(id),

  PRIMARY KEY (`id`),
  INDEX scheduled_updates_ns_device_id(namespace, `device_id`),
  INDEX scheduled_updates_status_scheduled_at(`status`, `scheduled_at`)
) DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci
;
