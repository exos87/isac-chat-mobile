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
