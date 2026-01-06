-- alter the table by adding `from` column
ALTER TABLE `static_deltas`
ADD `from` char(64) NOT NULL DEFAULT (0)
;

-- update `from` values for existing columns, computing them from the `id` value
UPDATE `static_deltas` SET `from` = LOWER(HEX(FROM_BASE64(RPAD(REPLACE(SUBSTRING_INDEX(`id`, '-', 1), '_', '/'), 44,  '='))))
WHERE `from` = 0
;

-- remove default value from `from` column
ALTER TABLE `static_deltas` ALTER `from` DROP DEFAULT
;

CREATE INDEX `idx_static_deltas_namespace_from` on `static_deltas` (`namespace`, `from`)
;
