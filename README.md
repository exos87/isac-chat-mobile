# ISAC Chat Mobile

Android klient pre `isac-chat`, navrhnuty ako mobilna verzia existujuceho web widgetu z `C:\Users\JL\IdeaProjects\isac-app` a napojeny na backend `C:\Users\JL\IdeaProjects\isac-chat`.

Aktualny stav:

- zalozena moderna Android kostra v Kotlin + Jetpack Compose
- pripraveny session bootstrap cez `API base URL + WebSocket URL + bearer token`
- pripravena REST vrstva pre conversations, messages, approvals, directory a unread count
- fallback login menom a heslom je uz napojeny na novy BFF/session flow `/auth/session/login` + `/auth/session/upstream-authorization`, nie na legacy `/auth/private`
- browser PKCE SSO po callbacku uz neuklada raw Keycloak public-client token priamo do chat session; najprv ho premosti cez `/auth/mobile-handoff` + `/auth/session/mobile-handoff/exchange` a do appky ulozi az upstream bearer token z `/auth/session/upstream-authorization`
- pripravena STOMP/WebSocket kostra pre badge, refresh konverzacii, approvals a presence
- spevneny conversation refresh flow:
  - deduplikacia `mark-as-read` requestov v detaile konverzacie
  - retry iba pre zlyhane `read` pokusy
  - reconnect/backoff v mobilnom STOMP klientovi
- hotove obrazovky:
  - session setup
  - zoznam konverzacii s tabmi `Chaty / Skupiny / Akcie`
  - detail konverzacie so spravami
  - approval panel
- tab `Akcie` zobrazuje aj moje cakajuce approval rozhodnutia z `GET /chat/approvals/my?status=PENDING`
- tap na kartu v sekcii `Moje cakajuce akcie` otvori detail konverzacie priamo v pane `Akcie` a prenesie aj `approvalCaseId`, aby sa dala konkretna akcia zvyraznit rovnako ako pri push/deep linku
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
  - PKCE SSO a fallback session/BFF login
- `feature/home/`
  - zoznam konverzacii a taby
- `feature/conversation/`
  - detail sprav, approval panel a composer

## Stabilizacne poznamky

- `ConversationViewModel` si drzi lokalny prehlad uz potvrdenych a rozbehnutych `read receipt` volani, aby tichy refresh alebo realtime invalidacia neposielali rovnake `POST /read` requesty znova.
- dashboard aj detail konverzacie uz rozlisia auth chybu `401` od bezneho network problemu a zobrazia jasnu vyzvu na nove prihlasenie (`Prihlasenie uz nie je platne. Prihlas sa prosim znova.`) namiesto neurciteho load erroru
- `StompChatRealtimeClient` ma vlastny exponential backoff reconnect, aby appka vedela obnovit realtime aj po docasnom vypadku siete alebo backendu bez rucneho refreshu dashboardu.
- websocket vrstva je oddelena cez `StompSocketFactory`, aby sa reconnect a STOMP handshake dali testovat bez priamej zavislosti na OkHttp websocket implementacii
- foreground udalosti su vytiahnute do samostatneho `AppForegroundCoordinator`, aby `HomeViewModel` aj `ConversationViewModel` vedeli po navrate appky do popredia spravit tichy resync bez priamej zavislosti na Android lifecycle API
- composer v detaile konverzacie ma zakladny recoverable retry flow:
  - pri kratkom vypadku spojenia sa odoslana sprava zaradi na automaticky retry
  - po obnoveni realtime spojenia alebo po navrate appky do popredia sa skusi odoslat znova
  - po obnoveni systemovej siete sa retry a tichy refresh spustia aj bez cakania na dalsi foreground event alebo websocket reconnect
  - ak sa text spravy odosle, ale zlyha az upload priloh, retry sa tyka uz len priloh a nevytvara duplicitnu spravu
  - UI zobrazi stavovu hlasku, ze sprava alebo prilohy cakaju na obnovenie spojenia
  - queued retry stav sa uklada po konverzaciach, takze prezije aj restart appky
  - bursty push/realtime/foreground udalosti spustaju debounce-nuty tichy refresh, aby appka zbytocne nespamovala backend opakovanymi reloadmi
- draft rozpisanej spravy sa uklada po konverzaciach:
  - obnovi sa po navrate do rovnakej konverzacie
  - zachovava text, vybrate prilohy aj scope viditelnosti
  - po uspesnom odoslani sa automaticky vymaze
- detail konverzacie sa pri beznom otvoreni aj po uspesnom odoslani vlastnej spravy vracia na koniec historie, aby pouzivatel videl najnovsie spravy
- picker priloh rozlisuje:
  - `Fotky a videa` cez media picker
  - `Subory` cez vseobecny dokumentovy picker
  - pri suboroch vacsich ako 25 MB appka zobrazi jasnu hlasku este pred uploadom
- home dashboard zobrazuje jemny stav nestabilneho spojenia, ked realtime kanal zahlasi chybu, a po navrate appky do foregroundu sa zosynchronizuje automaticky
- push notifikacie maju pripraveny FCM groundwork:
  - `PushMessagingClient` port pre ziskanie device tokenu
  - `PushTokenSyncCoordinator` pre synchronizaciu tokenu po prihlaseni
  - `FirebaseMessagingService` adapter pre obnovu tokenu a prijem push payloadu
  - `ChatPushPayloadParser` pre typovane spracovanie FCM `data` payloadov
  - `ChatNotificationCoordinator` vie z payloadu otvorit konkretnu konverzaciu a preniest aj `attachmentId`
  - approval push payload s `type=chat-approval` otvori konverzaciu rovno v tabe `Akcie` a prenasa aj `approvalCaseId` na kratke zvyraznenie prislusnej akcie
  - appka si sleduje aktualne otvorenu konverzaciu a foreground notifikacie pre ten isty chat potlaci, aby pouzivatela nerusila duplicitnym bannerom
  - po otvoreni konkretnej konverzacie sa existujuca chat notifikacia sama zrusi
  - conversation detail po otvoreni z notifikacie vie zacielit konkretnu prilohu, inline preview ju automaticky otvori a karta prilohy sa kratko zvyrazni
  - push/deep link vie po otvoreni konverzacie zvyraznit aj konkretnu spravu a automaticky na nu posunut detail
  - fallback text notifikacie rozlisuje spravu vs prilohu a obrazkovu prilohu
  - foreground FCM event ticho zosynchronizuje dashboard aj prave otvorenu konverzaciu bez rucneho refreshu
  - sync FCM tokenu do `profile/preferences` modulu `mobileApp`
  - v servisnych nastaveniach session obrazovky sa da nacitat backend push diagnostika a poslat testovacia push notifikacia pre aktualne prihlasene zariadenie
  - po uspesnom `Poslat test push` sa backend diagnostika automaticky obnovi, aby bolo hned vidiet novy cas pokusu a dorucenia
  - v servisnych nastaveniach sa da push token aj manualne znova zosynchronizovat s backendom bez noveho prihlasenia a po uspesnom syncu sa backend diagnostika obnovi automaticky
  - servisna obrazovka vie skopirovat zjednoteny push report do clipboardu pre rychlu diagnostiku
  - session obrazovka zobrazuje aj lokalny stav push vrstvy:
    - posledny uspesny sync tokenu
    - poslednu chybu syncu
    - posledny vysledok test push notifikacie
  - backend diagnostika zobrazuje aj zdravie registracie zariadenia a konkretne problemy registracie
  - servisna sekcia porovnava lokalny stav mobilu s backend registraciou a upozorni na nesulad tokenu, balika alebo sync chyby
  - formatovanie push diagnostiky je vytiahnute do samostatneho `PushDiagnosticsSummaryFormatter`, aby bolo testovatelne a nebolo roztrusene vo viewmodeli
  - fallback password login si po session/BFF prihlaseni vyziada upstream bearer token, aby mobil vedel bez dalsieho refaktoru pouzit existujuce chat REST a WebSocket API
  - rovnaky upstream bearer token model po novom pouziva aj PKCE SSO callback, aby web/BFF auth refaktoring nevytvaral 401 rozdiel medzi login vetvami
  - servisna diagnostika ma aj zjednoteny stav `Push je pripraveny / Push vyzaduje kontrolu / Push hlasi chybu / Push stav nie je znamy`, aby sa dalo rychlo odlisit, ci ide o konfiguraciu, registraciu alebo dorucovanie
  - diagnostika uz zobrazuje aj detail aktualnej registracie:
    - stav registracie
    - provider
    - balik/verziu appky
    - maskovany token preview
    - cas poslednej aktualizacie tokenu
    - cas posledneho push pokusu
    - cas posledneho uspesneho dorucenia
    - poslednu push chybu

## Testy

- widget/backend regresie sa testuju v projekte `isac-chat`
- mobil ma zavedene JVM unit testy pre conversation refresh a `mark-as-read` spravanie
- mobil ma zavedene JVM unit testy aj pre realtime reconnect klient
- mobil ma zavedene JVM unit testy aj pre push token sync flow
- mobil ma zavedene JVM contract testy pre fallback session/BFF login aj pre PKCE SSO handoff -> BFF session -> upstream bearer token exchange
- servisna push diagnostika uz zobrazuje aj odporucanu dalsiu akciu z backendu, posledny push pokus a posledne uspesne dorucenie
- backend push diagnostika teraz rozlisuje aj zastarane stavy:
  - dlho neobnoveny token
  - stare posledne uspesne dorucenie
  - stary neuspesne potvrdeny pokus o dorucenie
  - tieto stavy sa v mobile premietnu ako `Push vyzaduje kontrolu`
- spustenie:
  - `.\gradlew.bat testUseitacDevDebugUnitTest`

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
   - pouzi firemne SSO alebo fallback login menom a heslom
5. Po uspesnom prihlaseni sa otvori hlavny chat flow.

Poznamka:

- lokalne unit testy sa spustaju cez wrapper `.\gradlew.bat testUseitacDevDebugUnitTest`
- Android Studio ostava najpohodlnejsi sposob na emulator/device overenie a interaktivne debugovanie

## Co este chyba do parity s widgetom

- novy chat picker z directory/workforce
- plna sprava `GROUP_OPEN` skupin:
  - rename
  - pridanie clenov
  - zmena role
  - leave flow
- attachment upload, preview, download, delete
- approval UI uz podporuje vyber kompetentneho, formular a odoslanie rozhodnutia s poznamkou; dalsi polish je workflow lista "moje cakajuce akcie"
- mark-as-read heuristika podla otvorenia konverzacie
- reconnect/backoff strategia pre realtime a offline stav

## Dokumentacia

- `docs/API_MAPPING.md`
- `docs/FCM_SETUP.md`
- `docs/IMPLEMENTATION_PLAN.md`
