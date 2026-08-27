# Agente e CLI do Homelab Monitor

Este diretório contém o agente HTTP somente leitura, a CLI de manutenção e o
instalador do serviço systemd.

## Instalação simples

Em qualquer máquina Linux com Tailscale conectado:

```bash
curl -fsSL https://raw.githubusercontent.com/ArturCassu/homelab-monitor-android/main/server-agent/bootstrap.sh | sh
```

O assistente pergunta:

1. se a instalação será nesta máquina ou em outra via SSH/Tailscale;
2. qual porta a API deve usar, com `8099` como padrão;
3. se o Immich deve ser monitorado.

O SSH usa a autenticação já configurada e não recebe senhas como argumento.
Quando o `sudo` for necessário, ele é solicitado no terminal do host de
destino.

Se já houver um serviço antigo, mas faltarem `/usr/local/bin/homelab-monitor`
ou o pacote em `/opt/homelab-metrics/package`, o bootstrap identifica a
instalação incompleta. Escolha **1 — reparar/reconfigurar este host** para
instalar os arquivos atuais, manter o token existente e atualizar o serviço.
A opção **2** continua disponível para instalar em outra máquina.

## Comandos

```text
homelab-monitor help
homelab-monitor install
homelab-monitor status
homelab-monitor doctor
homelab-monitor config
homelab-monitor test
homelab-monitor token
homelab-monitor logs [-f]
homelab-monitor restart
homelab-monitor uninstall
```

`status` mostra o estado do serviço, coletor Docker, endpoint, Immich e
resposta HTTP. `doctor` verifica Tailscale, Docker, systemd e a API. `config`
altera porta, volumes e Immich. `test` chama `/v1/metrics` sem mostrar o
token. `token` só exibe o segredo após solicitação explícita no terminal
local.

## Agente

O endpoint padrão é `http://<ip-do-tailscale>:8099/v1/metrics` e exige:

```text
Authorization: Bearer <token>
```

O token fica em `/etc/homelab-metrics/token`, com acesso restrito. O processo
Python roda como `homelab-metrics`; um helper root executa somente `docker ps
--all` e grava um snapshot local. O Docker socket nunca é exposto ao celular.

O Immich é opcional e pode ser alterado com:

```bash
homelab-monitor config
```

O contrato completo da API está em [`../docs/metrics-agent.md`](../docs/metrics-agent.md).
