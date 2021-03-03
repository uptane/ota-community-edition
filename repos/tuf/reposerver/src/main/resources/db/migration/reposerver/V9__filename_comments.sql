create table `filename_comments` (
  `repo_id` char(36) NOT NULL,
  `filename` VARCHAR(254) NOT NULL,
  `comment` TEXT NOT NULL,
  primary key(`repo_id`,`filename`),
  constraint target_item_fk foreign key(`repo_id`,`filename`) references target_items(`repo_id`,`filename`) on delete CASCADE
);
