FROM node:22-alpine AS web
ARG VITE_CVCS_MODE=server
ENV VITE_CVCS_MODE=$VITE_CVCS_MODE
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.12-eclipse-temurin-25 AS java
WORKDIR /build
COPY pom.xml ./
COPY vcs-core/ vcs-core/
COPY vcs-cli/ vcs-cli/
COPY vcs-server/ vcs-server/
COPY --from=web /build/frontend/dist/ vcs-server/src/main/resources/static/
RUN mvn -B package -DskipTests

FROM eclipse-temurin:25-jre
RUN groupadd --system cvcs && useradd --system --gid cvcs --home-dir /app cvcs
WORKDIR /app
COPY --from=java /build/vcs-server/target/vcs-server-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p /app/data/repositories && chown -R cvcs:cvcs /app
USER cvcs
ENV CVCS_STORAGE_ROOT=/app/data/repositories
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
