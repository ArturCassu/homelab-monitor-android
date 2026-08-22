# Homelab Monitor para Android

MVP nativo em Kotlin para acompanhar o servidor Debian 13 `homelab` pela rede Tailscale. A tela principal usa Jetpack Compose e o projeto registra sete widgets Android Glance independentes:

1. Status geral: online/offline, uptime e última atualização.
2. CPU e RAM.
3. Armazenamento por volume.
4. Temperatura e sensores disponíveis.
5. Containers Docker: ativos, parados e com erro.
6. Immich: servidor e banco de dados.
7. Resumo geral.

O app não acessa Docker, não abre portas no servidor e não inclui credenciais reais. Quando o modo mock está ativado (padrão), a aplicação funciona sem o agente do homelab para permitir testar a UI e os widgets.

## Requisitos

- Android Studio recente com JDK 17.
- Android SDK Platform 35 e Build Tools compatíveis.
- Um dispositivo/emulador Android API 26 ou superior.
- Para dados reais: o app Tailscale conectado no Android e o agente de métricas acessível pelo endereço Tailscale do homelab.

## Build e instalação

Na raiz do projeto:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

No Windows, use `gradlew.bat` no lugar de `./gradlew`.

O APK de debug é gerado em `app/build/outputs/apk/debug/app-debug.apk`. O projeto usa Gradle Version Catalog e o Gradle Wrapper; não é necessário instalar Gradle globalmente.

## Configuração no app

1. Abra o app; o modo mock já vem selecionado.
2. Para usar o agente, conecte o Android ao tailnet e informe o endpoint base, por exemplo `http://homelab:8099` ou `https://homelab.example.ts.net:8099`.
3. Informe o token Bearer emitido especificamente para este app.
4. Desmarque **Usar dados mockados (MVP)** e toque em **Salvar e atualizar**.
5. Adicione à tela inicial os widgets individualmente pelo seletor de widgets do launcher. Cada item `Homelab · ...` corresponde a uma área independente.

O app acrescenta `/v1/metrics` ao endpoint base, caso esse caminho ainda não esteja presente. A coleta manual e a coleta periódica usam a mesma camada de repositório.

O endpoint HTTP sem TLS é aceito no MVP para facilitar um agente limitado à interface Tailscale. Nesse caso, a segurança do transporte depende do túnel Tailscale; para uso mais rigoroso, publique o agente somente via HTTPS. Nunca encaminhe a porta para a Internet.

## Atualização pelo GitHub

Como este repositório é público, o app tem a opção **Verificar atualizações** na tela principal. O app consulta `update/latest.json`, baixa a versão indicada para o cache privado, confere o SHA-256 e abre o instalador oficial do Android. O arquivo não é salvo em `Downloads`; o cache é limpo na próxima abertura do app e antes de cada novo download.

O Android ainda exige a confirmação do usuário para instalar um APK fora da Play Store. Para publicar uma versão nova, gere o APK, publique-o na release correspondente e atualize `update/latest.json` com o `version_code`, `version_name`, URL pública do APK e SHA-256. O APK deve ser assinado com a mesma chave da versão instalada; os builds `debug` deste MVP só atualizam entre máquinas que usam a mesma chave debug.

## Arquitetura

- `data/model/MonitoringModels.kt`: contrato de métricas, regras de estado e formatação.
- `data/repository/MonitorRepository.kt`: interface substituível, `MockMonitorRepository` e cliente HTTP somente leitura com OkHttp.
- `data/repository/SecureSettingsStore.kt`: endpoint e token cifrados com AES/GCM; a chave é gerada e mantida no Android Keystore.
- `data/repository/SnapshotStore.kt`: cache local do último snapshot, sem credenciais.
- `worker/MonitorWorker.kt`: atualização periódica via WorkManager, com rede conectada e intervalo mínimo do Android de 15 minutos.
- `ui/`: dashboard Compose e configuração.
- `widget/HomelabWidgets.kt`: sete `GlanceAppWidget` e receivers independentes; os widgets leem o cache e não fazem chamadas arbitrárias à rede.
- `update/`: verificação, download temporário e preparação do APK de atualização pelo GitHub público.
- `docs/metrics-agent.md`: especificação do pequeno agente a ser executado no homelab.

O `Application` agenda uma sincronização única periódica. Uma atualização bem-sucedida grava o snapshot e redesenha todos os widgets. Se a API falhar, o último snapshot permanece visível e a tela informa a falha da última coleta; o WorkManager agenda nova tentativa.

Os widgets usam o fundo adaptativo do tema Glance, com cantos arredondados, e aceitam redimensionamento horizontal e vertical pelo launcher. Cada provider declara limites mínimos e máximos próprios para evitar que o conteúdo fique ilegível em tamanhos extremos.

## Segurança e limites

- O token nunca é gravado em texto puro nas preferências e não é impresso em logs.
- O agente deve executar com usuário sem privilégios e consultar apenas APIs/sockets locais necessários; o celular recebe somente métricas agregadas.
- O socket Docker (`/var/run/docker.sock`) não deve ser montado, exposto ou encaminhado ao Android.
- O MVP não implementa rotação automática de token, certificado cliente, notificações ou histórico de séries temporais.
- O Android pode atrasar atualizações periódicas por Doze, bateria e políticas do fabricante. A coleta manual serve para diagnóstico imediato.
- A API real e o agente do servidor ainda dependem da implementação descrita na especificação. Até lá, o mock é a fonte funcional padrão.

## Decisões do MVP

- Endpoint único `/v1/metrics` em vez de várias chamadas, reduzindo consumo e superfície de autenticação.
- JSON versionado (`schema_version`) com bytes absolutos para evitar ambiguidades de unidade.
- Uma implementação sem Hilt para manter o projeto pequeno; `MonitorRepository` e `RepositoryFactory` permitem substituir o backend sem alterar a UI.
- Timestamps em epoch milliseconds para parsing simples e consistente entre Debian e Android.
- Distribuição inicial fora da Play Store para evitar custo de cadastro; o APK fica público, mas não contém credenciais do homelab.
