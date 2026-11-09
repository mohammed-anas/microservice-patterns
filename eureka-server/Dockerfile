FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,id=ecommerce-m2,sharing=locked,target=/root/.m2 \
    mvn -B -DskipTests package \
    && find target -maxdepth 1 -type f -name '*.jar' ! -name '*original*' -exec cp {} /src/app.jar \;

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /src/app.jar /app/app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
