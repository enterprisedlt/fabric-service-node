FROM openjdk:8-jre
RUN mkdir -p /opt/service/
COPY ./service-node/build/libs/service-node.jar /opt/service/
COPY ./service-chain-code/service-chain-code.tgz /opt/service/
WORKDIR /opt/service/
CMD ["java", "-jar", "/opt/service/service-node.jar"]