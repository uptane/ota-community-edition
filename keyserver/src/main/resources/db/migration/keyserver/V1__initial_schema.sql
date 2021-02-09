ALTER DATABASE CHARACTER SET utf8 COLLATE utf8_unicode_ci;

create table `roles` (
  `role_id` CHAR(36) NOT NULL PRIMARY KEY,
  `repo_id` CHAR(36) NOT NULL,
  `role_type` ENUM('ROOT', 'SNAPSHOT', 'TARGETS', 'TIMESTAMP') NOT NULL,
  `threshold` INTEGER NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT roles_unique_idx UNIQUE (`role_type`, `repo_id`)
)
;

CREATE TABLE `key_gen_requests` (
  `id` char(36) NOT NULL,
  `repo_id` char(36) NOT NULL,
  `status` ENUM('REQUESTED', 'GENERATED', 'ERROR') NOT NULL,
  `role_type` ENUM('ROOT', 'SNAPSHOT', 'TARGETS', 'TIMESTAMP') NOT NULL,
  `key_size` INTEGER NOT NULL,
  `threshold` INTEGER NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT key_gen_requests_unique_idx UNIQUE (`role_type`, `repo_id`),
  PRIMARY KEY (`id`)
)
;

CREATE TABLE `keys` (
  `key_id` varchar(254) NOT NULL,
  `role_id` char(36) NOT NULL,
  `key_type` ENUM('RSA') NOT NULL,
  public_key TEXT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (`role_id`) REFERENCES roles(`role_id`),
  PRIMARY KEY (`key_id`)
)
;

create table `repo_roles_keys` (
  `repo_id` char(36) NOT NULL,
  `key_id` VARCHAR(254) NOT NULL,
  `role_type` ENUM('ROOT', 'SNAPSHOT', 'TARGETS', 'TIMESTAMP') NOT NULL,
  `threshold` INTEGER NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`repo_id`, `role_type`)
)
;

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
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  primary key(`repo_id`,`role_type`)
)
;
