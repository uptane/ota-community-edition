ALTER DATABASE CHARACTER SET utf8 COLLATE utf8_unicode_ci;

create table `target_items` (
  `repo_id` char(36) NOT NULL,
  `filename` VARCHAR(254) NOT NULL,
  `uri` varchar(254) NOT NULL,
  `checksum` varchar(254) NOT NULL,
  `length` BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  primary key(`repo_id`,`filename`)
)
;

create table `signed_roles` (
  `repo_id` char(36) NOT NULL,
  `role_type` ENUM('ROOT', 'SNAPSHOT', 'TARGETS', 'TIMESTAMP') NOT NULL,
  `checksum` varchar(254) NOT NULL,
  `content` TEXT NOT NULL,
  `length` BIGINT NOT NULL,
  `version` INT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  primary key(`repo_id`,`role_type`)
)
;

