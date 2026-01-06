

alter table key_gen_requests add `key_type` ENUM('RSA','ED25519') NOT NULL DEFAULT 'RSA'
;

alter table key_gen_requests ALTER `key_type` DROP DEFAULT
;

alter table `keys` modify `key_type` ENUM('RSA','ED25519') NOT NULL
;
