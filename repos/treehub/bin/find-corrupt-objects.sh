#!/usr/bin/env bash

set -eo pipefail

function usage {
  echo "  usage: $0 -c <credentials.zip> -r <repo path>"
  echo ""
  echo "     -c <credentials.zip>     Location of your credentials.zip file"
  echo "     -r <repo path>           Path where your local OSTree repo will be"
  echo "                              created. A local repo from a previous run"
  echo "                              of this tool can be re-used only if it's"
  echo "                              from the same account."
  echo ""
  echo "  Prerequisites: ostree, curl, and jq"
}

function get_token {
    treehub_creds=$(unzip -qc "$1" treehub.json)
    auth_server=$(jq -r '.oauth2.server' <<<"$treehub_creds")
    treehub_server=$(jq -r '.ostree.server' <<<"$treehub_creds")
    client_id=$(jq -r '.oauth2.client_id' <<<"$treehub_creds")
    client_secret=$(jq -r '.oauth2.client_secret' <<<"$treehub_creds")
    curl -s -u "$client_id:$client_secret" "$auth_server"/token -d grant_type=client_credentials | jq -r .access_token
}

while getopts ":hc:r:" opt; do
  case $opt in
    h)
      usage
      exit 0
      ;;
    c)
      credentials_path=$OPTARG
      ;;
    r)
      repo_name=$OPTARG
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

if [ -z "$credentials_path" ] || [ -z "$repo_name" ]
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
treehub_server=$(unzip -qc "$credentials_path" treehub.json | jq -r '.ostree.server')

ostree_commits=$(curl -s --header "Authorization: Bearer ${token}" ${reposerver}/api/v1/user_repo/targets.json | jq -r '.signed.targets[] |select(.custom.targetFormat == "OSTREE")|.hashes.sha256' | tr '\n' ' ' )
logfile="corrupt-objects-${repo_name}-$(date -uI'seconds')"

ostree init --mode archive --repo="${repo_name}"
ostree remote add --repo="${repo_name}" --no-gpg-verify treehub ${treehub_server} 2>/dev/null || true

echo ""
echo "Pulling commits..."
echo ""
ostree pull --repo="${repo_name}" --http-trusted --mirror --http-header Authorization="Bearer ${token}" treehub ${ostree_commits}

echo ""
echo "Checking objects..."
echo ""
if ostree fsck --repo="${repo_name}" -a 2>"${logfile}"; then
  echo ""
  echo "  No corrupt objects found!"
  rm ${logfile}
else
  echo ""
  echo "$(cat ${logfile} | wc -l) corrupt objects found. Details logged to ${logfile}"
fi

