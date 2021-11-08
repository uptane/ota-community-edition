#!/usr/bin/env python2
# gets variables from vault, add them to json given in stdin

import sys
import json
import urllib2

if __name__ == "__main__":
    if len(sys.argv) is not 3:
        print "usage: add-vault-vars.py <VAULT_ENDPOINT> <VAULT_TOKEN>"
        sys.exit(-1);

    vault_endpoint = sys.argv[1]
    vault_token = sys.argv[2]

    apps = json.load(sys.stdin)

    vault_req = urllib2.Request(vault_endpoint)
    vault_req.add_header('X-Vault-Token', vault_token)
    vault_resp = urllib2.urlopen(vault_req)

    secrets = json.load(vault_resp)["data"]["env"]

    if type(apps) is list:
        for app in apps:
            app["env"].update(secrets)
    else:
        apps["env"].update(secrets)

    print json.dumps(apps, sort_keys=True, indent=4)

