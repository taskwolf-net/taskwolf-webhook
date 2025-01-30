FROM openjdk:21

COPY /build/libs/webhook-1.0.0-SNAPSHOT.jar webhook.jar
COPY /locale/ /locale/