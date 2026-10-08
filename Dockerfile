# ==========================================
# Multi-Stage Dockerfile para VideoPost Cloud
# Suporta: Java 21, Python 3, Drive Sync e REST API
# ==========================================

# ----------------- Estágio 1: Build do Java -----------------
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml .
# Baixar dependências para cache
RUN mvn dependency:go-offline -B || true

COPY src ./src
RUN mvn clean package -DskipTests -B

# ----------------- Estágio 2: Ambiente de Produção -----------------
FROM eclipse-temurin:21-jre-jammy

# Instalar Python 3 e utilitários
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    python3-pip \
    python3-dev \
    curl \
    ca-certificates \
    libsqlite3-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copiar dependências Python
COPY cloud/requirements.txt ./cloud/requirements.txt
RUN pip3 install --no-cache-dir --upgrade pip && \
    pip3 install --no-cache-dir -r cloud/requirements.txt

# Copiar JAR compilado do estágio 1
COPY --from=builder /build/target/videopost-cli-*.jar ./target/videopost-cli-1.0.0-SNAPSHOT.jar

# Copiar módulos da aplicação
COPY cloud ./cloud
COPY tiktok-service ./tiktok-service
COPY videos ./videos

# Criar diretórios de trabalho
RUN mkdir -p /app/videos /app/.videopost/logs /app/tiktok-service/logs && \
    chmod +x cloud/entrypoint.sh

# Variáveis padrão
ENV PORT=8000
ENV VIDEOS_DIR=/app/videos
ENV DRIVE_SYNC_INTERVAL_MINUTES=15
ENV PYTHONUNBUFFERED=1

EXPOSE 8000

ENTRYPOINT ["/app/cloud/entrypoint.sh"]
