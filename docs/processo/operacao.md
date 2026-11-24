# Operação e detecção

O que todo serviço copiado do modelo já emite, e o que fazer quando avisa.

| Sinal | Onde | Observação |
|---|---|---|
| Log | stdout, JSON ECS | `correlationId` sempre; `traceId` e `spanId` da requisição |
| Métrica | `GET /actuator/prometheus` | exige o papel `metricas`; o Prometheus coleta com `client_credentials` |
| Evento de segurança | `alicerce_seguranca_eventos_total{evento}` | rótulo fechado, toda série nasce em zero |
| Trace | OTLP, com `ALICERCE_TRACE=true` e `ALICERCE_COLETOR` | 10% por padrão (`ALICERCE_AMOSTRA`) |

Coleta no Prometheus:

```yaml
scrape_configs:
  - job_name: alicerce
    metrics_path: /actuator/prometheus
    oauth2:
      client_id: prometheus
      client_secret_file: /run/secrets/prometheus
      token_url: https://<emissor>/protocol/openid-connect/token
    static_configs:
      - targets: ["alicerce:8080"]
```

O cliente `prometheus` precisa do papel `metricas` e de um mapeador de audiência para o serviço.

Objetivos: 99,5% sem erro do servidor em 30 dias e p95 até 300 ms, sem contar o actuator. As regras
estão em `infra/observabilidade/regras.yml`, testadas com promtool na esteira.

## O que fazer

### erro do servidor
Ver a rota em `http_server_requests_seconds_count{outcome="SERVER_ERROR"}` e o log por
`log.level:ERROR`. O cliente recebeu o `correlationId`; com ele se acha a pilha.

### resposta lenta
Achar a rota mais lenta e abrir os traces dela. Se for a chamada para fora, o tempo limite está na
`PoliticaDeSaida`.

### servico fora do ar
Conferir `/actuator/health/liveness` e o motivo da última saída. Se só a coleta falha, é o token do
Prometheus: papel ou audiência.

### tentativa de ssrf
Alguém fez o serviço tentar um endereço interno. Achar a requisição pelo `correlationId` no horário e
tratar como ataque: o destino (169.254.169.254 é credencial da nuvem) diz o que procuravam. Conferir se
outro serviço faz a mesma chamada sem o `ClienteHttpSeguro`.

### tokens recusados
Token vencido aos poucos é normal. Enxurrada é: chave trocada no emissor, cliente com defeito ou alguém
testando token roubado ou forjado. Conferir antes se houve rotação de chave.

### acesso negado em massa
Conta legítima tentando rota acima do papel. Ver o `sub` no log; pode ser conta tomada.

### limite atingido
Uma origem só: bloquear na borda. Muitas: o limite por instância não segura, é caso de WAF.
