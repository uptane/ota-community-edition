# File upload script

This directory contains a script called `upload_file.sh` which can be
used to upload a target file to the Uptane image ("repo") server.

## Software requirements

The upload script is implemented as a Bash shell script. In addition
to `bash` it requires the utilities `unzip`, `curl` and `jq`.

## Credentials

In order to use the script you need provisioning credentials
(`credentials.zip`) downloaded from _Here OTA Connect_.

## Usage

Running the script as `upload_file.sh -h` shows the usage:

    upload_file.sh -c <credentials.zip> -i <hardware id1>,<hardware id2>,... -t <target name> -v <version> -f <file>

- `<credentials.zip>` is the path to the (provisioning) credentials file

- `<hardware id1>`,`<hardware id2>`,... is one or more hardware ids this file can be installed on

- `<target name>` is a name for the target file by which you can identify it in the UI

- `<version>` is the version of the target software, like "1.3.5".

- `<file>` is the path to the binary file to upload
