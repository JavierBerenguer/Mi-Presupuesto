# PROJECT MEMORY — MI PATRIMONIO

## 1. ESTADO ACTUAL DEL PROYECTO

- Fase actual: FASE 3 (MVP financiero) + FASE 4 (inversiones básicas) + FASE 5 (primer APK)
- Última tarea completada: T-002 (proyecto base Kotlin + Compose, compila y genera APK de depuración)
- Tarea en curso: UI del MVP escrita directamente por Claude (decisión del usuario, 2026-09-22), en rama `claude/fase-3-mvp`; tareas Codex T-006/8/9/10 CANCELADAS (parcial en worktrees, solo consultable)
- Próxima tarea: T-003 (diseño del dominio financiero y modelo Room; ver sección 11)
- Estado de compilación: `./gradlew.bat assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL (2026-09-22, sesión 4)
- Última prueba ejecutada: suite unitaria completa OK + **verificación manual en emulador Android 15 (API 35)**: la app arranca sin crashes y los flujos de cuentas, movimientos, presupuestos, inversiones y patrimonio funcionan con datos reales (ver sección 18)
- Último APK generado: app/build/outputs/apk/debug/app-debug.apk (MVP completo, instalado y ejecutado en emulador) — ver secciones 8 y 18
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
| T-006…T-012 | UI delegada a Codex (ver sección 6, «Reparto de la UI con Codex») | Alta | T-005 | INTERRUMPIDAS T-006, T-008, T-009, T-010 (ver sección 15); T-007, T-011, T-012 sin lanzar |
| T-013 | (Claude) revisar diffs, integrar ramas codex, navegación (MiPatrimonioApp/MainActivity/tema desde ajustes), tests + assembleDebug | Alta | T-006…T-012 | PENDIENTE |
| T-014 | Primer APK verificado (Fase 5) + documentación README/ARCHITECTURE/ROADMAP | Alta | T-013 | PENDIENTE |
| Fases 6-10 | Notificaciones bancarias, recurrentes, dividendos UI, multidivisa real, objetivos, importación/exportación, copias cifradas, cotizaciones, Supabase | Media | Primer APK | PENDIENTE (post-versión inicial) |

## 5. TAREA ACTUAL

### T-003 — Dominio financiero — COMPLETADA (2026-09-22)
- Objetivo: `Money`, cálculo de saldos, presupuestos, posiciones de inversión (coste medio ponderado), patrimonio; todo lógica pura en `domain/` con tests JUnit.
- Archivos previstos: app/src/main/java/com/mipatrimonio/app/domain/**, app/src/test/**.
- Verificación: `./gradlew.bat testDebugUnitTest`.
- Decisión: importes en céntimos `Long` (2 decimales; conversión y cantidades de activos con `BigDecimal`, redondeo HALF_EVEN). Sin multidivisa real en el MVP: solo se suma la divisa base; el resto se excluye y se marca patrimonio parcial (no se inventan tipos de cambio).
- Reparto: Codex NO se usa en esta sesión (su script parte de `main`, que aún no contiene el proyecto; y la autonomía pedida por el usuario). Claude implementa todo directamente.
- Resultado: domain/model/{Money,Models}.kt y domain/calc/{Balance,Stats,Budget,Position,NetWorth}Calculator.kt. Verificado: `./gradlew.bat testDebugUnitTest` → BUILD SUCCESSFUL, 34 tests (LedgerTest 15, MoneyTest 8, PositionTest 11), 0 fallos.
- Reglas fijadas: saldo derivado; transferencia no es ingreso/gasto; presupuesto usa gastos de su periodo/categoría (+subcategorías) y aviso al 80 %; posición por coste medio ponderado con comisiones capitalizadas; rentabilidad = simple no realizada (no TWR/MWR); patrimonio siempre marcado parcial (sin pasivos) y excluye divisas sin tipo de cambio.

### T-004 — Room v1 + repositorios + DI manual — COMPLETADA (2026-09-22)
- Objetivo: entidades/DAOs Room (cuentas, categorías, movimientos, transferencias, presupuestos, carteras, activos, operaciones, precios, ajustes), mappers, repositorios (Flow) y `AppContainer`; categorías por defecto sembradas en primer arranque.
- Archivos previstos: app/src/main/java/com/mipatrimonio/app/data/**, `MiPatrimonioApplication.kt`, manifest (android:name).
- Verificación: compilación KSP + `assembleDebug`; esquema exportado en app/schemas.
- Resultado: data/db (Entities, Daos, AppDatabase v1 con FKs RESTRICT y esquema exportado en app/schemas/1.json, Mappers), data/repository (Ledger, Investment, Settings con DataStore, DefaultCategories), AppContainer + MiPatrimonioApplication. Sin TypeConverters: enums como texto, BigDecimal como texto, fechas epochDay.
- Verificación: `testDebugUnitTest` → 43 tests OK (RepositoryTest 9 con Robolectric 4.17 + Room en memoria: persistencia, saldo derivado, no duplicar al editar, validaciones, FK impide borrar cuenta con movimientos, ventas en descubierto rechazadas, precios manuales). `assembleDebug` OK.
- Nota: Robolectric requiere `@Config(sdk=[34])` (el SDK objetivo 36/37 puede no estar soportado).

### T-005 — Base de UI común + capa de agregación — COMPLETADA (2026-09-22)
- Claude: domain/usecase/{FinanceSnapshot,Period,HistoryCalculator}.kt (+SnapshotTest, 6 tests), ui/common/{ViewModelFactory,Labels,Components}.kt, res/values/strings_common.xml. `testDebugUnitTest` 49 tests OK; `assembleDebug` OK.
- Histórico de patrimonio: efectivo por fecha + inversiones a COSTE (no hay histórico de cotizaciones); documentado en HistoryCalculator.

### Reparto de la UI con Codex (autorizado por el usuario, sin coste extra: login ChatGPT verificado con `codex login status`)
Base de las ramas codex: `claude/fase-3-mvp`. Cada tarea es dueña de su carpeta `ui/<feature>/` y de su `res/values/strings_<feature>.xml`; la navegación (MiPatrimonioApp.kt, MainActivity.kt) la integra Claude.
| Ficha | Alcance | Estado |
|---|---|---|
| T-006 | Movimientos (lista, filtros, formularios ingreso/gasto y transferencia) | INTERRUMPIDA (rama codex/t-006-movimientos; código parcial sin commitear en el worktree) |
| T-007 | Presupuestos | PENDIENTE |
| T-008 | Inversiones | INTERRUMPIDA (rama codex/t-008-inversiones; parcial sin commitear) |
| T-009 | Ajustes, cuentas y categorías | INTERRUMPIDA (rama codex/t-009-ajustes; parcial sin commitear) |
| T-010 | Gráficos Canvas (línea, barras, donut) | INTERRUMPIDA (rama codex/t-010-graficos; parcial sin commitear) |
| T-011 | Inicio (dashboard) — depende de T-010 | PENDIENTE |
| T-012 | Patrimonio — depende de T-010 | PENDIENTE |
| T-013 | (Claude) integración de navegación y tema, revisión de diffs, APK | PENDIENTE |

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

## 13. NOTAS OPERATIVAS DE DELEGACIÓN (Codex)
- Lanzar: PowerShell con `JAVA_HOME`, `ANDROID_HOME` y PATH definidos y `.\scripts\delegate-codex.ps1 -Task T-XXX -Slug <slug> -Base claude/fase-3-mvp` (el script usa `main` por defecto: hay que pasar `-Base`). Worktrees en `C:\Users\Usuario\Desktop\worktrees\<t-xxx-slug>`; informes en `docs/tasks/T-XXX.report.md` del repo principal.
- La máquina tiene 15 GB de RAM: como mucho 3-4 Codex/Gradle a la vez; parar mis daemons con `./gradlew.bat --stop` antes de lanzar tandas.
- Un Codex nuevo hereda las fichas solo si están commiteadas en la rama base.

## 14. DECISIONES/HALLAZGOS POSTERIORES
- 2026-09-22: el usuario aportó `datos-privados/trade-republic-exportacion.csv` (465 filas; ignorado por git vía `.gitignore`, nunca commitear). Hallazgos en ARCHITECTURE.md («Importación de Trade Republic»): `transaction_id` único = clave de deduplicación; `BUY PRIVATE_FUND` sin importe + `PRIVATE_MARKET_BUY` como salida de caja (no duplicar); MCC en tarjetas.
- Carencia de diseño (D-006, pendiente para Fase 7/8): operaciones de inversión sin cuenta de financiación → añadir `fundingAccountId` opcional (migración Room v2) y derivar el efecto en saldos. No se cambia en el MVP para no alterar el esquema v1 mientras Codex trabaja sobre él.

## 15. INTERRUPCIÓN DE CODEX POR MEMORIA (2026-09-22 ~02:15)
- Qué pasó: con 4 Codex + Gradle en paralelo, Claude Code detuvo las 4 tareas en segundo plano por memoria crítica (15 GB de RAM; ~0,5 GB libres). No es un fallo de las tareas. Instrucción del sistema: no relanzarlas por iniciativa propia; solo cuando el usuario lo pida.
- Estado real (verificado): cada worktree en `C:\Users\Usuario\Desktop\worktrees\t-00X-*` conserva ficheros SIN commitear (T-006: ui/movements + strings_movements.xml; T-008: ui/investments + strings_investments.xml + tests; T-009: ui/settings + strings_settings.xml + tests; T-010: ui/common/charts + tests + `.gradle-local/` a ignorar). Sin informes `.report.md`. Nada ha llegado a `claude/fase-3-mvp`.
- Compilación/tests de esas ramas: NO ejecutados ni verificados.
- Cómo retomar: relanzar de UNO en UNO (o dos como mucho) con `delegate-codex.ps1` (el worktree y la rama ya existen y se reutilizan); revisar primero lo parcial (`git -C ..\worktrees\<t> status`) y decir en el prompt que continúe desde lo existente. Orden recomendado: T-010 (gráficos, desbloquea T-011/T-012), luego T-006, T-009, T-008, T-007. Cerrar antes otras apps pesadas; parar daemons con `gradlew --stop`.
- Riesgo pendiente: el resto de tareas depende de estas; el primer APK con UI real está bloqueado hasta entonces. Alternativa si se prefiere: implementar la UI directamente por Claude (más lento en tokens pero sin carga de RAM).

## 16. CAMBIO DE PLAN: UI POR CLAUDE (2026-09-22)
- El usuario decide que Claude escriba la UI (respuesta a la interrupción por memoria). Las fichas T-006…T-012 se mantienen como especificación (docs/tasks) y Claude las implementa en `claude/fase-3-mvp`. Las ramas/worktrees de Codex quedan como CANCELADAS; su código parcial no se integra salvo revisión explícita.
- Orden: gráficos → ajustes/cuentas/categorías → movimientos → presupuestos → inversiones → inicio → patrimonio → navegación y tema → tests → APK.

## 17. REGLA DE CONCURRENCIA DE AGENTES (usuario, 2026-09-22)
- Como mucho **un Codex y un Claude a la vez**. Codex y Claude pueden trabajar simultáneamente entre sí, pero **nunca dos Codex ni dos Claude (subagentes incluidos) en paralelo**. Motivo: la tanda de 4 Codex saturó la RAM. Aplicar antes de cada `delegate-codex.ps1`: comprobar que no hay otro Codex en marcha.
- Estado: UI de las secciones 6/8/9/10 traída desde los worktrees de Codex a `claude/fase-3-mvp` (compila; tests 76 OK) y pendiente de revisión; presupuestos, inicio, patrimonio y navegación por escribir por Claude.

## 18. T-014 — Verificación en emulador (EN CURSO, 2026-09-22)
- Estado previo verificado: `assembleDebug` OK y 103 tests OK (12 suites) en `claude/fase-3-mvp` (commit b579b33). La UI completa está integrada (5 secciones + ajustes + formularios). NUNCA se ha ejecutado la app: riesgo de fallos de arranque/runtime.
- Plan: instalar emulator + system image x86_64 (android-35 google_apis) con sdkmanager (componentes del SDK; ~2 GB), crear AVD, instalar el APK, arrancar y revisar logcat; capturas con `adb exec-out screencap`.
- Restricción: poca RAM libre (~1,4 GB): parar daemons Gradle, AVD con ≤2 GB.

### 18.1. Progreso registrado (sesión 3, 2026-09-22)
- COMPLETADO: `emulator` 37.1.11 y `system-images;android-35;google_apis;x86_64` instalados en el SDK.
- COMPLETADO: AVD `mp_test` creado (aviso no fatal sobre `devices.xml` del system-image; no impide arrancar).
- COMPLETADO (sesión anterior, no registrado entonces): emulador arrancado headless con swiftshader, APK instalado, app arrancada y captura obtenida. Ese resultado NO se volcó a este archivo antes de la interrupción.
- DISCREPANCIA detectada al reanudar (sesión 4): `adb devices` vacío y ningún emulador en marcha; `emulator -list-avds` sí muestra `mp_test`. Es decir, el AVD persiste pero la ejecución anterior se perdió y su resultado no está verificado en este archivo.
- Reanudación en curso: relanzar `mp_test`, reinstalar el APK de `claude/fase-3-mvp`, arrancar la app, revisar logcat (crashes/excepciones) y capturar pantalla, registrando el resultado real aquí.

### 18.2. Ejecución real verificada (sesión 4, 2026-09-22)
- Emulador `mp_test` arrancado headless (PID Windows 38272, `-memory 1536`, swiftshader): `boot_completed=1`, `emulator-5554`, Android 15 (API 35). RAM libre de partida 1,48 GB de 15,16 GB (Code 2,5 GB + claude 1,6 GB + brave 1,5 GB).
- APK verificado al día (20,37 MB, 02:30:10 > último fuente `MiPatrimonioApp.kt` 02:30:04), es decir el de commit b579b33. `adb uninstall` + `adb install -r` → Success.
- Arranque REAL: `am start com.mipatrimonio.app/.MainActivity` → PID 3296, `topResumedActivity=MainActivity`. **logcat sin FATAL/ANR/excepciones**. Solo avisos benignos: `base.dm` ausente (normal sin baseline profile en debug), `PackageConfigPersister`, `InputManager-JNI` del splash.
- UI confirmada por captura: tema oscuro por defecto, textos en español, estado vacío con CTA, barra inferior con las 5 secciones.
- Flujo funcional verificado: Inicio → Crear cuenta → Cuentas → Nueva cuenta → diálogo (Nombre, Tipo, Divisa, Saldo inicial con sufijo EUR y teclado numérico) → Guardar → la cuenta «Banco Principal · Cuenta corriente · EUR · 1.500,00 €» aparece en «Cuentas activas». Persistencia Room y formato es-ES (miles con punto, decimales con coma) correctos.
- DEFECTO D-UI-001 detectado: en la barra de navegación inferior la etiqueta «Presupuestos» se parte en dos líneas («Presupuesto» / «s»). Pendiente de corregir.
- Nota de método: `adb exec-out screencap -p > fichero` desde PowerShell CORROMPE el PNG (BOM/CRLF). Usar `adb shell screencap -p /sdcard/shot.png` + `adb pull`.
- Dashboard con datos REALES verificado: Patrimonio neto 1.500,00 € con aviso «Patrimonio parcial: no incluye deudas», Dinero disponible 1.500,00 €, Inversiones 0,00 €, bloque «Este mes», «Sin presupuestos mensuales», selector de periodo (Mes/3/6/Año/Todo) y gráfico «Evolución del patrimonio» con punto real (22 sept, 1.500,00 €).
- Movimientos: buscador, chips de filtro (Todos/Ingresos/Gastos/Transferencias), estado vacío y FAB. El menú del FAB deshabilita «Nueva transferencia» explicando «Necesitas al menos dos cuentas activas» (buen estado deshabilitado).
- Formulario de movimiento verificado: selector Ingreso/Gasto, importe con sufijo EUR y teclado numérico, cuenta preseleccionada, categorías por defecto sembradas y FILTRADAS por tipo (Alimentación, Vivienda, Transporte, Ocio, Salud, Suscripciones…), fecha, descripción, comercio, notas.
- **REGLA FINANCIERA VERIFICADA EN APP REAL**: gasto de 250,50 € (categoría Alimentación) → lista muestra «−250,50 €» en rojo; dashboard pasa a Patrimonio neto 1.249,50 €, Dinero disponible 1.249,50 €, Gastos del mes 250,50 €, Balance −250,50 €, y el gráfico refleja la caída. Precisión decimal exacta (céntimos Long), sin errores de coma flotante.
- **PERSISTENCIA VERIFICADA**: `am force-stop` + reinicio (PID nuevo 3974) → los datos siguen intactos. Cumple el criterio «conservar los datos después de cerrar la aplicación».
- DEFECTO D-UI-002 (menor, ABIERTO): inconsistencia tipográfica del signo negativo — la lista de movimientos usa «−» (U+2212) y el balance del dashboard usa «-» (guion). Baja prioridad.

### 18.3. Presupuestos, inversiones y patrimonio verificados en app real (sesión 4)
- **Presupuestos**: creado presupuesto global mensual de 300,00 €. La tarjeta muestra «Mensual · 1 sept 2026 – 30 sept 2026», barra ámbar, «⚠ Cerca del límite · 84 % consumido», «Gastado 250,50 € de 300,00 €», «Disponible: 49,50 €». Cálculo y umbral de aviso correctos (250,50/300 = 83,5 % → 84 %). El dashboard pasó a mostrar «Presupuesto restante del mes: 49,50 €».
- **Inversiones**: cartera «Cartera Principal» + activo «Vanguard All-World · VWCE · EUR» (el formulario avisa «El ticker no identifica de forma única un activo») + compra de 10 a 100,00 € con 5,00 € de comisiones.
  - Coste 1.005,00 € y precio medio 100,50 € → comisión capitalizada en el coste medio ponderado, **correcto**.
  - Antes de fijar precio: «Sin cotización», Valor y Plusvalía = «No disponible», aviso ámbar «Activos sin cotización: Vanguard All-World». **No inventa precios**, tal y como exige CLAUDE.md §15.
  - Tras precio manual 110,00 € (el diálogo advierte «Este precio se guardará manualmente, sin proveedor externo»): Valor 1.100,00 €, Plusvalía +95,00 € (+9,45 %), «Precio actual: 110,00 € · Actualizado hoy». Métrica etiquetada como «Rentabilidad simple no realizada (no ponderada por tiempo)».
- **Patrimonio**: 2.349,50 € = 1.249,50 € efectivo + 1.100,00 € inversiones. Desglose con «Pasivos: No registrados» y explicación del carácter parcial. Donut por tipo de activo (Cuenta corriente 53 % / Inversiones 47 %), por divisa, por cuenta y por cartera. El gráfico histórico avisa «Las inversiones se valoran a coste en el histórico» y el punto de sept 26 (2.254,50 € = 1.249,50 + 1.005,00 de coste) es coherente con esa política.
- **D-UI-001 CORREGIDO y verificado**: `MiPatrimonioApp.kt` — la etiqueta del `NavigationBarItem` pasa a `maxLines = 1` con `MaterialTheme.typography.labelSmall`. Verificado tras reinstalar: «Presupuestos» cabe en una línea, sin truncar, y las cinco etiquetas encajan. `assembleDebug` + `testDebugUnitTest` → BUILD SUCCESSFUL (41 s).
- Criterios del MVP (CLAUDE.md §30) comprobados en dispositivo: instala, abre sin errores, crea cuentas, registra gastos, crea categorías (sembradas), configura presupuestos, muestra saldos correctos, registra inversiones manualmente, valora la cartera con precios manuales, calcula el patrimonio, muestra dashboard con datos reales y conserva los datos al cerrar. PENDIENTES de probar en app real: transferencia entre cuentas (requiere 2 cuentas), ingreso, edición/eliminación de movimientos, tema claro y ajustes.
- Nota: la app no declara permiso INTERNET, por lo que el funcionamiento offline está garantizado por construcción.
- Próxima acción exacta: crear una segunda cuenta y probar una transferencia (debe conservar el patrimonio total y no contabilizarse como gasto), y registrar un ingreso.
