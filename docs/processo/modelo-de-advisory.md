# Modelo de aviso de segurança

Formato do GitHub Security Advisory, preenchido em português e em inglês. O exemplo usa o
BRECHA-001 do [relatório do brecha](https://github.com/fabriciojunio/brecha) como se fosse um produto
real.

---

## Leitura de conta de outro cliente pela API (BOLA)

**Pacote:** `br.com.brecha:brecha` (Maven)
**Versões afetadas:** `< 1.1.0`
**Versão corrigida:** `1.1.0`
**Severidade:** Alta, CVSS 4.0 8,6 (`CVSS:4.0/AV:N/AC:L/AT:N/PR:L/UI:N/VC:H/VI:H/VA:N/SC:N/SI:N/SA:N`)
**CWE:** CWE-639
**CVE:** atribuído pelo GitHub na publicação

### Resumo

As rotas `GET /v1/contas/{id}` e `POST /v1/transferencias` não conferem se a conta informada pertence
ao cliente autenticado. Um cliente com conta própria lê o saldo e transfere dinheiro de qualquer outra
conta trocando o número na requisição.

### Impacto

Leitura de saldo e movimentação de qualquer conta por qualquer cliente autenticado.

### Correção

Atualize para a 1.1.0. A conta passa a ser conferida contra o dono do token, e conta de outro cliente
devolve 404. A transferência não aceita mais a conta de origem no corpo.

### Contorno, para quem não pode atualizar já

Bloquear no gateway as chamadas a `/v1/contas/*` e `/v1/transferencias` e migrar os clientes para
`/v2`.

### Créditos

Relatado por <nome de quem reportou, se autorizar>.

### Linha do tempo

| Data | Evento |
|---|---|
| dd/mm/aaaa | Relato recebido |
| dd/mm/aaaa | Recebimento confirmado |
| dd/mm/aaaa | Falha reproduzida e avaliada |
| dd/mm/aaaa | Versão 1.1.0 publicada e aviso divulgado |
