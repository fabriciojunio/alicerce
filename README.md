# alicerce

*[Read this in English](README.en.md)*

Modelo de serviço Spring Boot 4 seguro por padrão: a base que um time de segurança de aplicações
entrega às squads para todo serviço novo nascer com autenticação, autorização, cabeçalhos, erro, log
e esteira de segurança já resolvidos.

## O que vem pronto

| Área | O que o modelo faz | Onde |
|---|---|---|
| Autenticação | OAuth 2.0 / OIDC com JWT do Keycloak; emissor e audiência obrigatórios | `ConfiguracaoDeSeguranca.java`, `application.yml` |
| Autorização | Negação por padrão, papéis por rota, dono do recurso vindo do token, 404 para recurso alheio | `PedidoController.java` |
| Entrada | Records com só os campos permitidos e Bean Validation | `PedidoController.java` |
| Erro | Problem Details (RFC 9457) com `correlationId`, sem pilha | `TratadorDeErros.java` |
| Cabeçalhos | CSP, HSTS, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, `Cross-Origin-Resource-Policy`; CORS por lista | `ConfiguracaoDeSeguranca.java` |
| Abuso | Limite por endereço com 429 e `Retry-After` | `FiltroDeLimite.java` |
| Log | JSON no formato ECS, com identificador de correlação validado | `FiltroDeCorrelacao.java` |
| Actuator | Só `health`, sem detalhe | `application.yml` |
| Contêiner | Distroless, sem root, somente leitura, sem capacidades, bases por digest | `Dockerfile` |
| Chamada para fora | Cliente HTTP anti-SSRF: só HTTPS na 443, nenhum endereço interno nem por DNS rebinding ou redirecionamento, tempo e tamanho limitados | `saida/ClienteHttpSeguro.java` |

## A esteira

`.github/workflows/esteira.yml`, sem nenhuma etapa com `continue-on-error`:

| Etapa | Ferramenta |
|---|---|
| Testes, incluindo os de segurança | JUnit, Spring Security Test |
| Segredos no histórico | gitleaks |
| SAST | Semgrep |
| SCA | OSV-Scanner sobre SBOM CycloneDX |
| Imagem | Trivy, barrando alta e crítica com correção |
| DAST | OWASP ZAP baseline contra o contêiner, que sobe somente leitura |
| Publicação | GHCR, com assinatura cosign sem chave guardada (Sigstore) |
| VEX | OpenVEX gerado dos aceites do OSV e anexado à imagem como atestado assinado |
| Dependências | Dependabot com espera de 7 dias, imagens e actions por digest |

Todas gratuitas.

## Processo

O que não é código e as empresas pedem junto:

- [Gestão de vulnerabilidades](docs/processo/gestao-de-vulnerabilidades.md): fontes, triagem com o
  [crivo](https://github.com/fabriciojunio/crivo), SLA por prioridade, estados e indicadores.
- [Resposta a vulnerabilidade reportada](docs/processo/resposta-a-vulnerabilidade.md): recebimento,
  CVE, correção privada e divulgação coordenada.
- [Modelo de aviso de segurança](docs/processo/modelo-de-advisory.md).
- [Programa de Security Champions](docs/processo/security-champions.md).
- [Respostas ao questionário de segurança de cliente](docs/processo/questionario-de-seguranca.md).
- [Atualização de dependências](docs/processo/atualizacao-de-dependencias.md): Renovate para o Maven,
  Dependabot para a infraestrutura, regra de maturidade e token.
- [Modelo de ameaças](docs/seguranca/modelo-de-ameacas.md).

## Como usar

Java 25, Maven e, para o ambiente completo, Docker.

```bash
mvn verify                  # 43 testes, inclusive as garantias de segurança e o anti-SSRF
docker compose up --build   # o serviço e o Keycloak com o realm de exemplo
```

Para um serviço novo: copie o repositório, troque o pacote `pedidos` pelo seu domínio, ajuste o
`realm` e a audiência, e mantenha os testes de `SegurancaTest`. Se um deles quebrar, a garantia que
ele prova deixou de valer.

## Chamada para URL que veio de fora

Webhook, importação por URL, prévia de link: sempre que o destino depender do usuário, use o
`ClienteHttpSeguro`, que já vem como bean. A checagem do endereço mora no resolvedor de DNS do
HttpClient, então o IP validado é o mesmo que a conexão usa: não há janela para DNS rebinding, e
cada redirecionamento passa pela mesma checagem. Serviço interno que precise ser chamado entra na
`PoliticaDeSaida` com a faixa exata. A prova está em `ClienteHttpSeguroTest`: loopback, redes
privadas, metadados da nuvem, IPv6 interno, NAT64, registro de DNS misto, redirecionamento para
outro host e para outra porta.

## VEX

`scripts/vex.py` transforma os aceites do `osv-scanner.toml` num documento OpenVEX. Aceite no
prazo vira `not_affected`, com a justificativa e a CVE; aceite vencido vira
`under_investigation`, porque passado o prazo ninguém reavaliou. Na publicação, o documento vai
para a imagem como atestado assinado com cosign.

## O que o modelo não resolve

- O limite de requisições vale por instância; contra ataque distribuído, use o gateway ou um WAF.
- TLS fica na borda; mTLS entre serviços depende da implantação.
- Cifra em repouso e retenção de log dependem do banco e da plataforma escolhidos.

## Uso de IA

Este projeto foi estruturado com apoio de ferramentas de IA generativa. Modelagem, regras de
negócio, revisão do código e conclusões foram revisadas e validadas por Fabrício Júnio.
