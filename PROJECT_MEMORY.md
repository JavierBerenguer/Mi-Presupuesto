# PROJECT MEMORY — MI PATRIMONIO

## 1. ESTADO ACTUAL DEL PROYECTO

- Fase actual: FASE 2 completada; comienza FASE 3 (MVP financiero)
- Última tarea completada: T-002 (proyecto base Kotlin + Compose, compila y genera APK de depuración)
- Tarea en curso: ninguna
- Próxima tarea: T-003 (diseño del dominio financiero y modelo Room; ver sección 11)
- Estado de compilación: `./gradlew.bat assembleDebug` → BUILD SUCCESSFUL (2026-09-22)
- Última prueba ejecutada: ninguna (aún no hay tests; solo dependencias JUnit/coroutines-test declaradas)
- Último APK generado: app/build/outputs/apk/debug/app-debug.apk (esqueleto de navegación, sin lógica) — ver sección 8
- Bloqueos actuales: ninguno

## 2. RESUMEN EJECUTIVO

Repo con estructura Claude/Codex (CLAUDE.md, AGENTS.md, docs/tasks/TEMPLATE.md, scripts/delegate-codex.ps1) y ahora un proyecto Android real: Gradle 9.7.1 (wrapper con checksum), AGP 9.4.1, Kotlin 2.4.20, Compose (BOM 2026.09.00), Material 3, tema oscuro por defecto/claro opcional, navegación con 5 secciones (Inicio, Movimientos, Presupuestos, Inversiones, Patrimonio) + Ajustes en la barra superior. Todas las pantallas son estados vacíos: NO hay dominio, base de datos ni lógica financiera todavía. Room/DataStore/WorkManager están declarados como dependencias pero sin usar. Entorno: JDK 17 y Android SDK instalados por usuario (sin admin).

## 3. DECISIONES TÉCNICAS

- D-001 (2026-09-22): Kotlin + Jetpack Compose (frente a Flutter/RN). Justificación: acceso nativo a NotificationListenerService, Keystore, WorkManager y Room; sin capa puente. Consecuencia: toda la app en Kotlin, MVVM + Clean Architecture.
- D-002 (2026-09-22): Package/applicationId `com.mipatrimonio.app`; minSdk 26, targetSdk 36, compileSdk 37 (las androidx actuales exigen compileSdk 37). Room 2.8.5 con KSP 2.3.12.
- D-003 (2026-09-22): AGP 9 trae Kotlin integrado, por eso el módulo app NO aplica `kotlin-android`; solo `com.android.application`, `kotlin.plugin.compose` y `ksp`.
- D-004 (2026-09-22): `allowBackup=false` en el manifiesto (datos financieros; las copias serán las propias cifradas de la app, Fase 8). Sin permiso INTERNET por ahora (se añadirá con cotizaciones/Supabase, Fase 9).
- D-005 (2026-09-22): política git (CLAUDE.md §35): nunca commit/push a `main`; trabajo propio en `claude/<descripcion>` con commit y push automáticos (rutas concretas, sin `git add -A`); merge a main solo con autorización explícita. Rama actual: `claude/fase-2-proyecto-base` (commit 65b5788, subida a origin). `Claude.md` modificado por el usuario, sin commitear (no es mío).
- Pendiente de decidir en T-003: representación del dinero (BigDecimal vs. céntimos Long), inyección de dependencias (manual vs. Hilt).

## 4. TAREAS PENDIENTES

| ID | Objetivo | Prioridad | Dependencias | Estado |
|---|---|---|---|---|
| T-003 | Dominio: `Money`/redondeo/divisa, entidades y reglas de saldo; esquema Room v1 (cuentas, categorías, movimientos, transferencias) con tests | Alta | T-002 | PENDIENTE |
| T-004 | Pantallas MVP (cuentas, movimientos, categorías) — delegable a Codex con ficha | Alta | T-003 | PENDIENTE |
| T-005 | Presupuestos + dashboard + patrimonio básico | Alta | T-004 | PENDIENTE |
| T-006 | Inversiones básicas (carteras, activos, compras/ventas, precios manuales) | Alta | T-005 | PENDIENTE |
| T-007 | Primer APK verificado con tests (Fase 5) | Alta | T-006 | PENDIENTE |

## 5. TAREA ACTUAL

Ninguna en curso. Última cerrada: T-002 (ver sección 6).

## 6. HISTORIAL DE TAREAS

### T-000 — Inspección del entorno — COMPLETADA (2026-09-22)
- Resultado: Windows 11 (10.0.26200); git 2.53.0; Node v24.14.0; winget; Java 1.8.0_391 (insuficiente); sin Gradle/adb/sdkmanager; sin ANDROID_HOME/JAVA_HOME; sin SDK; disco C: 211 GB libres.

### T-001 — JDK 17 y Android SDK — COMPLETADA (2026-09-22)
- winget no permite instalar Temurin 17 sin elevación → ZIP oficial de Adoptium (API api.adoptium.net), SHA-256 verificado. JDK 17.0.20.1 en `%LOCALAPPDATA%\Programs\Java\jdk-17.0.20.1+1`; JAVA_HOME (usuario). El Java 8 del PATH no se tocó.
- cmdline-tools `commandlinetools-win-15859902_latest.zip`, SHA-256 verificado contra developer.android.com/studio → `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`. Licencias aceptadas (`yes | sdkmanager --licenses` desde Bash; la tubería de PowerShell no funcionó).
- Instalados (`--list_installed`): build-tools;35.0.1, platform-tools 37.0.1, platforms;android-35, android-36, android-37.0. ANDROID_HOME (usuario).
- Verificación: salida real de `java -version`, `sdkmanager --list_installed`, `adb --version`.

### T-002 — Proyecto base Kotlin + Compose — COMPLETADA (2026-09-22)
- Archivos: settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradlew(.bat), gradle/wrapper/*, local.properties (ignorado por git), app/build.gradle.kts, app/proguard-rules.pro, app/src/main/{AndroidManifest.xml, res/values/{strings,themes,colors}.xml, java/com/mipatrimonio/app/{MainActivity.kt, ui/theme/Theme.kt, ui/navigation/{Destino.kt,MiPatrimonioApp.kt}}}.
- Wrapper generado en directorio temporal vacío (la tarea `wrapper` falla dentro del proyecto si la configuración no resuelve) usando Gradle 9.7.1 descargado y verificado (SHA-256 acd53f1e…d20a) con `distributionSha256Sum` fijado.
- Verificación: `./gradlew.bat assembleDebug` → BUILD SUCCESSFUL, sin avisos; `aapt2 dump badging` confirma package com.mipatrimonio.app, versionName 0.1.0, label "Mi Patrimonio", targetSdk 36.
- NO verificado: instalación/ejecución en dispositivo o emulador (no hay ninguno configurado).

## 7. REGISTRO DE ERRORES

- E-001 — CORREGIDO (2026-09-22, T-002): `gradle wrapper` dentro del proyecto falla ("does not contain a Gradle build" sin settings; luego, fallo de configuración). Solución: generar el wrapper en un directorio temporal con `settings.gradle.kts` vacío y copiarlo. Verificado con `./gradlew.bat --version`.
- E-002 — CORREGIDO (T-002): `settings.gradle.kts` con `"com\.android.*"` → "Unsupported escape sequence" (se perdió una barra al escribirlo). Solución: `"com\\.android.*"`. Verificado: el build avanzó.
- E-003 — CORREGIDO (T-002): checkAarMetadata exigía compileSdk ≥ 37 por las androidx recientes (core-ktx 1.19.0, compose-ui 1.12.1, lifecycle 2.11.0…). Solución: instalar platforms;android-37.0 y `compileSdk = 37` (targetSdk sigue en 36). Verificado: BUILD SUCCESSFUL.
- Nota operativa: en Bash, un script con muchos heredocs anidados falló al analizarse ("unexpected EOF"); usar la herramienta Write para crear archivos de código.

## 8. COMPILACIONES Y PRUEBAS

- 2026-09-22 `./gradlew.bat assembleDebug`: 1º intento FALLÓ (E-002), 2º FALLÓ (E-003), 3º OK (19 s) con aviso de icono deprecado, 4º OK sin avisos tras usar `Icons.AutoMirrored.Filled.TrendingUp`.
- APK de DEPURACIÓN (no apto para publicar): `C:\Users\Usuario\Desktop\apk finanzas\app\build\outputs\apk\debug\app-debug.apk`, 20 433 805 bytes, SHA-256 `84043ed83d7f83163a6ca3262788cc86b8f910b63bd224ec197a4e45bc02d351`. Es solo el esqueleto de navegación.
- Tests: ninguno ejecutado todavía.

## 9. DEPENDENCIAS Y CONFIGURACIÓN

- Verificadas contra Maven/Google repos el 2026-09-22: Gradle 9.7.1, AGP 9.4.1, Kotlin 2.4.20, KSP 2.3.12, Compose BOM 2026.09.00, Room 2.8.5, activity-compose 1.13.0, navigation-compose 2.10.1, lifecycle 2.11.0, DataStore 1.2.1, WorkManager 2.11.2, coroutines 1.11.0, core-ktx 1.19.0, JUnit 4.13.2.
- Herramientas: JDK Temurin 17.0.20.1, Android SDK (cmdline-tools 15859902, build-tools 35.0.1, platform-tools 37.0.1, platforms 35/36/37.0). Java 8 sigue en el PATH; usar JAVA_HOME (las terminales abiertas antes de fijarla no la ven; en Bash: `export JAVA_HOME="$LOCALAPPDATA/Programs/Java/jdk-17.0.20.1+1"`).
- Variables de entorno (solo nombres): JAVA_HOME, ANDROID_HOME. `local.properties` local con `sdk.dir`.
- Pendiente: firma de APK de distribución (no hay keystore; no se crea sin datos del usuario), documentación README/ARCHITECTURE/ROADMAP.

## 10. PROBLEMAS Y BLOQUEOS

(ninguno)

## 11. PRÓXIMOS PASOS

1. Registrar T-003 como EN CURSO y decidir Money: propuesta = importes en céntimos `Long` + `BigDecimal` para cálculos intermedios (tipos de cambio, cantidades de activos con decimales) con redondeo HALF_EVEN documentado. Crear `domain/model/Money.kt` y `domain/model/Currency` con tests unitarios (importes negativos, división por cero, redondeo).
2. Esquema Room v1 (`Account`, `Category`, `Transaction`, `Transfer`) con saldo derivado (saldo inicial + movimientos), `exportSchema=true`, y test de los casos: ingreso sube saldo, gasto lo baja, transferencia no es gasto y conserva patrimonio.
3. Redactar fichas en `docs/tasks/` (T-004…) y delegar UI a Codex vía `scripts/delegate-codex.ps1`.
4. Añadir `.gitattributes` (`* text=auto`, `gradlew text eol=lf`, `*.bat eol=crlf`) para que git no convierta `gradlew` a CRLF; empezar T-003 en una rama nueva `claude/fase-3-dominio` desde `main` actualizado (o desde la rama actual si el usuario aún no ha fusionado).

## 12. REGISTRO CRONOLÓGICO DE SESIONES

- 2026-09-22 (sesión 1): FASE 0, T-000; se crea PROJECT_MEMORY.md; inicio de T-001 (JDK).
- 2026-09-22 (sesión 2): estado real == memoria; T-001 completada (SDK); T-002 completada (proyecto base + primer APK de esqueleto). Errores E-001…E-003 corregidos. Próxima acción: T-003.
