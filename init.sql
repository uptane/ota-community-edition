
create user if not exists user_profile identified by 'user_profile';

CREATE DATABASE IF NOT EXISTS user_profile;

GRANT ALL PRIVILEGES ON `user_profile%`.* TO 'user_profile'@'%';

FLUSH PRIVILEGES;
