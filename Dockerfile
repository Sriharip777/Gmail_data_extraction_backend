FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
    "-XX:+UseSerialGC", \
    "-Xss512k", \
    "-XX:MaxRAM=256m", \
    "-Dserver.address=0.0.0.0", \
    "-Dserver.port=${PORT:-8080}", \
    "-jar", "app.jar"]
