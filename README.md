# Homelab Monitor

Aplicativo Android nativo para acompanhar um servidor Debian pela rede privada
do Tailscale. O projeto inclui o app, sete widgets Android independentes e um
agente de métricas somente leitura para o homelab.

## Instalação rápida

### No homelab

Abra um terminal no Debian 13 e execute uma única vez:

```bash
curl -fsSL https://raw.githubusercontent.com/ArturCassu/homelab-monitor-android/main/server-agent/bootstrap.sh | sh
```

O instalador é interativo e pergunta:

1. instalar nesta máquina ou em outra máquina via SSH/Tailscale;
2. porta da API, usando `8099` como padrão;
3. ativar ou não o monitoramento opcional do Immich.

Se detectar a instalação antiga que já possui o serviço, mas não possui a CLI,
ele informa o problema e oferece **reparar/reconfigurar este host**. Essa opção
instala o pacote completo, mantém o token existente e atualiza o serviço. A
opção de instalar em outra máquina via SSH continua disponível.

Requisitos do host: Debian ou Linux compatível, `curl`, `tar`, `openssl`,
Tailscale conectado e, opcionalmente, Docker. O `sudo` pode solicitar a senha
no terminal do homelab; ela não é passada ao script nem armazenada.

### No celular Android

1. Instale e conecte o [Tailscale no Android](https://play.google.com/store/apps/details?id=com.tailscale.ipn).
2. Baixe o [APK de teste mais recente](https://github.com/ArturCassu/homelab-monitor-android/releases/download/v0.4.1/HomelabMonitor-debug.apk).
3. Se o Android pedir, permita temporariamente a instalação pelo navegador ou
   gerenciador de arquivos.
4. Abra o Homelab Monitor e informe `homelab`, `http://homelab` ou uma URL com
   porta, como `http://homelab:8099`.
5. Informe o token mostrado intencionalmente no terminal do homelab por:

   ```bash
   sudo homelab-monitor token
   ```

   Não publique esse valor em issues, chats ou commits.
6. Toque em **Testar e entrar**. O app só salva a configuração depois de uma
   resposta válida da API.

O host e o token são protegidos por uma chave do Android Keystore. Para trocar
de homelab depois, use **Configurações → Trocar homelab**.

## Configuração do agente

Depois da instalação, a CLI fica disponível no homelab:

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

Comandos úteis:

- `status`: mostra o serviço, a porta, o Immich e o status HTTP da API;
- `doctor`: verifica Tailscale, Docker, systemd e a instalação;
- `config`: altera porta, volumes e Immich;
- `test`: testa `GET /v1/metrics` sem imprimir o token;
- `token`: mostra o token somente quando solicitado no terminal local;
- `logs [-f]`: mostra ou acompanha os logs do serviço;
- `restart`: reinicia o agente e o coletor Docker;
- `install`: reabre o instalador local/SSH;
- `uninstall`: remove o agente após confirmação explícita.

O endpoint padrão é:

```text
http://<ip-ou-hostname-do-tailscale>:8099/v1/metrics
```

O app também aceita a porta omitida: HTTP usa `8099` e HTTPS usa `443`. Uma
porta explícita sempre é preservada.

## Widgets

O app possui sete widgets independentes e redimensionáveis:

- Status: online/offline, uptime e última atualização;
- CPU/RAM: uso, memória e load;
- Armazenamento: uso por volume com barras proporcionais;
- Sensores: temperaturas disponíveis;
- Containers: ativos, parados, erros e detalhes quando houver espaço;
- Immich: servidor, banco e versão, quando habilitado;
- Geral: resumo do host e dos serviços.

Para adicionar: pressione uma área vazia da tela inicial, escolha **Widgets**
e selecione **Homelab Monitor**. Cada card pode ser adicionado
separadamente. Para redimensionar, mantenha o widget pressionado e arraste as
alças; o conteúdo muda entre uma versão compacta e uma expandida para evitar
texto cortado.

Os previews do seletor do Android usam o mesmo desenho dos widgets reais. Os
indicadores são desenhados pelo app, sem emojis e sem depender da fonte do
celular.

## Atualizações do APK

Em **Configurações**, use **Verificar atualização**. O app baixa uma única
cópia temporária no cache privado, verifica o SHA-256 e abre o instalador
oficial do Android. O Android ainda exige confirmação para instalar APK fora da
Play Store, mas o arquivo temporário é reutilizado/removido e não se acumula em
`Downloads`.

Também é possível usar a página de [releases do GitHub](https://github.com/ArturCassu/homelab-monitor-android/releases).
Para atualizar sobre a versão instalada, a assinatura do APK precisa ser a
mesma; a release de teste do projeto mantém a assinatura de desenvolvimento.

## Build local

Requisitos: Android Studio recente, JDK 17, Android SDK Platform 35 e Android
API 26 ou superior.

Na raiz do projeto:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

No Windows, use `gradlew.bat`. O APK debug fica em
`app/build/outputs/apk/debug/app-debug.apk`; o release local é
`app/build/outputs/apk/release/app-release-unsigned.apk`.

## API e segurança

A API mínima é somente leitura:

```text
GET /v1/metrics
Authorization: Bearer <token>
Accept: application/json
```

O agente escuta apenas no IPv4 do Tailscale. O celular nunca recebe acesso ao
Docker socket: um helper root executa somente `docker ps --all` e grava um
snapshot local para o processo Python ler. O processo HTTP roda como
`homelab-metrics`, usa limites do systemd e não oferece comandos remotos.

O Immich é opcional. Quando desativado, `immich.enabled` é `false` e o app
oculta o estado do Immich no resumo e no dashboard.

O contrato detalhado está em [`docs/metrics-agent.md`](docs/metrics-agent.md),
e a implementação da instalação está em
[`server-agent/README.md`](server-agent/README.md).

## Solução de problemas

### `homelab-monitor: command not found`

A instalação antiga pode ter criado o serviço sem instalar a CLI. Execute
novamente o bootstrap acima e escolha **1 — reparar/reconfigurar este host**.
Não é necessário remover o serviço nem apagar o token.

### O serviço está ativo, mas a API não responde

No homelab, rode:

```bash
homelab-monitor status
homelab-monitor doctor
homelab-monitor logs
```

Confirme que o Tailscale está conectado no homelab e no celular e que o host e a
porta usados no app correspondem ao resultado de `status`.

### HTTP 401 no app

Gere o valor novamente no terminal do homelab com
`sudo homelab-monitor token` e substitua o token salvo no app em
**Configurações → Trocar homelab**. O token não é enviado ao GitHub.

### Immich aparece como indisponível

O serviço é opcional e depende dos nomes dos containers. Confira a opção
`ENABLE_IMMICH` com `homelab-monitor config` e valide os containers com
`homelab-monitor doctor`.

### Widgets não atualizam imediatamente

O Android pode atrasar o WorkManager por economia de bateria e pelo modo Doze.
Abra o app e use a sincronização manual; a atualização periódica respeita o
limite mínimo de 15 minutos do Android.

## Estrutura principal

- `app/src/main/java/com/example/homelabmonitor/ui`: onboarding e dashboard
  Compose;
- `app/src/main/java/com/example/homelabmonitor/widget/HomelabWidgets.kt`:
  sete providers Glance responsivos;
- `app/src/main/java/com/example/homelabmonitor/data/repository`: validação,
  armazenamento seguro e repositório substituível por API/mock;
- `app/src/main/java/com/example/homelabmonitor/worker`: sincronização com
  WorkManager;
- `server-agent/homelab-monitor`: CLI interativa;
- `server-agent/bootstrap.sh`: instalador público de um comando;
- `server-agent/homelab_metrics.py`: API Python sem dependências externas;
- `update/latest.json`: metadados usados pelo atualizador do APK.

O app continua funcionando com dados mockados quando o usuário escolhe o modo
demonstração; o modo real exige host válido e token válido.
