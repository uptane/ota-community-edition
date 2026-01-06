CREATE TABLE `delegations` (
  `repo_id` char(36) NOT NULL,
  `name`  varchar(50) NOT NULL,
  `content` longtext NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  PRIMARY KEY (`repo_id`,`name`)
)
