ALTER TABLE `campaign_stats`
  RENAME TO `group_stats`,
  ADD COLUMN `status` ENUM('scheduled', 'launched', 'cancelled') NOT NULL DEFAULT 'scheduled',
  DROP COLUMN `completed`,
  DROP FOREIGN KEY `campaign_stats`,
  ADD CONSTRAINT `group_stats_campaign_id_fk` FOREIGN KEY (campaign_id) REFERENCES `campaigns` (uuid);

CREATE TABLE `device_updates` (
  `campaign_id` CHAR(36) NOT NULL,
  `update_id` CHAR(36) NOT NULL,
  `device_id` CHAR(36) NOT NULL,
  `status` ENUM('scheduled', 'successful', 'cancelled', 'failed') NOT NULL DEFAULT 'scheduled',

  PRIMARY KEY (campaign_id, device_id),
  CONSTRAINT `device_updates_campaign_id_fk` FOREIGN KEY (campaign_id) REFERENCES `campaigns` (uuid)
);
