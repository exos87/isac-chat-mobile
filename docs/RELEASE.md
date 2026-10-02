# Release Build

Projekt teraz obsahuje 3 samostatne Play-distribuovatelne varianty:

- `useitacDev`
  package: `sk.uss.isac.chat.mobile`
- `usskTest`
  package: `sk.uss.isac.chat.mobile.ussk.test`
- `usskProd`
  package: `sk.uss.isac.chat.mobile.ussk`

Pre lokalny alebo interny release build:

1. Spusti `scripts\generate-release-keystore.ps1`
2. Spusti napr.:
   - `scripts\build-release.ps1 -Flavor useitacDev`
   - `scripts\build-release.ps1 -Flavor usskTest`
   - `scripts\build-release.ps1 -Flavor usskProd`
   - alebo `scripts\build-release.ps1` pre vsetky 3 naraz
3. Signed artefakty najdes v:
   - `app\build\outputs\apk\<flavor>\release\app-<flavor>-release.apk`
   - `app\build\outputs\bundle\<flavor>Release\app-<flavor>-release.aab`

Aktualna odporucana verzia po SSO callback loop stabilizacii:

- `versionName`: `0.1.10`
- `versionCode`: pouzit automaticky generovany rastuci kod pri build/publish skripte

Release build od tejto verzie uz neumoznuje manualny bearer token bootstrap v UI. Podporovane su len:

- firemne OIDC/Keycloak SSO
- fallback login menom a heslom proti backend session/BFF flow

Automaticky upload `useitacDev` do Google Play internal testing:

- priprav si service account s Play API pristupom
- pouzi `scripts\publish-google-play.ps1`
- detailny navod je v `docs\PLAY_PUBLISH.md`

Konfiguracia endpointov:

- `useitacDev` ma zabudovane fungujucu developer test konfiguraciu
- `usskTest` ma predvolene URL na `https://isac-tst.kbs.sk.uss.com`
- `usskProd` cita URL z Gradle properties alebo ENV premennych:
  - `USSK_PROD_CHAT_BASE_URL`
  - `USSK_PROD_CHAT_WS_URL`
  - `USSK_PROD_PROFILE_API_URL`
  - `USSK_PROD_OIDC_AUTH_URL`
  - `USSK_PROD_OIDC_TOKEN_URL`
  - `USSK_PROD_OIDC_END_SESSION_URL`
  - `USSK_PROD_OIDC_CLIENT_ID`
  - `USSK_PROD_OIDC_REDIRECT_URI`

Pre Play Store:

- kazdy flavor ma vlastny `applicationId`, takze vies publikovat vsetky 3 ako samostatne appky
- odporucany flow je `Internal testing` alebo `Closed testing` pre `useitacDev` a `usskTest`
- `usskProd` je kandidat na produkcny listing

Poznamky:
- `keystore.properties` aj `.jks` subory su ignorovane v gite.
- Vygenerovany lokalny keystore je vhodny pre interne testovanie. Pre Google Play produkciu odporucam samostatny dlhodoby release keystore, ktory budes bezpecne zalohovat mimo repozitara.


## Local ownership and authentication regression runbook (2026-10-02)

Run the lightweight developer flavor JVM suite in an isolated checkout:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA/Android/Sdk"
./gradlew.bat :app:testUseitacDevDebugUnitTest --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process'
```

The ignored `app/src/useitacDev/google-services.json` flavor configuration must be present;
reuse the existing local configuration without putting it into Git. Excluding the Google Services
task with `-x` does not work: the generated-resource provider is queried before task completion.

Local acceptance covers persisted draft/retry isolation between accounts, tenants and deployments,
legacy records without ownership ignored, retry restoration for the same owner, logout/reconnect
invalidation, captured-request ownership, explicit bootstrap headers, destination path boundaries,
authorization on every preview fetch, changed preview content and denied previews, and authentication
clients excluding inherited sensitive loggers. Preview files are private cache artifacts; logout and
identity switches clear that cache without deleting exported attachments. Tokens without a usable
subject, issuer and one unambiguous tenant claim do not get persisted outgoing state or previews.
Realm-only tokens without an explicit tenant claim retain live chat but lose local draft/retry restoration
and attachment previews until the token contract supplies tenant identity. Local owner claims partition
storage only; server authentication remains responsible for verifying JWT signatures and permissions.

The original key/header/logger rules reproduced four failing tests in the targeted negative run;
restored fixes pass the full JVM suite. These checks do not establish Android device lifecycle,
backup behavior, Firebase delivery, release signing, or customer runtime acceptance. Message retry
idempotency still needs a coordinated server/client contract before F-MOB-04 can be closed.
