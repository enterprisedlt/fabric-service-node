#!/bin/bash
set -e

ENV_CONFIG=$1
TARGET_DIR=$2

if [[ ! -f "${ENV_CONFIG}" ]]
then
  echo "Config does not exist!"
  exit 1
fi

rm -rf ${TARGET_DIR}/shared
mkdir -p ${TARGET_DIR}/shared
cat ${ENV_CONFIG} | awk -v TARGET_DIR="${TARGET_DIR}" -F, '
    {
        if (NR==1) {
            for (i = 1; i < NF + 1; i++){
                header[i] = $i
            }
        } else {
            org_name=$1
            org_path=sprintf("%s/%s", TARGET_DIR, org_name)

            mkd="mkdir -p " org_path
            system(mkd)
            close(mkd)

            settings_file = sprintf("%s/settings", org_path)
            print "#!/bin/sh" > settings_file
            for (i = 1; i < NF + 1; i++){
                printf("export %s=%s\n", header[i], $i) >> settings_file
            }
            close(settings_file)

            org_list_file = sprintf("%s/shared/list", TARGET_DIR)
            print org_name >> org_list_file
            close(org_list_file)
        }
    }
'

PORT=7000
for ORG in $(cat ${TARGET_DIR}/shared/list); do
  . ${TARGET_DIR}/${ORG}/settings
  cat >${TARGET_DIR}/${ORG}/components.json <<EOL
{
  "network": {
    "orderingNodes": [
      {
        "name": "osn1.${ORG}.${DOMAIN}",
        "port": $((PORT + 1)),
        "box": "default"
      },
      {
        "name": "osn2.${ORG}.${DOMAIN}",
        "port": $((PORT + 2)),
        "box": "default"
      },
      {
        "name": "osn3.${ORG}.${DOMAIN}",
        "port": $((PORT + 3)),
        "box": "default"
      }
    ],
    "peerNodes": [
      {
        "name": "peer0.${ORG}.${DOMAIN}",
        "port": $((PORT + 4)),
        "box": "default"
        "couchDB": {
          "port": $((PORT + 5))
        }
      }
    ]
  }
}
EOL
  PORT=$((PORT + 10))
  echo "" >${TARGET_DIR}/${ORG}/hosts
done

echo "Done"
