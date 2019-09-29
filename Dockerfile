FROM openjdk:8-jre
RUN mkdir -p /opt/service
COPY ./service-node/build/libs/service-node.jar /opt/service/
COPY ./service-chain-code/service-chain-code.tgz /opt/service/
RUN mkdir -p /opt/service/admin-console
COPY ./admin-console/index.html /opt/service/admin-console/
COPY ./admin-console/favicon.ico /opt/service/admin-console/
COPY ./admin-console/scripts /opt/service/admin-console/scripts/

WORKDIR /opt/service/
CMD ["java", "-jar", "/opt/service/service-node.jar"]