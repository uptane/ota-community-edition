CREATE TABLE `campaign_cancels`(
  `campaign_id` CHAR(36) NOT NULL,
  `status` ENUM('error', 'pending', 'inprogress', 'completed') NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (campaign_id)
);
