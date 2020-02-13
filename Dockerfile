FROM openjdk:8-jre
RUN mkdir -p /opt/service
COPY ./target/assembly/service-node.jar /opt/service/
COPY ./target/assembly/service-chain-code.tgz /opt/service/
COPY ./target/assembly/admin-console /opt/service/admin-console

WORKDIR /opt/service/
CMD ["java", "-jar", "/opt/service/service-node.jar"]
