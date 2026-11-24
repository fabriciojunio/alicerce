# Registro de triagem

Achados de ferramenta que foram analisados e fechados sem mudança de código, com o motivo. Cada
linha precisa de evidência que outra pessoa consiga conferir; "a ferramenta errou" sem prova não
fecha nada.

| Data | Ferramenta e regra | Onde | Decisão | Por quê |
|---|---|---|---|---|
| 13/11/2026 | CodeQL `java/spring-disabled-csrf-protection` (alerta 1) | `ConfiguracaoDeSeguranca.java:48` | falso positivo | A API é só resource server: sessão `STATELESS`, nenhum cookie emitido e o token chega no cabeçalho `Authorization`, que o navegador não anexa sozinho numa requisição forjada. Sem credencial ambiente não existe CSRF. O comentário no código diz quando a proteção tem que voltar: no dia em que entrar cookie. |

## Como reabrir

Se o serviço passar a aceitar cookie de sessão, ou se aparecer um formulário servido pela própria
aplicação, a decisão acima perde a base: o alerta volta a valer e o CSRF é religado com
`CookieCsrfTokenRepository`.
