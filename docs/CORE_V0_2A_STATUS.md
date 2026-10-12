# ПАРИТМ v0.2-A — Core delivery note

**Status:** code integrated to `main` via [PR #4](https://github.com/Antariyami/terensai-rhythm-android/pull/4); CI tests and debug APK build passed on [main](https://github.com/Antariyami/terensai-rhythm-android/actions/runs/38320047040). This is a **backend/core milestone**, not a new feature-complete app.

## Available in code
- Kotlin pure domain: `ProgramPlan`, `StagePlan`, `SessionPlan`, `SessionEvent`, `TimelineEngine`, `Clock`.
- Independent tracks `GUEST`, `MASTER`; plan and fact are separate.
- Deterministic projection from event log, idempotency for the same event ID and invalid transition guards.
- Domain time can be injected with a fake Clock for repeatable unit tests; Android UI has not been switched to new engine.
- Room database: `programs`, `sessions`, `session_events` tables, foreign key, index, non-destructive data handling. Stored sessions keep `snapshotJson` of the program.
- Non-destructive v0.1 SharedPreferences importer **prepared but not enabled** until old UI is replaced; old journal is preserved raw, without inventing historical timestamps.
- 11 JVM tests for pause, wall-clock timing, duplicate completes, independent tracks, time drift, future events, validation and legacy record handling.
- CI runs both `testDebugUnitTest` and `assembleDebug`.

## Not yet done
- Actual v0.1 screen still operates on SharedPreferences and has old branding.
- No user-visible Room archive, migration activation or export in this release.
- No Compose UI, unified plan/fact/forecast timeline, tiles, custom program editor, WhatsApp export or notifications.
- No Room schema upgrade tests (first Room DB version only); no Android instrumented tests or device validation.
- No permanent APK signing key; artifacts use CI debug keys that may differ per build. **Do not install over your existing app without first exporting and protecting its data.**

## Next iteration
0.2-B: connect read-only programs screen and a new session screen to the domain and Room, activate migration only at a safe switch boundary, implement real archive list and report share after data persistence is verified. Maintain a development/test APK separate from production signing.
