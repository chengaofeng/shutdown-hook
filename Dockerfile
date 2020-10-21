FROM openjdk:8u212-jdk-alpine

ADD spring-boot-shutdownhook-1.0-SNAPSHOT.jar /home

COPY docker-entrypoint.sh /home/docker-entrypoint.sh
WORKDIR /home

RUN chmod +x docker-entrypoint.sh
ENTRYPOINT ["./docker-entrypoint.sh"]