CREATE TABLE `updates` (
  `namespace` varchar(255) NOT NULL,
  `id` char(36) NOT NULL,
  `device_id` char(36) NOT NULL,
  `hardware_update_id` char(36) NOT NULL,
  `correlation_id` varchar(255) NOT NULL,
  `scheduled_for` datetime(3) NULL,
  `status` Enum('Assigned', 'Scheduled', 'Completed', 'PartiallyCompleted', 'Cancelled') NOT NULL,
  `status_info` json DEFAULT NULL,
  `completed_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`, `device_id`),

  CONSTRAINT fk_updates_hardware_update
  FOREIGN KEY (hardware_update_id) REFERENCES hardware_updates (id),
  FOREIGN KEY (device_id) REFERENCES provisioned_devices(id),

  KEY `updates_status_scheduled_for` (`status`,`scheduled_for`),
  KEY `idx_updates_device_id` (`device_id`),
  KEY `idx_updates_namespace` (`namespace`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;

CREATE INDEX `idx_assignments_namespace_correlation_id` ON `assignments` (`namespace`, `correlation_id`);

INSERT INTO `updates` (
  `namespace`, 
  `id`, 
  `device_id`, 
  `hardware_update_id`, 
  `correlation_id`, 
  `scheduled_for`,
  `status`, 
  `status_info`, 
  `completed_at`, 
  `created_at`, 
  `updated_at`
)
SELECT 
  `namespace`, 
  `id`, 
  `device_id`, 
  `hardware_update_id`, 
  CONCAT('urn:here-ota:mtu:', `hardware_update_id`), 
  `scheduled_at`, 
  `status`, 
  `status_info`, 
  CASE 
    WHEN `status` IN ('Completed', 'Cancelled') THEN `updated_at`
    ELSE NULL
  END as `completed_at`, 
  `created_at`, 
  `updated_at`
FROM `scheduled_updates`
ON DUPLICATE KEY UPDATE
  `status` = VALUES(`status`),
  `status_info` = VALUES(`status_info`),
  `completed_at` = CASE 
    WHEN VALUES(`status`) IN ('Completed', 'Cancelled') THEN VALUES(`updated_at`)
    ELSE NULL
  END,
  `updated_at` = VALUES(`updated_at`)
  ;

alter table EcuInstallationResult ADD `description` TEXT NULL
;