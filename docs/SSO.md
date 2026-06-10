# Keycloak SSO

Mobilna appka uz obsahuje plny klientsky flow pre browser-based Keycloak SSO.

Podporovane su 3 flavor varianty:

- `useitacDev`
  - client id: `isac-chat-mobile`
  - redirect: `isacchat://auth/callback`
- `usskTest`
  - client id: `isac-chat-mobile-ussk-test`
  - redirect: `isacchat-ussk-test://auth/callback`
- `usskProd`
  - client id: `isac-chat-mobile-ussk`
  - redirect: `isacchat-ussk://auth/callback`

Kazdy flavor ma vlastnu redirect scheme, aby sa dali publikovat ako 3 samostatne Play appky bez konfliktu callbackov.
- spustenie authorization requestu v browseri/custom tabs
- redirect spat do appky cez `isacchat://auth/callback`
- PKCE token exchange v appke
- premostenie raw Keycloak tokenu cez `/auth/mobile-handoff` + `/auth/session/mobile-handoff/exchange`
- ulozenie upstream bearer chat session z `/auth/session/upstream-authorization` a potvrdenie zariadenia v profile
- USS branded login obrazovku s preferovanym SSO vstupom a fallback loginom cez `/auth/session/login` + `/auth/session/upstream-authorization`

Co je uz hotove:

1. Pouzivatel vie prejst cez branded mobilny login screen.
2. Appka vie otvorit Keycloak authorization flow v browseri.
3. Po callbacku vie appka raw Keycloak token premostit na rovnaky upstream bearer model ako fallback login, ulozit session a oznacit zariadenie ako `VERIFIED`.
4. Ak este Keycloak mobile client nie je pripraveny, stale funguje konzistentny fallback login cez rovnaky session/BFF backend flow ako vo web appke.

Co treba nastavit v Keycloaku:

1. Vytvorit novy `public` client pre kazdy flavor, napr.:
   - `isac-chat-mobile`
   - `isac-chat-mobile-ussk-test`
   - `isac-chat-mobile-ussk`
2. Povolit `Standard Flow`
3. Zapnut PKCE `S256`
4. Pridat valid redirect URI podla flavoru:
   - `isacchat://auth/callback`
   - `isacchat-ussk-test://auth/callback`
   - `isacchat-ussk://auth/callback`
5. Volitelne pridat post logout redirect URI podla flavoru:
   - `isacchat://open`
   - `isacchat-ussk-test://open`
   - `isacchat-ussk://open`
6. Povolit realm/browser login rovnakeho pouzivatela, akeho pouziva web ISAC

Poznamka k aktualnemu stavu:

- pre `useitac.onesoft.sk` uz funguje fallback login aj PKCE callback cez rovnaky session/BFF/upstream bearer model
- PKCE flow v appke je pripraveny, ale server musi mat skutocne vytvoreny flavor-specific client, inak authorization endpoint vrati `400`
- lokalny `useitac` Keycloak sa da idempotentne dorovnat skriptom [C:\Users\JL\IdeaProjects\isac-devops\scripts\configure-useitac-keycloak-mobile-sso.ps1](C:\Users\JL\IdeaProjects\isac-devops\scripts\configure-useitac-keycloak-mobile-sso.ps1)

Co vyplnit v appke na Session obrazovke:

- `OIDC Authorization URL`
  - napriklad `https://useitac.onesoft.sk/auth/realms/ISAC-Test/protocol/openid-connect/auth`
- `OIDC Token URL`
  - napriklad `https://useitac.onesoft.sk/auth/realms/ISAC-Test/protocol/openid-connect/token`
- `OIDC Client ID`
  - napr. `isac-chat-mobile`, `isac-chat-mobile-ussk-test`, `isac-chat-mobile-ussk`
- `OIDC Redirect URI`
  - podla flavoru, napr. `isacchat-ussk-test://auth/callback`
- `OIDC Scope`
  - `openid profile email`

Poznamka:
- Aktualny fallback login ide cez `/auth/session/login`, nasledne si appka vytiahne upstream bearer token cez `/auth/session/upstream-authorization`. To je zamer, aby mobil ostal kompatibilny s novym web/BFF auth modelom aj predtym, nez sa uplne uzavrie SSO rollout.
- PKCE browser SSO ide po callbacku cez `POST /auth/mobile-handoff`, `POST /auth/session/mobile-handoff/exchange` a nakoniec `GET /auth/session/upstream-authorization`, aby mobil po uspesnom SSO nepouzival raw public-client token na chat API.

## Regresia callback loop po BFF refaktore

Po auth/BFF refaktore sa objavila este jedna mobilna regresia:

- browser login v Keycloaku prebehol uspesne,
- appka dostala callback `isacchat://auth/callback`,
- nasledne sa Session obrazovka vedela znovu sama pokusit otvorit hosted SSO,
- vysledkom bolo blikajuce prepinanie appky/browseru a pouzivatel sa nevratil stabilne do appky.

Oprava v appke:

1. `MainActivity` spracuje callback aj v `onNewIntent(...)`, po spracovani
   vycisti `intent.data` a vrati `intent.action` na `ACTION_MAIN`, aby sa
   ten isty deep link nespracoval opakovane.
2. `MainActivity` je v manifeste nastavena ako `android:launchMode="singleTask"`,
   aby callback z browsera vracal pouzivatela do existujucej aktivity a
   nevytvaral paralelne instancie.
3. `SessionViewModel` po `OidcSsoStatus.Error` alebo `Success` uz nepusta
   automaticky hosted SSO znovu. Manualny retry stale funguje, lebo
   `startOidcLogin()` pred novym pokusom resetuje predosly SSO status.

Regresne testy:

- `SessionHostedSsoLoopTest`
  - auto launch pre hosted preset sa spusti iba raz
  - callback chyba zablokuje automaticky relaunch
  - manualny retry po callback chybe stale funguje
- `AndroidManifestContractTest`
  - kontroluje `singleTask`
  - kontroluje callback host/path `auth/callback`

Overeny build gate:

```powershell
cd C:\Users\JL\IdeaProjects\isac-chat-mobile
.\gradlew.bat clean testUseitacDevDebugUnitTest assembleUseitacDevDebug bundleUseitacDevRelease
```

Poznamka k manualnemu smoke:

- pri tomto overeni nebolo cez `adb devices -l` pripojene zariadenie,
  takze browser SSO callback treba este potvrdit na realnom telefone alebo
  emulatore s dostupnym login flow.
