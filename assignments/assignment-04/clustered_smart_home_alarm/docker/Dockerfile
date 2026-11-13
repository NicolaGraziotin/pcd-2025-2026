FROM sbtscala/scala-sbt:eclipse-temurin-25.0.1_8_1.12.11_3.8.3 AS builder

WORKDIR /app
COPY . .

# The main class is configured in build.sbt as ass04.Main.
RUN sbt -batch assembly

FROM eclipse-temurin:25.0.4_7-jre

WORKDIR /app
COPY --from=builder /app/target/cluster.jar /app/cluster.jar

ENTRYPOINT ["java", "-jar", "cluster.jar"]
