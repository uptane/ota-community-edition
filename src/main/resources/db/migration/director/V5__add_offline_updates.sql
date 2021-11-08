RENAME TABLE `signed_roles` TO `device_roles`
;

CREATE TABLE `admin_roles` (
  `repo_id` char(36) NOT NULL,
  `name` varchar(255) NOT NULL,
  `role` ENUM('OFFLINE-UPDATES', 'OFFLINE-SNAPSHOT') NOT NULL,
  `version` int(11) NOT NULL,
  `content` longtext NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  `expires_at` datetime(3) NOT NULL,
  `checksum` varchar(254) NOT NULL,
  `length` bigint NOT NULL,
  PRIMARY KEY (`repo_id`, `name`, `version`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci
;
