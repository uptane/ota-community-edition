
ALTER TABLE campaigns
ADD COLUMN status enum('prepared', 'launched', 'finished', 'cancelled') NULL AFTER `update_id`
;