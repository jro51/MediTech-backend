# ---------- Etapa 1: compilar con Maven (no hace falta tener Maven instalado) ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# ---------- Etapa 2: imagen final liviana, solo con el JRE ----------
FROM eclipse-temurin:17-jre-alpine
# curl lo usan los healthchecks de docker-compose
RUN apk add --no-cache curl \
 && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
USER app
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
