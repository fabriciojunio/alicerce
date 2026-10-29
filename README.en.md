# alicerce

*[Leia em português](README.md)*

A secure-by-default Spring Boot 4 service template: the baseline an application security team hands
to product squads so every new service starts with authentication, authorisation, headers, errors,
logging and a security pipeline already in place.

## What comes ready

| Area | What the template does | Where |
|---|---|---|
| Authentication | OAuth 2.0 / OIDC with Keycloak JWTs; issuer and audience required | `ConfiguracaoDeSeguranca.java`, `application.yml` |
| Authorisation | Deny by default, per-route roles, resource owner taken from the token, 404 for someone else's resource | `PedidoController.java` |
| Input | Records with only the allowed fields, plus Bean Validation | `PedidoController.java` |
| Errors | Problem Details (RFC 9457) with `correlationId`, no stack trace | `TratadorDeErros.java` |
| Headers | CSP, HSTS, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`; allow-listed CORS | `ConfiguracaoDeSeguranca.java` |
| Abuse | Per-address rate limit with 429 and `Retry-After` | `FiltroDeLimite.java` |
| Logging | ECS JSON with a validated correlation id | `FiltroDeCorrelacao.java` |
| Actuator | `health` only, no details | `application.yml` |
| Container | Distroless, non-root, read-only, no capabilities, base images pinned by digest | `Dockerfile` |

## The pipeline

`.github/workflows/esteira.yml`, with no step allowed to `continue-on-error`:

| Stage | Tool |
|---|---|
| Tests, security tests included | JUnit, Spring Security Test |
| Secrets in history | gitleaks |
| SAST | Semgrep |
| SCA | OSV-Scanner over a CycloneDX SBOM |
| Image | Trivy, blocking fixable high and critical |
| DAST | OWASP ZAP baseline against the container, started read-only |
| Publishing | GHCR, signed with keyless cosign (Sigstore) |
| Dependencies | Renovate with a 7-day wait and digest pinning |

All of them free.

## Process

What is not code and companies ask for anyway (in Portuguese):

- [Vulnerability management](docs/processo/gestao-de-vulnerabilidades.md): sources, triage with
  [crivo](https://github.com/fabriciojunio/crivo), SLA by priority, states and metrics.
- [Responding to reported vulnerabilities](docs/processo/resposta-a-vulnerabilidade.md): intake, CVE,
  private fix and coordinated disclosure.
- [Security advisory template](docs/processo/modelo-de-advisory.md).
- [Security Champions programme](docs/processo/security-champions.md).
- [Answers to customer security questionnaires](docs/processo/questionario-de-seguranca.md).
- [Threat model](docs/seguranca/modelo-de-ameacas.md).

## Usage

Java 21, Maven and, for the full environment, Docker.

```bash
mvn verify                  # 12 tests, including the security guarantees
docker compose up --build   # the service plus Keycloak with the sample realm
```

For a new service: copy the repository, replace the `pedidos` package with your domain, adjust the
realm and audience, and keep the `SegurancaTest` tests. If one breaks, the guarantee it proves no
longer holds.

## What the template does not solve

- The rate limit is per instance; against distributed attacks, use the gateway or a WAF.
- TLS terminates at the edge; mTLS between services depends on the deployment.
- Encryption at rest and log retention depend on the database and platform chosen.
