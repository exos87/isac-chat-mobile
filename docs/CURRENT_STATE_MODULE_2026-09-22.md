# isac-chat-mobile – current-state module documentation

Dátum overenia: 2026-09-22  
Baseline: 5c14529387372a44e31506a109c9f3f5d8c881b6 (origin/main)  
Status: VERIFIED SOURCE; Play/release status PARTIAL

## Hranica modulu

**Build/runtime tvar:** Standalone Kotlin/Gradle Android client.  
**Vlastnený rozsah:** Chat, push, SSO and mobile attachment/user flow.  
**Hlavné väzby:** isac-chat, Keycloak and generated/API contracts.

## Dôkazová legenda

- **VERIFIED SOURCE** – hranica a artefakty sú potvrdené v auditovanom repozitári.
- **PARTIAL** – implementácia existuje, ale chýba remote, deploy, runtime alebo acceptance gate.
- **PLANNED** – cieľ/backlog, nie tvrdenie o dnešnej implementácii.
- **RUNTIME ACCEPTED** – vyžaduje datovaný dôkaz konkrétneho prostredia a tenant/user scope.
- **BLOCKED** – ďalšie tvrdenie je zastavené známou chýbajúcou podmienkou.

Source baseline nepreukazuje image, bežiaci runtime, produkciu ani zákaznícku akceptáciu. Diagram v docs/architecture/current-state.puml je udržiavateľný zdrojový náčrt hranice modulu, nie deployment diagram.

Exportovateľná trojsekčná vizualizácia: [current-state.svg](architecture/current-state.svg). Centrálny album s produktovým a modulovými SVG je v dokumentačnom repozitári `isac-devops/produkt dokumentacia/30-Modules/visuals/2026-09-22`.

Podrobná UML dokumentácia: [architecture/README.md](architecture/README.md) obsahuje aplikačné vrstvy, integrácie/nasadenie a dátový model alebo explicitnú hranicu bez vlastnej DB.
