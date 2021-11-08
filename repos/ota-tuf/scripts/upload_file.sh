#!/usr/bin/env bash

set -eo pipefail
IFS=$'\n\t'

function usage {
  echo "usage: $0 -c <credentials.zip> -i <hardware id1>,<hardware id2>,... -t <target name> -v <version> -f <file>"
}

function get_token {
    treehub_creds=$(unzip -qc "$1" treehub.json)
    auth_server=$(jq -r '.oauth2.server' <<<"$treehub_creds")
    client_id=$(jq -r '.oauth2.client_id' <<<"$treehub_creds")
    client_secret=$(jq -r '.oauth2.client_secret' <<<"$treehub_creds")
    scope=$(jq -r '.oauth2.scope' <<<"$treehub_creds")
    curl -s -u "$client_id:$client_secret" "$auth_server/token" -d grant_type=client_credentials -d scope=$scope | jq -r .access_token
}

while getopts ":hc:i:t:v:f:" opt; do
  case $opt in
    h)
      usage
      exit 0
      ;;
    c)
      credentials_path=$OPTARG
      ;;
    i)
      hardware_ids=$OPTARG
      ;;
    t)
      target_name=$OPTARG
      ;;
    v)
      version=$OPTARG
      ;;
    f)
      file=$OPTARG
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      exit 1
      ;;
    :)
      echo "Option -$OPTARG requires an argument." >&2
      exit 1
      ;;
  esac
done

if [ -z "$credentials_path" ] || [ -z "$hardware_ids" ] || [ -z "$target_name" ] || [ -z "$version" ] || [ -z "$file" ]
then
    usage
    exit 1
fi

token=$(get_token "$credentials_path")

if [ -z "$token" ]
then
        echo "Couldn't get token"
        exit 1
fi

reposerver=$(unzip -qc "$credentials_path" tufrepo.url)

curl -o /dev/null -f --max-time 3600 --request PUT --header "application/octet-stream" --header "Authorization: Bearer $token" \
  "${reposerver}/api/v1/user_repo/targets/${target_name}_${version}?name=${target_name}&version=${version}&hardwareIds=${hardware_ids}" --data-binary @"$file"
