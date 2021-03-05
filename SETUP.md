# Setup instructions

## Инструкция по развертыванию

### На новой машине

```bash
# 0. Important vars
MY_ORG="EXAMPLE LLS"
MY_DOMAIN="example.com"
PROJECT_DIR="fabric-service-node-scripts"
WEB_ADMIN_HOST="localhost"
CERT_FILE="admin.p12"

# 1. Autopilot from here

curl -JLs https://bit.ly/2WJxs30 | tar xvz -C ${PROJECT_DIR}

#PATH=$PATH:$PWD/fabric-service-node-scripts # doesn't work as intended

bash ${PROJECT_DIR}/preprequisites.sh

touch hosts
touch settings

export ORG=${MY_ORG}
export DOMAIN=${MY_DOMAIN}
export SERVICE_EXTERNAL_ADDRESS=${WEB_ADMIN_HOST}
export SERVICE_BIND_PORT=8000

bash ${PROJECT_DIR}/fabric-service-start.sh .

# Get client cert here
FILE=/etc/redhat-release
if test -f "$FILE"
then
    touch ${CERT_FILE} && chmod 755 ${CERT_FILE}
    bash ${PROJECT_DIR}/fabric-service-get-user-key-docker.sh . admin admin ${PROFILE_PATH}/${CERT_FILE}
else
    bash ${PROJECT_DIR}/fabric-service-get-user-key.sh . admin admin ${CERT_FILE}
fi



```

Теперь можно заходить в веб консоль на хосте и запускать сам Хайперледжер.

https://localhost:8000/admin-console - web UI

Для доступа нужно загрузить в браузер клиентский серт - `admin.p12` в текущей папке. С доверием самоподписанному ЦА у Хрома будут проблемы, т.к. в DNS.altnames не проставлен. Проще использовать firefox

### Примечание

На CentOS проблемы из-за старого курла, ядер и прочей стабильной фигни, которую так любит этот дистрибутив.

### После этого в интерфейсе

#### Для иннициации

WIP

#### Для присоединения

1. присоединиться
2. указываем файл invite.json
3. указываем 3 ноды и пиру
4. join
