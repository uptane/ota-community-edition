CREATE TABLE `updates` (
  `uuid` CHAR(36) NOT NULL,
  `update_id` VARCHAR(200),
  `update_source_type` ENUM('multi_target', 'external') NOT NULL,
  `namespace` CHAR(200) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `description` VARCHAR(600),
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

   PRIMARY KEY (`uuid`),
   UNIQUE KEY `unique_update_id` (`namespace`, `update_id`)
);