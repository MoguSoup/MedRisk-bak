FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
RUN mkdir -p /root/.m2 \
    && printf '%s\n' \
      '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd"><mirrors><mirror><id>tencent</id><mirrorOf>*</mirrorOf><url>https://mirrors.tencent.com/nexus/repository/maven-public/</url></mirror></mirrors></settings>' \
      > /root/.m2/settings.xml
COPY pom.xml .
RUN mvn -q -s /root/.m2/settings.xml -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -s /root/.m2/settings.xml -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/medrisk-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
