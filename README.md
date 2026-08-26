# Homelab Monitor para Android

Aplicativo nativo em Kotlin para acompanhar um servidor Debian pela rede
privada do Tailscale. O app usa Jetpack Compose na tela principal, Jetpack
Glance nos widgets e WorkManager para sincronizações periódicas.

## O que existe no app

- Onboarding que bloqueia a entrada real até um host válido responder à API.
- Host aceito como `homelab`, `100.x.y.z`, `http://homelab` ou URL completa.
- Porta opcional: `http://homelab` usa `8099` e `https://homelab` usa `443`;
  uma porta explícita é preservada.
- Modo demonstração separado, para conhecer a UI sem servidor.
- Dashboard com estado dominante, sincronização rápida, métricas resumidas,
  erros legíveis e configurações editáveis.
- Sete widgets independentes e redimensionáveis: Status, CPU/RAM,
  Armazenamento, Sensores, Containers, Immich e Geral.
- Os widgets têm previews próprios no seletor do Android, com o mesmo estilo
  visual dos cards; os indicadores são desenhados pelo app e não dependem de
  emojis ou da fonte instalada no celular.
- Immich opcional: quando o agente o desativa, ele deixa de aparecer no
  resumo e no dashboard; o widget dedicado informa que está desativado.

## Build e instalação do APK

Requisitos: Android Studio recente, JDK 17, Android SDK Platform 35 e um
dispositivo Android API 26 ou superior.

Na raiz do projeto:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

No Windows, use `gradlew.bat`. O APK fica em
`app/build/outputs/apk/debug/app-debug.apk`.
O build de release local é gerado como
`app/build/outputs/apk/release/app-release-unsigned.apk`; para os testes deste
projeto, a release do GitHub usa o APK debug assinado pelo keystore de
desenvolvimento, porque nenhum keystore de produção é versionado.

## Primeiro acesso

1. Conecte o Tailscale no celular e no Debian.
2. Abra o app e informe o host ou URL do agente.
3. Se a porta for omitida, o app usa `8099` em HTTP ou `443` em HTTPS.
4. Informe o token do agente e toque em **Testar e entrar**.
5. O app só salva a configuração real depois de receber uma resposta válida
   de `GET /v1/metrics`.

Exemplos aceitos:

```text
homelab
http://homelab
http://homelab:8099
http://100.64.10.20:8099
https://homelab.example.ts.net
```

O host e o token são cifrados com uma chave do Android Keystore. O token não
vai para logs, GitHub ou widgets. Para trocar de servidor, use
**Configurações → Trocar homelab**.

## Instalar o agente no homelab

O agente é separado do app e roda como um serviço somente leitura. A forma
mais simples, em uma máquina Linux com `curl` e `tar`, é:

```bash
curl -fsSL https://raw.githubusercontent.com/ArturCassu/homelab-monitor-android/main/server-agent/bootstrap.sh | sh
```

O instalador pergunta se deve instalar:

- nesta máquina; ou
- em outra máquina via SSH/Tailscale, sem capturar senha.

Também pergunta a porta da API e se o Immich deve ser monitorado. Se o host
já tiver uma instalação, ele não sobrescreve nada silenciosamente: oferece
reconfigurar a instalação atual ou iniciar uma instalação SSH em outra
máquina.

Após a instalação, o comando fica disponível:

```bash
homelab-monitor help
homelab-monitor status
homelab-monitor doctor
homelab-monitor config
homelab-monitor test
homelab-monitor token
homelab-monitor logs
homelab-monitor restart
```

`homelab-monitor token` exibe o segredo somente no terminal local, mediante
ação explícita. Não cole esse valor em chats, commits ou issues.

O serviço HTTP escuta somente no IPv4 do Tailscale. O celular nunca acessa o
Docker socket: um helper root fixo atualiza um snapshot de leitura dos
containers a cada 30 segundos, enquanto a API roda como `homelab-metrics`.

## Contrato da API

```text
GET /v1/metrics
Authorization: Bearer <token>
Accept: application/json
```

A resposta contém CPU, RAM, volumes configurados, sensores, containers e o
estado opcional do Immich. O campo `immich.enabled` informa ao app se o
recurso foi habilitado no servidor. Consulte
`docs/metrics-agent.md` para o contrato completo e as decisões de segurança.

## Arquitetura

- `data/repository/EndpointConfig.kt`: validação e normalização de host,
  esquema, caminho e porta padrão.
- `data/repository/MonitorRepository.kt`: cliente HTTP e mensagens de erro
  orientadas à ação.
- `data/repository/SecureSettingsStore.kt`: endpoint/token cifrados no
  Android Keystore.
- `ui/OnboardingScreen.kt`: conexão inicial e modo demonstração.
- `ui/DashboardScreen.kt`: dashboard Compose responsivo e editável.
- `widget/HomelabWidgets.kt`: sete providers Glance independentes.
- `worker/MonitorWorker.kt`: sincronização periódica respeitando o mínimo de
  15 minutos do Android.
- `server-agent/homelab-monitor`: CLI de instalação e manutenção.
- `server-agent/homelab_metrics.py`: API Python sem dependências externas.

## Segurança e limites

- O agente nunca expõe o Docker socket pela rede.
- A porta é limitada à interface `tailscale0`; não há encaminhamento para a
  Internet.
- A API aceita somente `GET /v1/metrics` com Bearer token.
- O token é gerado no servidor, fica fora do Git e não é impresso pelos
  instaladores automaticamente.
- O modo HTTP é aceitável apenas como MVP dentro do túnel Tailscale; HTTPS é
  recomendado para um uso mais rigoroso.
- O Android pode atrasar a atualização periódica por Doze, bateria e políticas
  do fabricante. O botão de sincronização serve para diagnóstico imediato.

## Atualização do APK

O app consulta `update/latest.json`, baixa a versão nova para o cache privado,
confere o SHA-256 e abre o instalador oficial do Android. O APK temporário não
fica acumulado em `Downloads`; o Android ainda exige confirmação para instalar
um APK fora da Play Store.

As versões de teste são publicadas nas releases públicas do GitHub. A mesma
assinatura de desenvolvimento precisa ser mantida para que o Android aceite a
atualização sobre uma instalação anterior; uma instalação assinada por outro
keystore deve ser removida antes da primeira troca de assinatura.
