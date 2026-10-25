FROM eclipse-temurin:21-jre

WORKDIR /app

COPY skipbo-multiplayer.jar skipbo-multiplayer.jar

EXPOSE 7070

ENTRYPOINT ["java", "-jar", "skipbo-multiplayer.jar"]
