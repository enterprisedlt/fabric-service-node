#!/bin/bash

PROFILE_PATH=${1:-.};
if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "${PROFILE_PATH}");
else
    PROFILE_PATH=$(readlink -f "${PROFILE_PATH}");
fi
ENV_CONFIG="${PROFILE_PATH}/config.csv";

if [[ ! -f ${ENV_CONFIG} ]]
then
  echo "Profile config does not exist!"
  exit 1
fi


for i in `ls ${PROFILE_PATH} | grep -v 'config.csv'`; do rm -rfv "${PROFILE_PATH}/${i}"; done

mkdir -p ${PROFILE_PATH}/shared
cat ${ENV_CONFIG} | awk -v PROFILE_PATH="${PROFILE_PATH}" -F, '
    {
        if (NR==1) {
            for (i = 1; i < NF + 1; i++){
                header[i] = $i
            }
        } else {
            org_name=$1
            org_path=sprintf("%s/%s", PROFILE_PATH, org_name)

            mkd="mkdir -p " org_path
            system(mkd)
            close(mkd)

            settings_file = sprintf("%s/settings", org_path)
            print "#!/bin/sh" > settings_file
            for (i = 1; i < NF + 1; i++){
                printf("export %s=%s\n", header[i], $i) >> settings_file
            }
            close(settings_file)

            org_list_file = sprintf("%s/shared/list", PROFILE_PATH)
            print org_name >> org_list_file
            close(org_list_file)
        }
    }
'


PORT=7000;
for ORG in `cat ${PROFILE_PATH}/shared/list`
do
cat > ${PROFILE_PATH}/${ORG}/components.json << EOL
{
  "network": {
    "orderingNodes": [
      {
        "name": "osn1",
        "port": $((PORT+1))
      },
      {
        "name": "osn2",
        "port": $((PORT+2))
      },
      {
        "name": "osn3",
        "port": $((PORT+3))
      }
    ],
    "peerNodes": [
      {
        "name": "peer0",
        "port": $((PORT+4)),
        "couchDB": {
          "port": $((PORT+5))
        }
      }
    ]
  }
}
EOL
PORT=$((PORT+10));
echo "" > ${PROFILE_PATH}/${ORG}/hosts;
done

echo "Done"
