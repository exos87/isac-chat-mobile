# API Mapping

Mapovanie medzi web widgetom a mobilnym klientom.

## Overene backend endpointy

Pouzite podla `isac-chat/README.md` a backend controllerov:

- `GET /api/chat/conversations`
- `POST /api/chat/conversations`
- `GET /api/chat/conversations/{conversationId}`
- `PATCH /api/chat/conversations/{conversationId}`
- `POST /api/chat/conversations/{conversationId}/members`
- `PATCH /api/chat/conversations/{conversationId}/members/{memberId}`
- `POST /api/chat/conversations/{conversationId}/leave`
- `GET /api/chat/conversations/{conversationId}/messages`
- `POST /api/chat/conversations/{conversationId}/messages`
- `POST /api/chat/messages/{messageId}/read`
- `DELETE /api/chat/messages/{messageId}`
- `GET /api/chat/messages/{messageId}/attachments`
- `POST /api/chat/messages/{messageId}/approval-cases`
- `GET /api/chat/conversations/{conversationId}/approval-cases`
- `POST /api/chat/approvals/{approvalCaseId}/decisions`
- `GET /api/chat/directory/users`
- `GET /api/chat/me/unread-count`
- `GET /api/ws/chat?access_token=<jwt>`

## Web widget -> mobile screen

### 1. Session bootstrap

Web widget:

- parent Angular app injectuje token cez `postMessage`

Mobil:

- uvodna `SessionScreen`
- zatial manualne zadanie:
  - `baseUrl`
  - `wsUrl`
  - bearer token

### 2. Conversation list

Web widget:

- sidebar
- tabs `Chaty`, `Skupiny`, `Akcie`
- unread badge
- search

Mobil:

- `HomeScreen`
- `TabRow` pre rovnake tri sekcie
- search field
- unread badge per tab aj global count

### 3. Conversation detail

Web widget:

- header s titulom a clenmi
- messenger history
- composer

Mobil:

- `ConversationScreen`
- message pane
- composer s podporou `VisibilityScope`

### 4. Approval flow

Web widget:

- karta `Akcie`
- approval request z vlastnej spravy
- decision buttons

Mobil:

- `ConversationPane.ACTIONS`
- list approval case-ov
- quick approval request skeleton
- approve / changes required / reject

### 5. Presence

Web widget:

- STOMP subscribe na `/topic/chat/presence`

Mobil:

- `StompChatRealtimeClient`
- event `PresenceUpdated`
- aktualizacia listu konverzacii a directory cache

## Realtime mapping

Subscribe destinationy rovnake ako widget:

- `/user/queue/chat/badge`
- `/user/queue/chat/conversations`
- `/user/queue/chat/approvals`
- `/topic/chat/presence`

Spravanie v mobilnej kostre:

- badge event updatuje global unread count
- conversations event spusti tichy refresh dashboardu
- approvals event spusti tichy refresh
- presence event upravi `online` stav v lokálnom UI state

## Dolezite otvorene rozhodnutia

1. Auth flow
   Mobil potrebuje samostatny login proti Keycloak/ISAC auth, lebo chat backend sam o sebe token nevydava.

2. Attachment UX
   Backend attachment flow je pripraveny, ale na mobile treba navrhnut picker, upload progres a preview strategiu.

3. Approval competent picker
   Widget pouziva workforce-backed directory. Mobil to zatial nahradza jednoduchym quick flow a treba dorobit plny picker.

