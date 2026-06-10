# FCM Setup

Mobilna aplikacia ma pripraveny zaklad pre Firebase Cloud Messaging a backend `isac` uz vie odosielat chat push notifikacie cez Firebase Admin.

## Co je uz hotove

- Android app ma `FirebaseMessagingService`
- FCM token sa po prihlaseni synchronizuje do `profile/preferences.modules.mobileApp`
- backend `isac` vie z chat eventov odoslat FCM push
- ak pride push payload s `type`, `title`, `body` a `conversationId`, app ho vie typovane spracovat, zobrazit a otvorit konkretny chat

## Co este treba doplnit

1. vytvorit Firebase projekt pre prislusny flavor alebo produkt
2. pridat `google-services.json` do `app/src/<flavor>/google-services.json`
3. dodat Firebase service account JSON pre backend `isac`
4. zapnut backend konfiguraciu:
   - `NOTIFICATIONS_CHAT_PUSH_ENABLED=true`
   - `NOTIFICATIONS_CHAT_PUSH_SERVICE_ACCOUNT_PATH=/run/secrets/isac-chat-firebase.json`
   - `NOTIFICATIONS_CHAT_PUSH_FIREBASE_PROJECT_ID=<volitelne>`

## Docker Compose odporucanie

Pre lokalny alebo hosted docker deployment je pripravene jednoduche pravidlo:

1. vloz Firebase Admin JSON do:
   - `C:\\Users\\JL\\IdeaProjects\\isac-devops\\secrets\\isac-chat-firebase.json`
2. v `.env` alebo prostredi nastav:
   - `NOTIFICATIONS_CHAT_PUSH_ENABLED=true`
   - `NOTIFICATIONS_CHAT_PUSH_FIREBASE_PROJECT_ID=<firebase-project-id>`
3. `docker compose` uz mountuje `./secrets` do kontajnera na `/run/secrets`

Ak subor chyba alebo push nie je zapnuty, backend zostane funkcny a pri starte jasne zaloguje, ci je chat push vypnuty, nekompletny alebo pripraveny.

## Odporucany payload

```json
{
  "notification": {
    "title": "UseIT Chat",
    "body": "Mas novu spravu."
  },
  "data": {
    "type": "chat-message",
    "conversationId": "12345",
    "messageId": "98765",
    "title": "UseIT Chat",
    "body": "Mas novu spravu.",
    "externalReference": "HLASENIE:90010"
  }
}
```

## Poznamka

Ak backend FCM konfiguracia alebo mobilny `google-services.json` chyba, app aj backend maju zostat funkcne bez hard failu. Push vrstva sa ma dat zapnut postupne po prostrediach.

## Rychla diagnostika z mobilu

Po prihlaseni otvor servisne nastavenia session obrazovky a pouzi:

- `Načítať stav push`
- `Poslať test push`

Tieto akcie volaju backend endpointy:

- `GET /notifications/push/chat/status`
- `POST /notifications/push/chat/test`

Pomahaju rychlo overit:

- ci je chat push v prostredi zapnuty a kompletne nakonfigurovany
- ci ma aktualny pouzivatel registrovane overene mobilne zariadenie
- ci backend vie odoslat synteticku testovaciu push notifikaciu na aktualne zariadenie
- aky je aktualny stav registracie zariadenia, balik appky, maskovany token a posledna push chyba

## Attachment payload

```json
{
  "data": {
    "type": "chat-attachment",
    "conversationId": "12345",
    "messageId": "98765",
    "attachmentId": "555",
    "fileName": "IMG_2255.JPG",
    "contentType": "image/jpeg",
    "previewAvailable": "true",
    "senderDisplayName": "Katarina Lovasova",
    "title": "UseIT Chat",
    "body": "Poslal(a) prilohu: IMG_2255.JPG"
  }
}
```
