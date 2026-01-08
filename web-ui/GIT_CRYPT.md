# Instructions On Using Git-Crypt

When you clone this repo for the first time you will not be able to view/use the content on ./src/secrets because they are encrypted.

To decrypt these files, follow the steps below:

1. Install GPG https://help.github.com/en/github/authenticating-to-github/checking-for-existing-gpg-keys 2.[ Generate new GPG key](https://help.github.com/en/articles/generating-a-new-gpg-key) if you don't already have one.
2. Request that your GPG user id be added to the git-crypt by someone that already has access to the git-crypt.
3. Once it's added, execute `git-crypt unlock`

You should now have access to the encrypted files
