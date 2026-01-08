#!/bin/bash

echo > .proxy

    cp /etc/nginx/conf.d/app.template .temp_conf

if [ "$DISABLE_PROXY" -ne 1 ]
    then
while read p; do
    src="$(cut -d':' -f2 <<<$p)"
    dest="$(cut -d':' -f1 <<<$p)"
  echo "Creating proxy for ${src} → ${dest}";
  echo "location ${src} {" >> .proxy;
  echo "   proxy_pass http://${API_ENDPOINT_HOST}:${API_ENDPOINT_PORT}${dest};" >> .proxy;
  echo "}" >> .proxy;
done <proxy.txt

 sed -i.bak '/___PROXY_CONF___/ r .proxy' .temp_conf
  else
  echo "Internal API proxy is disabled "
    fi
    sed -i 's/___PROXY_CONF___/ /g' .temp_conf

rm -fr /etc/nginx/sites-enabled
rm -fr /etc/nginx/sites-available

# default values
export AWS_IDENTITY_POOL_ID=${AWS_IDENTITY_POOL_ID:-""}
export AWS_USER_POOL_ID=${AWS_USER_POOL_ID:-""}
export AWS_CLIENT_ID=${AWS_CLIENT_ID:-""}
export AWS_REGION=${AWS_REGION:-""}
export GUEST_MODE=${GUEST_MODE:-0}
export LIMITED_ACCESS=${LIMITED_ACCESS:-0}
export USER_POOL=${USER_POOL:-"dev"}
export DEMO_MODE=${DEMO_MODE:-'0'}
export CREDENTIALS_DOWNLOAD_LINK=${CREDENTIALS_DOWNLOAD_LINK:-"/api/accounts/credentials.zip"}
export SUGARCRM_COMMERCIAL_ACCESS_CAMPAIGN_ID=${SUGARCRM_COMMERCIAL_ACCESS_CAMPAIGN_ID:-""}
export SUGARCRM_ONBOARDING_WALKTHROUGH_CAMPAIGN_ID=${SUGARCRM_ONBOARDING_WALKTHROUGH_CAMPAIGN_ID:-""}
export SUGARCRM_NEW_USER_CAMPAIGN_ID=${SUGARCRM_NEW_USER_CAMPAIGN_ID:-""}
export CDN_URL=${CDN_URL:-"cdn.dev.torizon.io"}
APP_DIR="/usr/app"
NGINX_DIR='/usr/share/nginx/html'

replace_env_var(){
    infile=$1
    outfile=$2
    envVars='$___APPLICATION_ENV___'
    for envVar in $(env | cut -d= -f1 | sed -e 's/^//'); do
        if [ "$envVar" != '_' ]; then
            cleaned="${!envVar%\'}" # Removes traling single quote
            cleaned="${cleaned#\'}" # Removes leading single quote
            cleaned="${cleaned%\"}" # Removes traling double quote
            cleaned="${cleaned#\"}" # Removes leading double quote
            export ${envVar}=$cleaned
            envVars="${envVars},\$${envVar}"
        fi

    done
    envsubst $envVars < $infile > $outfile
}

walk_dir () {
    shopt -s nullglob dotglob

    for pathname in "$1"/*; do
        if [ -d "$pathname" ]; then
            walk_dir "$pathname"
        else
            # printf '%s\n' "$pathname" # /usr/share/nginx/html
            dest="${pathname/$APP_DIR/$NGINX_DIR}"
            mkdir -p "$(dirname "${dest}")"
            replace_env_var  "$pathname"  $dest
            # printf 'SRC: %s --- DEST: %s\n' "$pathname" "$dest" # /usr/share/nginx/html
        fi
    done
}

walk_dir "$APP_DIR"
sed -i "s#src=https://CDN_URL/js#src=https://$CDN_URL/js#g" /usr/share/nginx/html/index.html
echo "$(env | cut -d= -f1 | sed -e 's/^/$/')"
 replace_env_var  '.temp_conf' '/etc/nginx/conf.d/default.conf' && cat /etc/nginx/conf.d/default.conf && exec nginx -g 'daemon off;'
