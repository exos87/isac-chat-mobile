# Google Play Publish

Tento projekt je pripraveny na automaticky upload `useitacDev` buildu do Google Play `internal` tracku cez Gradle Play Publisher.

## Co treba urobit len raz

1. V Google Cloud projekte pre tvoju Play appku zapni `Android Publisher API`.
2. Vytvor `service account` a JSON key.
3. V Google Play Console otvor `Nastavenia` -> `Pristup k rozhraniu API`.
4. Prepoj Play Console s Google Cloud projektom.
5. Pridaj service account ako pouzivatela do Play Console pre appku `UseIT Chat`.

Minimalne odporucane opravnenia pre tento flow:

- vydania do testovacich kanalov
- zobrazovanie informacii o aplikacii

## Lokalna priprava

Service account JSON nedavaj do gitu. Mas 2 moznosti:

1. Jednorazovo pri publishi:
   - `scripts\publish-google-play.ps1 -ServiceAccountJsonPath C:\secure\useitac-play-service-account.json`

2. Trvale cez lokalnu ENV premennu:
   - `USEITAC_DEV_PLAY_SERVICE_ACCOUNT_JSON=C:\secure\useitac-play-service-account.json`

Skript si sam nacita JSON a pre publish ho odovzda pluginu cez `ANDROID_PUBLISHER_CREDENTIALS`.

## Publish pouzitie

Zakladny upload do internal tracku:

```powershell
scripts\publish-google-play.ps1 -ServiceAccountJsonPath C:\secure\useitac-play-service-account.json
```

Upload s vlastnym release menom a poznamkou:

```powershell
scripts\publish-google-play.ps1 `
  -ServiceAccountJsonPath C:\secure\useitac-play-service-account.json `
  -VersionCode 260851033 `
  -VersionName "0.1.10" `
  -ReleaseStatus draft `
  -ReleaseName "UseIT Chat 0.1.10" `
  -ReleaseNotes "Nova testovacia verzia."
```

Upload uz pripraveneho AAB bez rebuild-u:

```powershell
scripts\publish-google-play.ps1 `
  -ServiceAccountJsonPath C:\secure\useitac-play-service-account.json `
  -SkipBuild
```

## Co skript robi

- skontroluje `keystore.properties`
- nacita service account JSON
- nastavi `versionCode` a `versionName` pre build
- doplni release notes pre `useitacDev`
- spusti Gradle task `publishUseitacDevReleaseBundle`
- cieli na track `internal`
- vie poslat release aj ako `draft`, co je potrebne, kym je Play appka stale v stave konceptu
- ak `-VersionCode` nevyplnis, skript automaticky vygeneruje rastuci `versionCode` podla aktualneho casu v pasme `Europe/Bratislava`

## Poznamky

- Prvu verziu appky treba nahrat do Play Console manualne. Dalsie vydania uz mozu ist cez tento automaticky flow.
- Track vies zmenit parametrom `-Track`, ale pre developer verziu odporucany zostava `internal`.
- Ak chces, aby sa auth zmeny dostali k realnym pouzivatelom, novu verziu je potrebne naozaj nahrat do Google Play; samotna zmena v repozitari nestaci.
