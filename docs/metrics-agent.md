# Especificação do agente de métricas do homelab

Esta especificação é independente do app Android. O agente deve rodar no Debian 13 `homelab` como um serviço pequeno, somente leitura, preferencialmente em um usuário dedicado sem acesso administrativo.

## Endpoint mínimo

`GET /v1/metrics`

Headers obrigatórios:

```text
Authorization: Bearer <token>
Accept: application/json
```

Resposta `200 OK`:

```json
{
  "schema_version": 1,
  "host": "homelab",
  "online": true,
  "uptime_seconds": 345678,
  "observed_at_epoch_ms": 1776720000000,
  "cpu": {
    "usage_percent": 18.4,
    "load_1m": 0.42
  },
  "memory": {
    "used_bytes": 5368709120,
    "total_bytes": 16106127360
  },
  "volumes": [
    {
      "name": "/srv/docker",
      "used_bytes": 420000000000,
      "free_bytes": 580000000000,
      "total_bytes": 1000000000000
    }
  ],
  "sensors": [
    {
      "name": "CPU Package",
      "value": 48.0,
      "unit": "°C",
      "available": true
    }
  ],
  "containers": {
    "running": 11,
    "stopped": 2,
    "error": 1,
    "items": [
      {"name": "immich-server", "state": "running", "health": "healthy"}
    ]
  },
  "immich": {
    "server": "healthy",
    "database": "healthy",
    "version": "v1.132.0"
  }
}
```

Campos desconhecidos podem ser adicionados no futuro; o cliente ignora campos extras. `schema_version` deve ser incrementado apenas quando houver mudança incompatível. Valores de armazenamento e memória são inteiros em bytes. `usage_percent` varia de 0 a 100. `online` significa que o agente conseguiu coletar o estado do host na amostra.

Sensores ausentes devem ser omitidos ou enviados com `available: false`; não invente valores. Se Immich não estiver instalado, use `"unknown"` em `server` e `database`.

## Autenticação e rede

- Gere um token aleatório longo por instalação/app e valide comparação em tempo constante.
- Aceite somente `Authorization: Bearer ...`; responda `401` sem revelar se o token ou o endpoint estava errado.
- Faça bind na interface Tailscale (`tailscale0`) ou em loopback atrás de um proxy HTTPS acessível pelo tailnet.
- Não publique a porta no roteador, firewall externo ou interface LAN sem necessidade.
- Responda `405` a métodos diferentes de `GET`; não crie endpoints de comando.
- Desabilite logs do header `Authorization`; logs de acesso devem conter apenas método, rota, código e duração.
- Recomenda-se HTTPS para produção. Se o MVP usar HTTP dentro do tailnet, mantenha a porta acessível somente pelo Tailscale e trate o token como segredo de alto valor.

## Coleta permitida

O agente pode ler:

- `/proc` e `/sys` para uptime, CPU, memória e sensores;
- `statvfs`/`df` para os volumes explicitamente configurados;
- `docker ps`/API local somente para leitura, com uma política que não permita operações de escrita;
- os endpoints locais de health/readiness do Immich e o estado de disponibilidade do PostgreSQL usado pelo Immich.

Não monte o Docker socket em um container Android-facing, não encaminhe o socket pela rede e não aceite parâmetros do cliente que sejam repassados ao Docker ou ao shell. Volumes e nomes de serviços devem ser uma allowlist de configuração do agente.

## Serviço e falhas

- Execute como `homelab-metrics` com `NoNewPrivileges=true`, `PrivateTmp=true`, filesystem somente leitura quando possível e limites de CPU/memória.
- Mantenha o token em um arquivo root-owned (`0600`) ou em um mecanismo de segredo do host; nunca no repositório Git.
- Use timeout curto por coletor e retorne o restante dos dados mesmo quando um sensor não existir.
- Se a coleta essencial falhar, retorne `503` e um corpo de erro sem detalhes sensíveis. O app manterá o último snapshot e tentará novamente.
- Faça cache no máximo por poucos segundos para evitar que uma chamada lenta bloqueie o endpoint.

## Compatibilidade com este app

O app chama somente `GET <endpoint-base>/v1/metrics`, envia o token como Bearer e desserializa o JSON acima. A implementação do agente pode ser em Go, Rust ou Python; o contrato HTTP é a fronteira estável e o app não depende de comandos específicos do Debian.
