CREATE TABLE `ecu_targets` (
  `namespace` varchar(200) NOT NULL,
  `id` char(36) NOT NULL,
  `filename` varchar(4096) NOT NULL,
  `length` BIGINT NOT NULL,
  `checksum` varchar(254) NOT NULL,
  `sha256` char(64) NOT NULL,
  `uri` varchar(255) NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  PRIMARY KEY (`id`),
  INDEX ecu_targets_file_sha256_idx(namespace, `filename`(500), sha256)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci
;

CREATE TABLE `ecus` (
  `namespace` varchar(200) NOT NULL,
  `ecu_serial` varchar(64) NOT NULL,
  `device_id` char(36) NOT NULL,
  `public_key` varchar(4096) NOT NULL,
  `hardware_identifier` varchar(200) NOT NULL,
  `current_target` CHAR(36) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  INDEX `ecu_namespace_idx` (`namespace`),
  PRIMARY KEY (`device_id`,`ecu_serial`),
  CONSTRAINT `ecu_current_target_fk` FOREIGN KEY (`current_target`) REFERENCES ecu_targets(`id`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci
;

CREATE TABLE `devices` (
  `namespace` varchar(200) NOT NULL,
  `id` char(36) NOT NULL,
  `primary_ecu_id` varchar(64) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  `generated_metadata_outdated` BOOLEAN NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `devices_unique_id` UNIQUE (`id`),
  CONSTRAINT `primary_ecu_fk` FOREIGN KEY (`id`, `primary_ecu_id`) REFERENCES ecus(`device_id`, `ecu_serial`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci
;


CREATE TABLE `signed_roles` (
  `role` enum('ROOT','SNAPSHOT','TARGETS','TIMESTAMP') NOT NULL,
  `version` int(11) NOT NULL,
  `device_id` char(36) NOT NULL,
  `checksum` varchar(254) NULL,
  `length` bigint NULL,
  `content` longtext NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  `expires_at` datetime(3) NOT NULL,
  PRIMARY KEY (`device_id`, `role`,`version`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci
;

CREATE TABLE `hardware_updates` (
  `namespace` varchar(200) NOT NULL,
  `id` char(36) NOT NULL,
  `hardware_identifier` char(200) NOT NULL,
  `to_target_id` char(36) NOT NULL REFERENCES ecu_targets(id),
  `from_target_id` char(36) DEFAULT NULL REFERENCES ecu_targets(id),
  `target_format` enum('BINARY','OSTREE') NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  PRIMARY KEY (`id`,`hardware_identifier`),
  CONSTRAINT `hardware_updates_to_target_fk` FOREIGN KEY (`to_target_id`) REFERENCES ecu_targets(`id`),
  CONSTRAINT `hardware_updates_from_target_fk` FOREIGN KEY (`from_target_id`) REFERENCES ecu_targets(`id`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci
;


CREATE TABLE `repo_namespaces` (
  `namespace` varchar(200) NOT NULL,
  `repo_id` char(36) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  PRIMARY KEY (`namespace`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci
;


CREATE TABLE `assignments` (
  `namespace` varchar(200) NOT NULL,
  `device_id` char(36) NOT NULL,
  `ecu_serial` varchar(64) NOT NULL,
  `ecu_target_id` char(36) NOT NULL,
  `correlation_id` varchar(255) NOT NULL,
  `in_flight` BOOLEAN NOT NULL,

  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),

  CONSTRAINT `assignments_ecu_fk` FOREIGN KEY (`ecu_target_id`) REFERENCES ecu_targets(`id`),
  CONSTRAINT `assignments_ecu_target_fk` FOREIGN KEY (`device_id`, `ecu_serial`) REFERENCES ecus(`device_id`, `ecu_serial`),
  CONSTRAINT `assignments_device_fk` FOREIGN KEY (`device_id`) REFERENCES devices(`id`),

  INDEX `assignments_device_id_idx` (`device_id`),
  INDEX `assignments_ecu_serial_idx` (`ecu_serial`),

  PRIMARY KEY (`device_id`, `ecu_serial`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci
;

CREATE TABLE `processed_assignments` (
  `namespace` varchar(200) NOT NULL,
  `device_id` char(36) NOT NULL,
  `ecu_serial` varchar(64) NOT NULL,
  `ecu_target_id` char(36) NOT NULL,
  `correlation_id` varchar(255) NOT NULL,
  `canceled` BOOLEAN NOT NULL,
  `successful` BOOLEAN NOT NULL,
  `result_desc` TEXT NULL,

  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),

  INDEX `processed_assignments_ns_device_id_idx` (`namespace`, `device_id`),

  CONSTRAINT `p_assignments_ecu_target_fk` FOREIGN KEY (`ecu_target_id`) REFERENCES ecu_targets(`id`),
  CONSTRAINT `p_assignments_ecu_fk` FOREIGN KEY (`device_id`, `ecu_serial`) REFERENCES ecus(`device_id`, `ecu_serial`),
  CONSTRAINT `p_assignments_device_fk` FOREIGN KEY (`device_id`) REFERENCES devices(`id`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci
;

CREATE TABLE `auto_update_definitions` (
  `id` char(36) NOT NULL,
  `namespace` varchar(200) NOT NULL,
  `device_id` char(36) NOT NULL,
  `ecu_serial` varchar(64) NOT NULL,
  `target_name` varchar(255) NOT NULL,
  `deleted` BOOLEAN NOT NULL DEFAULT FALSE,

  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),

  CONSTRAINT `auto_update_definitions_unique_target_name` UNIQUE (`device_id`, `ecu_serial`, `target_name`),

  INDEX `auto_update_definitions_idx_target_name` (`namespace`, `target_name`),
  INDEX `auto_update_definitions_idx_namespace_device_id` (`namespace`, `device_id`),

  PRIMARY KEY (`id`),

  CONSTRAINT `auto_update_definitions_ecu_fk` FOREIGN KEY (`device_id`, `ecu_serial`) REFERENCES ecus(`device_id`, `ecu_serial`),
  CONSTRAINT `auto_update_definitions_assignments_device_fk` FOREIGN KEY (`device_id`) REFERENCES devices(`id`)

) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci
;

CREATE TABLE `device_manifests` (
  `device_id` char(36) NOT NULL,
  `sha256` char(64) NOT NULL,
  `manifest` longtext NOT NULL,
  `received_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  PRIMARY KEY (`device_id`, `sha256`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci
;
