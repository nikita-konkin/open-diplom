# Открытый диплом в контейнере — для сервера, где уже есть docker (ADR-0006
# docker не требует, но и не запрещает). Сборка прогоняет все тесты.
#
#   docker build -t open-diplom .
#   docker run -p 8090:8090 -v open-diplom-data:/data open-diplom
#
# Параметры — через JAVA_OPTS, например -Dopendiplom.context-path=/open-diplom.

FROM maven:3.9-eclipse-temurin-11 AS build
# Liberation Serif — метрики Times New Roman: на нём идут тесты печати и PDF-планов
RUN apt-get update \
    && apt-get install -y --no-install-recommends fonts-liberation \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package

FROM eclipse-temurin:11-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends fonts-liberation curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app /data \
    && chown 1000:1000 /data
WORKDIR /app
COPY --from=build /build/target/open-diplom.jar /app/open-diplom.jar
# uid 1000 — пользователь ubuntu образа; папка данных на хосте должна принадлежать тому же uid
USER 1000
ENV JAVA_OPTS="-Xmx128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8090
VOLUME /data
CMD ["sh", "-c", "exec java $JAVA_OPTS -Dopendiplom.mode=server -Dopendiplom.data=/data -Dopendiplom.open-browser=false -jar /app/open-diplom.jar"]
