FROM node:22-bookworm-slim AS frontend-build
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN VITE_API_ROOT=/api npm run build

FROM eclipse-temurin:21-jdk-jammy AS backend-build
WORKDIR /build/backend
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw
COPY backend/src/ src/
COPY --from=frontend-build /build/frontend/dist/ src/main/resources/static/
# Tests run against a dedicated PostgreSQL database before deployment.
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system jobstar && useradd --system --gid jobstar jobstar
COPY --from=backend-build --chown=jobstar:jobstar /build/backend/target/backend-0.0.1-SNAPSHOT.jar app.jar
USER jobstar
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
