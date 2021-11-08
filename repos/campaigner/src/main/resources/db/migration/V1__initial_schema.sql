ALTER DATABASE CHARACTER SET utf8 COLLATE utf8_unicode_ci;

CREATE TABLE `campaigns` (
  `namespace` CHAR(200) NOT NULL,
  `uuid` CHAR(36) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `update_id` CHAR(36) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

   PRIMARY KEY (uuid)
);

CREATE UNIQUE INDEX `campaigns_unique_name` ON
  `campaigns` (`namespace`, `name`)
;

CREATE TABLE `campaign_groups` (
  `campaign_id` CHAR(36) NOT NULL,
  `group_id` CHAR(36) NOT NULL,

  PRIMARY KEY (campaign_id, group_id),
  CONSTRAINT `campaign_groups_campaign_id_fk` FOREIGN KEY (campaign_id) REFERENCES `campaigns` (uuid)
);

CREATE TABLE `campaign_stats` (
  `campaign_id` CHAR(36) NOT NULL,
  `group_id` CHAR(36) NOT NULL,
  `completed` BOOLEAN NOT NULL,
  `processed` BIGINT UNSIGNED NOT NULL,
  `affected` BIGINT UNSIGNED NOT NULL,

  PRIMARY KEY (campaign_id, group_id),
  CONSTRAINT `campaign_stats` FOREIGN KEY (campaign_id) REFERENCES `campaigns` (uuid)
);
