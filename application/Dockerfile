FROM openjdk:8-jre-slim

RUN \
      addgroup --system franklin \
      && adduser --system --disabled-login --disabled-password \
      --home /var/lib/franklin \
      --shell /sbin/nologin \
      --ingroup franklin \
      -u 1000 \
      franklin

COPY ./application/target/scala-2.12/franklin-api-assembly.jar /var/lib/franklin/

USER franklin
WORKDIR /var/lib/franklin

ENTRYPOINT ["java", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-jar", "franklin-api-assembly.jar"]
