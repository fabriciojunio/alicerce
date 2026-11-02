# Duas etapas. A primeira compila; a segunda leva só o JRE e o jar, numa base
# distroless: sem shell, sem gerenciador de pacotes, sem nada que um invasor
# use para se instalar depois de entrar. Bases fixadas por digest, que o
# Dependabot atualiza.

FROM maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS construcao
WORKDIR /construcao
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package && cp target/alicerce-*.jar aplicacao.jar

FROM gcr.io/distroless/java21-debian13:nonroot@sha256:b22857e939bd95abd69d9d747f0421a274f9f70855ecfc335f7d97f6eeef578d
WORKDIR /app
COPY --from=construcao --chown=nonroot:nonroot /construcao/aplicacao.jar aplicacao.jar
# Usuário 65532 da imagem distroless. Roda também com sistema de arquivos
# somente leitura (docker run --read-only): a aplicação não grava em disco.
USER nonroot
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/aplicacao.jar"]
