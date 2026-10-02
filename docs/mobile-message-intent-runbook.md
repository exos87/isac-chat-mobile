# Durable message intents

Keyed conversation sends carry optional canonical `clientMessageId` UUID. The owner-bound retry record is committed before the first request; save/owner failure prevents dispatch. The same immutable body, visibility and attachments accompany the same key after timeout, reconnect or ViewModel/store recreation. An intentional subsequent equal-text message uses a new UUID after the preceding result is known. Captured owner and session epoch still fence outgoing credentials/work.

Server compatibility is required: keyed replay of the same request returns201/original message, changed request409, deleted original410. An optional key means legacy unkeyed calls do not acquire the guarantee. The initial-conversation convenience message still uses the pre-existing unkeyed helper. Attachment upload is a separate idempotency contract; no exactly-once consumer/broker delivery claim is made.

Legacy uncertain message retries without a valid key remain quarantined and cannot silently receive a new UUID. The user must verify the conversation and explicitly clear the composer before creating a new intent. Malformed nonempty retry records (including blank raw, unknown kind or missing required attachment message ID) fail closed without erasure. An uncertain keyed message's composer fields remain fixed until resolution.

Retry mutations are serialized in-process across store instances. Cleanup compares the completed record under the same lock, so old cleanup cannot delete a newer pending record. Message retry is saved once before dispatch rather than re-saved asynchronously after a failure; attachment transitions are awaited. Cleanup failure retains safe original identity and surfaces a sanitized error. Android multiprocess storage/OS kill and device persistence acceptance are not established by JVM proxy fixtures.

Only run with the coordinator's serialized JVM slot:

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME='C:/Users/JL/AppData/Local/Android/Sdk'
.\gradlew.bat testUseitacDevDebugUnitTest --tests '*ConversationViewModelTest' --tests '*OwnedConversationStoreTest' --tests '*MessageIntentWireTest' --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process' --init-script D:/CodexArtifacts/useit-product-review-remediation-20261002/wave6/mobile-test-memory.gradle
```

External init script caps test JVM at256m/one fork; Gradle heap768m, combined configured heaps1024m plus native overhead, estimated approximately1.3GB unmeasured. Android SDK and ignored existing flavor Google configuration are reused; generated build/flavor configuration/evidence are excluded from Git. Final targeted gate: 36/36 PASS, zero failures/errors/skips (ConversationViewModelTest 30, OwnedConversationStoreTest 5, MessageIntentWireTest 1), BUILD SUCCESSFUL in 31 seconds. Final XML and logs are external. All build JVMs ended before slot release.

Prepared cases cover lost response against a simulated keyed committed server, pre-dispatch persistence, reload, save failure, immutable request, equal text as a new intent, legacy quarantine, delayed compare-cleanup, raw corrupt store preservation and HTTP wire201/409/410. They do not themselves prove server uniqueness. Original Chat baseline and real persistence/server gates own duplicate message/event, transactional rollback and race acceptance. A controlled mobile dispatch-key omission script is preserved externally but remains NOT_RUN and is not needed when the original server baseline reproduces the finding.

Working process-only runtime: existing Android Studio JBR 21.0.9 (21.0.9+-14649483-b1163.86). JDK 25 fails Gradle 8.7 before tasks with unsupported class major version 69; that iteration is NOT_RUN for functional acceptance. The first JBR run compiled successfully and executed 36 cases, with two error-visibility failures because refresh cleared corrupt/legacy quarantine reasons. Refresh now preserves those reasons; assertions remain unchanged.
