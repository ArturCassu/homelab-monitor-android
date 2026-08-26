# CLI e agente do Homelab Monitor

Esta pasta contém o agente HTTP, a CLI de manutenção e o instalador.

## Uso recomendado

Em uma máquina Linux com `curl` e `tar`:

```bash
curl -fsSL https://raw.githubusercontent.com/ArturCassu/homelab-monitor-android/main/server-agent/bootstrap.sh | sh
```

Ou, a partir desta pasta:

```bash
./homelab-monitor install
```

O fluxo pergunta se a instalação será local ou via SSH, a porta da API e se o
Immich deve ser monitorado. Quando já encontra uma instalação, oferece
reconfigurar o host atual ou instalar em outra máquina.

```text
homelab-monitor help
homelab-monitor status
homelab-monitor doctor
homelab-monitor config
homelab-monitor test
homelab-monitor token
homelab-monitor logs [-f]
homelab-monitor restart
homelab-monitor uninstall
```

O agente roda como `homelab-metrics` e o helper root publica somente um
snapshot de `docker ps`. O token fica em `/etc/homelab-metrics/token` e só é
exibido por solicitação explícita com `homelab-monitor token`.

O endpoint padrão é `http://<ip-tailscale>:8099/v1/metrics`. A opção
`--port`/o comando `config` permitem trocar a porta. Immich pode ser ligado ou
desligado sem remover o restante das métricas.
