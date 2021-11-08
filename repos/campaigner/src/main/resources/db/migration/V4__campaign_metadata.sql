CREATE TABLE `campaign_metadata`(
  `campaign_id` CHAR(36) NOT NULL REFERENCES `campaigns` (`campaign_id`),
  `type` ENUM('install') NOT NULL,
  `value` TEXT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (campaign_id, type)
);
