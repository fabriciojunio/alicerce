# Respostas ao questionário de segurança de cliente

Clientes corporativos mandam questionários de segurança antes de contratar (no formato CAIQ, SIG Lite
ou próprio). Estas são as respostas para um serviço criado a partir do alicerce, cada uma com a
evidência que o cliente pode pedir. Onde a resposta depende da implantação, está escrito.

| Domínio | Pergunta | Resposta | Evidência |
|---|---|---|---|
| Autenticação | Como os usuários se autenticam? | Por OAuth 2.0 / OpenID Connect, com token JWT emitido por provedor de identidade (Keycloak). O serviço não guarda senha. | `ConfiguracaoDeSeguranca.java`, `infra/realm-alicerce.json` |
| Autenticação | O token é validado? | Assinatura, emissor (`iss`), audiência (`aud`) e validade (`exp`), pelo Spring Security a partir da chave pública do emissor. | `application.yml`. Os testes simulam o token já validado; a validação em si é do Spring Security |
| Autorização | Como o acesso aos dados é controlado? | Negação por padrão; papéis por rota; posse do recurso conferida contra o usuário do token. Recurso de outro usuário responde 404. | `SegurancaTest.pedidoDeOutroDonoResponde404` |
| Criptografia em trânsito | Há TLS? | Sim, na borda (balanceador ou ingress). O serviço envia HSTS de um ano nas respostas HTTPS. TLS entre serviços depende da implantação. | HSTS configurado em `ConfiguracaoDeSeguranca.java`; o Spring só o envia em HTTPS, por isso não aparece no teste, que usa HTTP |
| Criptografia em repouso | Os dados são cifrados em repouso? | Depende do banco e do provedor usados na implantação. O modelo não guarda dado em disco. | |
| Desenvolvimento seguro | Há SAST, SCA e varredura de segredos? | Sim, em todo push e pull request, barrando o merge: Semgrep, OSV-Scanner sobre SBOM CycloneDX, gitleaks. | `.github/workflows/esteira.yml` |
| Desenvolvimento seguro | Há teste dinâmico? | OWASP ZAP baseline contra o contêiner em todo build. Pentest antes de lançamento relevante. | `esteira.yml`, job `imagem` |
| Cadeia de suprimentos | Como as dependências são controladas? | Versões fixadas, actions por SHA, imagens por digest, atualização automática com espera de 7 dias, SBOM a cada build. | `renovate.json`, `Dockerfile` |
| Cadeia de suprimentos | A imagem é assinada? | Sim, com cosign sem chave guardada (Sigstore), na publicação de produção. | `esteira.yml`, job `publicar` |
| Contêiner | A imagem roda como root? | Não. Base distroless, usuário sem privilégio, sistema de arquivos somente leitura e sem capacidades do kernel; o CI sobe o contêiner assim. | `Dockerfile`, `docker-compose.yml` |
| Contêiner | A imagem é varrida? | Trivy, barrando falha alta ou crítica com correção disponível. | `esteira.yml` |
| Gestão de vulnerabilidades | Há prazo de correção? | P1 em 7 dias, P2 em 30, P3 em 90, P4 em 180. | `docs/processo/gestao-de-vulnerabilidades.md` |
| Resposta | Como reportar uma falha? | Aviso privado do GitHub ou e-mail; confirmação em 3 dias úteis; divulgação coordenada em até 90 dias. | `SECURITY.md`, `docs/processo/resposta-a-vulnerabilidade.md` |
| Logs | O que é registrado? | Log estruturado em JSON (ECS) com identificador de correlação; sem token, senha nem dado pessoal. | `application.yml`, `FiltroDeCorrelacao.java` |
| Disponibilidade | Há proteção contra abuso? | Limite de requisições por endereço na aplicação; contra ataque distribuído, depende do gateway ou WAF da implantação. | `FiltroDeLimite.java`, `LimiteTest` |
| Pessoas | A equipe recebe treinamento? | Programa de Security Champions com trilha de formação. | `docs/processo/security-champions.md` |
