ALTER TABLE `updates` MODIFY COLUMN
  `status` Enum('Scheduled', 'Assigned', 'Seen', 'Completed', 'PartiallyCompleted', 'Cancelled') NOT NULL
;