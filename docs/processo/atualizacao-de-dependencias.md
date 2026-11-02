# Atualização de dependências

| | |
|---|---|
| Dono do processo | Segurança de aplicações |
| Revisão | 02/11/2026 |
| Vale para | todo serviço criado a partir do alicerce |

## Objetivo

Manter as dependências em dia sem que a fila de atualização passe pela segurança. Duas ferramentas
dividem o trabalho, cada uma no que faz melhor, sem abrir o mesmo PR duas vezes.

## Divisão

| Ferramenta | Cuida de | Config |
|---|---|---|
| Renovate (self-hosted) | dependências Maven da aplicação | [`.github/renovate.json5`](../../.github/renovate.json5), workflow `renovate.yml` |
| Dependabot | imagens Docker, docker-compose e GitHub Actions | [`.github/dependabot.yml`](../../.github/dependabot.yml) |

O Renovate agrupa patch e minor num PR só, mantém um painel de dependências numa issue e cruza
versão com o banco do OSV. O Dependabot segue nas atualizações de infraestrutura, onde a oferta do
Renovate não acrescenta nada.

## Regra de maturidade

Em ambos, versão nova só vira PR depois de 7 dias publicada (`minimumReleaseAge` no Renovate,
`cooldown` no Dependabot). Correção de vulnerabilidade não espera: o Renovate abre o PR na hora pelo
`vulnerabilityAlerts`, e o Dependabot pelo aviso de segurança do GitHub.

## Token

O Renovate self-hosted roda no próprio repositório e precisa de um token para abrir PR. Sem o
segredo `RENOVATE_TOKEN`, o workflow termina sem fazer nada, então o repositório não quebra enquanto
o token não existe.

Para ligar:

1. Criar um token fine-grained com acesso a este repositório e permissão de `Contents` (leitura e
   escrita) e `Pull requests` (leitura e escrita).
2. Guardar como segredo `RENOVATE_TOKEN` em Settings, Secrets and variables, Actions.
3. Rodar o workflow Renovate manualmente uma vez (Actions, Renovate, Run workflow) para conferir. A
   primeira execução abre o painel de dependências.

Usar um token próprio, e não o `GITHUB_TOKEN` do fluxo, é o que faz a esteira rodar nos PRs que o
Renovate abre; PR aberto com o token do fluxo não dispara outro workflow.
