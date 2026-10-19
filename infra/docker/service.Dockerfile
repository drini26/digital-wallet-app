# One Dockerfile for every backend service, built from the repository root so the parent POM and

# Stage 1: build the jar with Maven (JDK image).
FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /workspace
COPY mvnw pom.xml lombok.config ./
COPY .mvn .mvn
COPY wallet-common/pom.xml wallet-common/
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/
COPY ledger-service/pom.xml ledger-service/
COPY banking-service/pom.xml banking-service/
COPY wallet-common/src wallet-common/src
COPY ${MODULE}/src ${MODULE}/src
RUN --mount=type=cache,target=/root/.m2 \
    sed -i 's/\r$//' mvnw && chmod +x mvnw \
 && ./mvnw -B -q -pl ${MODULE} -am package -DskipTests -Djacoco.skip=true \
 && cp ${MODULE}/target/${MODULE}-*.jar /workspace/app.jar

# Stage 2: run the jar on a small JRE image as a non-root user.
FROM eclipse-temurin:21-jre-alpine
ARG PORT
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app
ENV PORT=${PORT} \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE ${PORT}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
