# Gestão de vulnerabilidades

| | |
|---|---|
| Dono do processo | Segurança de aplicações |
| Revisão | 29/10/2026 |
| Vale para | todo serviço criado a partir do alicerce |

## Objetivo

Toda falha encontrada tem dono, prazo e fim registrado: corrigida e verificada, ou aceita por alguém
com autoridade para aceitar, com data de revisão.

## De onde vêm os achados

| Fonte | Ferramenta | Quando |
|---|---|---|
| Código (SAST) | Semgrep | todo push e pull request |
| Dependências (SCA) | OSV-Scanner sobre o SBOM, Dependabot ou Renovate | todo push; diário para avisos novos |
| Segredo | gitleaks no histórico inteiro | todo push |
| Imagem | Trivy | todo build de imagem |
| Aplicação rodando (DAST) | OWASP ZAP baseline | todo build e semanal em produção |
| Pentest | equipe interna ou externa | antes de lançamento relevante e anual |
| Relato externo | aviso privado do GitHub ou e-mail de segurança | a qualquer hora, ver [resposta a vulnerabilidade](resposta-a-vulnerabilidade.md) |

## Triagem

A saída das ferramentas passa pelo [crivo](https://github.com/fabriciojunio/crivo), que junta os
relatórios, remove repetição, cruza cada CVE com o catálogo KEV da CISA e o EPSS da FIRST e devolve a
prioridade. Quem tria confirma o resultado: ferramenta erra para os dois lados.

| Prioridade | Critério | Prazo para corrigir |
|---|---|---|
| P1 | explorada (KEV), crítica, ou EPSS de 50% ou mais | 7 dias corridos |
| P2 | alta, ou EPSS de 10% ou mais | 30 dias |
| P3 | média | 90 dias |
| P4 | baixa ou informativa | 180 dias |

O prazo conta da triagem. P1 em serviço exposto à internet abre incidente, não só tarefa.

## Estados

```
Novo -> Triado -> Em correção -> Corrigido -> Verificado
                \-> Risco aceito (com data de revisão)
                \-> Falso positivo (com o motivo)
```

- **Corrigido** é quando a correção entra em produção. **Verificado** é quando o teste que reproduzia a
  falha passa a falhar no ataque. Sem verificação o item não fecha.
- **Risco aceito** precisa de motivo, de quem aceitou e da data de revisão. Nos repositórios ele
  existe como arquivo versionado (`.github/codeql-accepted.json`, `osv-scanner.toml`,
  `.zap/regras.tsv`), e o CI volta a reprovar quando a data passa.
- **Falso positivo** registra por que não é falha, para a próxima pessoa não refazer a análise.

## Registro

Cada achado vira uma issue com o rótulo `seguranca` e estes campos:

| Campo | Exemplo |
|---|---|
| Título | `[P2] SSRF na importação de comprovante` |
| Fonte | pentest, Semgrep, OSV, relato externo |
| Severidade | CVSS 4.0 com o vetor |
| Prioridade e prazo | P2, 28/11/2026 |
| Onde | arquivo e linha, rota ou pacote e versão |
| Como reproduzir | requisição ou teste |
| Dono | a squad dona do serviço |

Detalhe explorável de falha aberta não vai em issue pública. Em repositório público, usar o aviso de
segurança privado do GitHub.

## Indicadores

Medidos por mês, por squad:

- achados abertos por prioridade e quantos estão fora do prazo;
- tempo médio entre triagem e verificação (MTTR), por prioridade;
- riscos aceitos vencidos.

O indicador serve para achar onde falta ajuda, não para ranquear squads.
