# =========================================================
# 1. Build stage
# =========================================================

FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace


# Gradle Wrapper와 build configuration을 먼저 복사한다.
#
# source code보다 변경 빈도가 낮기 때문에
# Docker layer cache를 최대한 활용하기 위함이다.
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle .
COPY settings.gradle .


RUN chmod +x gradlew


# Dependency resolution layer.
#
# source code가 바뀌더라도 build.gradle 등이 바뀌지 않았다면
# dependency 다운로드 layer를 재사용할 수 있다.
RUN ./gradlew dependencies \
    --no-daemon


# 실제 application source
COPY src ./src


# 테스트는 image build 단계에서는 제외한다.
#
# 테스트는 CI 단계에서 별도로 수행하고,
# image build는 이미 검증된 source를 packaging하는 역할로 분리한다.
RUN ./gradlew bootJar \
    --no-daemon \
    -x test \
    && JAR_FILE="$(find build/libs \
        -maxdepth 1 \
        -type f \
        -name '*.jar' \
        ! -name '*-plain.jar' \
        | head -n 1)" \
    && test -n "$JAR_FILE" \
    && cp "$JAR_FILE" /workspace/app.jar


# =========================================================
# 2. Runtime stage
# =========================================================

FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app


# container 안에서 root 권한으로 application을 실행하지 않는다.
RUN groupadd \
        --system \
        app \
    && useradd \
        --system \
        --gid app \
        --home-dir /app \
        --shell /usr/sbin/nologin \
        app


COPY --from=build \
    --chown=app:app \
    /workspace/app.jar \
    /app/app.jar


USER app:app


EXPOSE 8080


ENTRYPOINT ["java", "-jar", "/app/app.jar"]