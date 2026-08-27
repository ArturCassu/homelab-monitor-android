# Agente de métricas do homelab

O agente é um serviço independente do aplicativo Android. Ele deve rodar no
Debian 13 `homelab`, somente pela rede Tailscale, sem expor Docker socket ou
qualquer endpoint de comando.

## Instalação e manutenção

Use a CLI incluída em `server-agent/homelab-monitor`:

```bash
homelab-monitor install
homelab-monitor status
homelab-monitor doctor
homelab-monitor config
homelab-monitor test
homelab-monitor logs
homelab-monitor restart
```

O `install` é interativo. Ele oferece instalação local ou via SSH, pergunta a
porta e pergunta se o Immich deve ser habilitado. Se já detectar
`homelab-metrics.service` ou a configuração em `/etc/homelab-metrics`, ele
oferece reparar/reconfigurar este host ou instalar em outro destino SSH. A
reparação reinstala a CLI, o pacote e o serviço sem gerar um novo token quando
o token atual já existe.

O bootstrap público é:

```bash
curl -fsSL https://raw.githubusercontent.com/ArturCassu/homelab-monitor-android/main/server-agent/bootstrap.sh | sh
```

A CLI nunca recebe senha como argumento. A instalação SSH depende de uma chave
ou sessão Tailscale SSH já autorizada; o `sudo` é solicitado no terminal do
servidor de destino.

## Endpoint mínimo

```text
GET /v1/metrics
Authorization: Bearer <token>
Accept: application/json
```

Exemplo de resposta:

```json
{
  "schema_version": 1,
  "host": "homelab",
  "online": true,
  "uptime_seconds": 345678,
  "observed_at_epoch_ms": 1776720000000,
  "cpu": {"usage_percent": 18.4, "load_1m": 0.42},
  "memory": {"used_bytes": 5368709120, "total_bytes": 16106127360},
  "volumes": [
    {"name": "/srv/storage", "used_bytes": 420000000000, "free_bytes": 580000000000, "total_bytes": 1000000000000}
  ],
  "sensors": [
    {"name": "CPU Package", "value": 48.0, "unit": "°C", "available": true}
  ],
  "containers": {
    "running": 11,
    "stopped": 2,
    "error": 1,
    "items": [
      {"name": "immich_server", "state": "running", "health": "healthy"}
    ]
  },
  "immich": {
    "enabled": true,
    "server": "healthy",
    "database": "healthy",
    "version": "v1.132.0"
  }
}
```

Quando o Immich é desabilitado na instalação/configuração, o agente retorna:

```json
{"immich":{"enabled":false,"server":"unknown","database":"unknown","version":null}}
```

O app ignora o bloco no resumo e no dashboard quando `enabled` é `false`.

## Autenticação e rede

- Gere um token aleatório longo por instalação. A CLI o guarda em
  `/etc/homelab-metrics/token`, com proprietário root e leitura apenas pelo
  grupo do serviço.
- O comando `homelab-monitor token` só exibe o valor após solicitação
  explícita no terminal local.
- Compare o Bearer token em tempo constante e responda `401` sem indicar se o
  endpoint ou o segredo estava errado.
- Faça bind no IPv4 da interface Tailscale. A CLI adiciona a porta ao UFW
  somente em `tailscale0` quando o UFW está ativo.
- Responda `405` a métodos diferentes de `GET` e não registre o header
  `Authorization`.
- O MVP usa HTTP dentro do tailnet. Para produção, prefira HTTPS atrás de um
  proxy acessível apenas pelo Tailscale.

## Coleta e privilégios

O processo Python roda como `homelab-metrics` e lê `/proc`, `/sys` e os
volumes explicitamente configurados. O Docker é coletado por um helper root
fixo que executa apenas `docker ps --all --format ...` e escreve um snapshot
em `/run/homelab-metrics/docker.tsv`. A API não possui acesso ao Docker socket.

O helper é atualizado a cada 30 segundos. Se o snapshot estiver ausente ou
antigo, a resposta continua entregando host/CPU/RAM/volumes e marca as métricas
de containers como indisponíveis.

O serviço usa usuário dedicado, `NoNewPrivileges`, `PrivateTmp`, filesystem
protegido, limites de CPU/memória e reinício automático. A configuração de
volumes padrão é `/`, `/srv/storage` e `/mnt/windows-ssd`; altere com
`homelab-monitor config`.

## Compatibilidade com o app

O app normaliza `homelab` para `http://homelab:8099` e chama somente
`/v1/metrics`. Sem porta, HTTP usa 8099 e HTTPS usa 443; uma porta explícita,
como `http://homelab:9100`, é preservada.
O JSON é versionado por `schema_version`; campos extras são ignorados pelo
cliente Android.
