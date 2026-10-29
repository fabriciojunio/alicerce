# Modelo de ameaças

Revisado em 29/10/2026. STRIDE sobre o serviço de exemplo. Quem cria um serviço a partir do alicerce
copia este arquivo, troca o fluxo do exemplo pelo próprio e mantém as linhas que continuam valendo.

## O sistema

```
navegador ──HTTPS──> gateway/ingress ──HTTP──> alicerce ──> (banco do serviço)
     │
     └──OIDC (PKCE)──> Keycloak ──emite JWT──┘
```

Fronteiras de confiança: o navegador e tudo o que é nosso; o gateway e o serviço; o serviço e o
provedor de identidade, de quem ele só aceita token assinado.

## Ameaças

| STRIDE | Ameaça | Controle | Prova |
|---|---|---|---|
| S | Token forjado ou de outro emissor | assinatura conferida pela chave pública do emissor; `iss` e `aud` obrigatórios | `application.yml` (validação do Spring Security); sem token, 401 em `SegurancaTest.semTokenRecebe401` |
| S | Token válido de outro serviço do mesmo provedor | audiência `alicerce-api` exigida | `application.yml` |
| T | Corpo com campo extra (dono, papel) | corpo em record só com os campos permitidos; dono vem do token | `SegurancaTest.donoVemDoTokenENaoDoCorpo` |
| T | Linha falsa no log pelo identificador de correlação | só UUID é aceito; o resto é trocado | `SegurancaTest.correlationIdForaDoFormatoEhTrocado` |
| R | Ação sem rastro | log JSON com `correlationId` em toda requisição | `FiltroDeCorrelacao.java` |
| I | Ler recurso de outro usuário (BOLA) | posse conferida; outro dono recebe 404 | `SegurancaTest.pedidoDeOutroDonoResponde404` |
| I | Detalhe interno no erro | RFC 9457 sem pilha nem mensagem da exceção | `SegurancaTest.corpoInvalidoResponde400SemDetalheInterno` |
| I | Actuator expondo ambiente ou memória | só `health`, sem detalhe; o resto negado | `SegurancaTest.actuatorSoExpoeSaudeSemDetalhe` |
| D | Muitas requisições de uma origem | limite por endereço, 429 com `Retry-After` | `LimiteTest` |
| E | Usuário comum em rota de administração | papel `admin` exigido na configuração | `SegurancaTest.rotaDeAdminRecusaUsuarioComum` |
| E | Invasor com shell no contêiner | distroless, sem root, somente leitura, sem capacidades | `Dockerfile`; job `imagem` |

## O que fica para a implantação

- TLS na borda e, se a política pedir, entre serviços (mTLS).
- Limite de requisições distribuído no gateway: o da aplicação vale por instância.
- Cifra em repouso do banco que o serviço escolher.
- Envio dos logs para um destino que o próprio serviço não consiga apagar.
