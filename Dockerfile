# Imagem oficial Microsoft: JDK + Maven + Chromium/Firefox/WebKit e dependências de SO
FROM mcr.microsoft.com/playwright/java:v1.49.0-noble

WORKDIR /app

# Cache de dependências separado do código
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q test-compile

ENV HEADLESS=true \
    EVIDENCE_DIR=/app/target/evidence

# Sobrescreva com: docker run ... -Dgroups=negative
ENTRYPOINT ["mvn", "-B", "test"]
CMD ["-Dgroups=e2e"]
