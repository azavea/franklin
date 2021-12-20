FROM aaronxsu/franklin:latest

WORKDIR /var/lib/franklin

USER root 
RUN echo 'export $(strings /proc/1/environ)' >> /root/.profile

USER franklin

ENTRYPOINT ["java", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-jar", "franklin-api-assembly.jar"]