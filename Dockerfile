FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY api/ .
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
# 무료 인스턴스(512MB)에 맞춰 힙을 컨테이너 메모리 비율로 잡는다
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"
EXPOSE 8080
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
