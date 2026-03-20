# ISAC Chat Mobile

Android klient pre `isac-chat`, navrhnuty ako mobilna verzia existujuceho web widgetu z `C:\Users\JL\IdeaProjects\isac-app` a napojeny na backend `C:\Users\JL\IdeaProjects\isac-chat`.

Aktualny stav:

- zalozena moderna Android kostra v Kotlin + Jetpack Compose
- pripraveny session bootstrap cez `API base URL + WebSocket URL + bearer token`
- pripravena REST vrstva pre conversations, messages, approvals, directory a unread count
- pripravena STOMP/WebSocket kostra pre badge, refresh konverzacii, approvals a presence
- hotove obrazovky:
  - session setup
  - zoznam konverzacii s tabmi `Chaty / Skupiny / Akcie`
  - detail konverzacie so spravami
  - approval panel
- doplnena dokumentacia a implementacny plan pre parity s web widgetom

## Architektura

Projekt je zatial jednomodulovy a drzi sa jednoducheho rozdelenia:

- `app/`
  - bootstrap aplikacie, navigation
- `core/session/`
  - session storage pre base URL, WS URL, bearer token, `X-Api-Type`
- `core/network/`
  - OkHttp interceptory a STOMP client
- `core/data/remote/`
  - Retrofit interface a DTO
- `core/data/repository/`
  - mapovanie backend API do domennych modelov
- `feature/session/`
  - bootstrap prihlasenia / session konfiguracie
- `feature/home/`
  - zoznam konverzacii a taby
- `feature/conversation/`
  - detail sprav, approval panel a composer

## Default lokalne endpointy

Pre Android emulator su v `BuildConfig` nastavene:

- REST: `http://10.0.2.2:9880/api/`
- WebSocket: `ws://10.0.2.2:9880/api/ws/chat`
- header: `X-Api-Type=private`

To zodpoveda lokalnemu docker backendu bez nginx proxy. Pri realnom deployi sa da session prepisat v uvodnej obrazovke.

## Ako projekt otvorit

1. Otvor `C:\Users\JL\IdeaProjects\isac-chat-mobile` v Android Studio.
2. Nechaj Studio doplnit Gradle wrapper alebo project sync.
3. Spusti emulator.
4. V session obrazovke zadaj:
   - `Chat API base URL`
   - `WebSocket URL`
   - bearer token z existujuceho ISAC prostredia
5. Po ulozeni sa otvori hlavny chat flow.

Poznamka:

- lokalne v tomto prostredi som nevedel spustit Gradle build, lebo `gradle` tu nie je nainstalovany a wrapper zatial nebol vygenerovany
- kostra je pripravena tak, aby sa dala hned zosynchronizovat v Android Studio

## Co este chyba do parity s widgetom

- plny login flow cez Keycloak / ISAC auth namiesto manualneho token bootstrapu
- novy chat picker z directory/workforce
- plna sprava `GROUP_OPEN` skupin:
  - rename
  - pridanie clenov
  - zmena role
  - leave flow
- attachment upload, preview, download, delete
- lepsie approval UI s vyberom kompetentneho a formularom ako vo widgete
- mark-as-read heuristika podla otvorenia konverzacie
- reconnect/backoff strategia pre realtime a offline stav

## Dokumentacia

- `docs/API_MAPPING.md`
- `docs/IMPLEMENTATION_PLAN.md`
