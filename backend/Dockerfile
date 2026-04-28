# syntax=docker/dockerfile:1.7
# ----- stage 1: build -----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache parent + module poms first
COPY pom.xml ./
COPY common/pom.xml common/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY product-service/pom.xml product-service/pom.xml
COPY cart-service/pom.xml cart-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY chatbot-service/pom.xml chatbot-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml

# Pre-fetch deps
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests -pl common,api-gateway,auth-service,product-service,cart-service,order-service,payment-service,chatbot-service,notification-service \
        -am dependency:go-offline || true

# Copy sources
COPY common common
COPY api-gateway api-gateway
COPY auth-service auth-service
COPY product-service product-service
COPY cart-service cart-service
COPY order-service order-service
COPY payment-service payment-service
COPY chatbot-service chatbot-service
COPY notification-service notification-service

ARG SERVICE
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests -pl ${SERVICE} -am package

# ----- stage 2: runtime -----
FROM eclipse-temurin:21-jre-alpine
ARG SERVICE
WORKDIR /app
COPY --from=build /workspace/${SERVICE}/target/*.jar /app/app.jar
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
