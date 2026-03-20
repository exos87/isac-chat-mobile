# Implementation Plan

Plan je zoradeny tak, aby sme co najrychlejsie dosiahli funkcionalnu paritu s existujucim web widgetom.

## Faza 0: hotovo v tejto iteracii

- zalozenie Android projektu
- Compose navigation a obrazovky
- repository vrstva pre hlavne chat endpointy
- STOMP skeleton pre realtime
- dokumentacia a mapovanie API

## Faza 1: minimalna funkcna verzia

Ciel:

- otvorit appku
- nacitat conversations
- otvorit konverzaciu
- citat a odosielat spravy
- vidiet unread badge a realtime refresh

Kroky:

1. otvorit projekt v Android Studio a nechat dobehnut Gradle sync
2. overit compile errors po prvom syncu a doladit verzie dependency
3. pripojit emulator na lokalny `isac-chat` backend
4. overit REST flow:
   - conversations
   - conversation detail
   - messages
   - send message
5. overit STOMP flow:
   - unread badge
   - refresh conversation list
   - presence

Definition of done:

- vieme sa pripojit cez bearer token
- vieme zobrazit zoznam chatov
- vieme odoslat spravu a vidiet ju po refreshi / realtime

## Faza 2: widget parity pre spravy

Ciel:

- spravy sa budu spravat rovnako ako vo widgete

Kroky:

1. mark-as-read pri otvoreni konverzacie
2. lepsie rozlisenie direct/group/action konverzacii
3. zobrazenie participant badges a role
4. dorobit composer pre `MASTER_ONLY`
5. delete vlastnej spravy

Definition of done:

- detail konverzacie posobi rovnako ako messenger cast widgetu

## Faza 3: novy chat a skupiny

Ciel:

- zakladat direct a group chaty rovnako ako vo widgete

Kroky:

1. directory/workforce picker
2. direct conversation create/open
3. group draft:
   - title
   - vyber clenov
   - create
4. handling `externalReference`
5. planner/task group chat otvaranie bez duplikacie

Definition of done:

- mobil vie zalozit a otvorit direct aj group chat

## Faza 4: group management parity

Ciel:

- `GROUP_OPEN` sprava skupiny

Kroky:

1. rename group
2. add members
3. remove members
4. update member role
5. leave conversation

Definition of done:

- vlastnik/spravca vie spravovat skupinu z mobilu

## Faza 5: approvals parity

Ciel:

- rovnake approval flow ako v widgete

Kroky:

1. vyber cielovej spravy
2. competent picker cez directory/workforce
3. proposal code + proposal text
4. approval case detail
5. approve / changes required / reject

Definition of done:

- mobil pokryva celu kartu `Akcie`

## Faza 6: attachments

Ciel:

- attachment flow ako vo widgete

Kroky:

1. system file picker
2. upload po odoslani spravy
3. preview loading
4. download/open
5. delete attachment
6. scan status a preview availability v UI

Definition of done:

- attachmenty sa daju realne pouzivat v produkcii

## Faza 7: production hardening

Kroky:

1. realny login flow cez Keycloak / ISAC auth
2. secure token storage review
3. reconnect/backoff a offline stav
4. telemetry/logging
5. smoke test scenare
6. QA checklist proti widgetu

## Odporucany najblizsi krok

Ako dalsi krok by som siel spravit toto:

1. otvorit projekt v Android Studio
2. nechat prebehnut sync
3. opravit pripadne compile detaily po prvom syncu
4. potom napojit realny emulator na lokalny `isac-chat`
5. dotiahnut fazu 1 do plne funkcnej podoby

