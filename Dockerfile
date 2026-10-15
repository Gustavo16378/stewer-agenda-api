# =====================================================================
# Stewer Agenda API — imagem de producao
#
# Alvo: Render Web Service do plano gratuito, 512 MB de RAM e 0,1 de CPU.
# Tudo aqui existe por causa desse teto.
#
#   docker build -t stewer-agenda-api .
#   docker run --rm -p 8080:8080 --memory=512m --cpus=0.1 \
#     -e DATABASE_URL=... -e JWT_SECRET=... stewer-agenda-api
#
# O build acontece DENTRO da imagem: o Render clona o repositorio e chama
# `docker build`, sem Maven na maquina dele.
# =====================================================================

# ---------------------------------------------------------------------
# 1. build
# ---------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /projeto

# O pom sozinho primeiro: enquanto ele nao mudar, o Docker reaproveita a
# camada com o repositorio Maven inteiro e o build pula o download.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
# -DskipTests porque quem roda a suite e o CI, contra um Postgres de verdade.
# Repetir os testes aqui exigiria Docker dentro do build (Testcontainers).
RUN mvn -B -q clean package -DskipTests

# ---------------------------------------------------------------------
# 2. runtime
#
# JRE e nao JDK: o compilador nao serve para nada em producao e sao ~100 MB
# a menos de imagem. Alpine pelo mesmo motivo.
# ---------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Usuario sem privilegio: se a aplicacao for comprometida, o invasor nao e root
# dentro do container.
RUN addgroup -S stewer && adduser -S stewer -G stewer

WORKDIR /app

# O quarkus-app vem em camadas, da que menos muda para a que mais muda. Em
# cada deploy so a ultima costuma ser reenviada.
COPY --from=build --chown=stewer:stewer /projeto/target/quarkus-app/lib/ ./lib/
COPY --from=build --chown=stewer:stewer /projeto/target/quarkus-app/quarkus/ ./quarkus/
COPY --from=build --chown=stewer:stewer /projeto/target/quarkus-app/app/ ./app/
COPY --from=build --chown=stewer:stewer /projeto/target/quarkus-app/quarkus-run.jar ./

USER stewer

# ---------------------------------------------------------------------
# Memoria e CPU
#
# Medido com --memory=512m --cpus=0.1 (ver RELATORIO-DEPLOY.md):
#
#   MaxRAMPercentage=55   heap de ~281 MB. O resto dos 512 MB nao sobra: vai
#                         para metaspace, code cache, pilhas e buffers diretos
#                         do Netty. Com 75% o container era morto por OOM sob
#                         rajada.
#   UseSerialGC           com 0,1 de CPU nao ha paralelismo para aproveitar.
#                         G1 reserva memoria e threads que aqui so atrapalham.
#   MaxMetaspaceSize      teto explicito: sem ele o metaspace cresce ate o
#                         limite do container e quem morre e o processo todo.
#   ReservedCodeCacheSize 64 MB sobra para uma API deste tamanho.
#   TieredStopAtLevel=1   so o compilador C1. O C2 custa CPU que nao temos, e
#                         o ganho de pico nao aparece num servico com este
#                         volume. Derruba o tempo de subida.
#   Xss512k               as threads do Netty nao precisam de 1 MB cada.
#   AlwaysActAsServerClassMachine  sem isto a JVM se ve como maquina pequena e
#                         escolhe defaults de cliente.
# ---------------------------------------------------------------------
ENV JAVA_TOOL_OPTIONS="\
-XX:MaxRAMPercentage=55 \
-XX:+UseSerialGC \
-XX:MaxMetaspaceSize=128m \
-XX:ReservedCodeCacheSize=64m \
-XX:TieredStopAtLevel=1 \
-Xss512k \
-XX:+AlwaysActAsServerClassMachine \
-Dfile.encoding=UTF-8 \
-Djava.security.egd=file:/dev/urandom"

# O Render injeta PORT; application.properties le `${PORT:8080}` em %prod.
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
