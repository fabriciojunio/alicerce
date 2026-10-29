# Duas etapas. A primeira compila; a segunda leva só o JRE e o jar, numa base
# distroless: sem shell, sem gerenciador de pacotes, sem nada que um invasor
# use para se instalar depois de entrar. Bases fixadas por digest, que o
# Dependabot atualiza.

FROM maven:3.9-eclipse-temurin-26@sha256:b2c1ad85954592f9928e84327c65201f308ad9b5d8ed7d5b823717c97bf23fbb AS construcao
WORKDIR /construcao
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package && cp target/alicerce-*.jar aplicacao.jar

FROM gcr.io/distroless/java21-debian13:nonroot@sha256:a2aeb5a7a714cfe928b31fdf67b2d23f503ea6b9612705af062de22e0c50ba77
WORKDIR /app
COPY --from=construcao --chown=nonroot:nonroot /construcao/aplicacao.jar aplicacao.jar
# Usuário 65532 da imagem distroless. Roda também com sistema de arquivos
# somente leitura (docker run --read-only): a aplicação não grava em disco.
USER nonroot
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/aplicacao.jar"]
