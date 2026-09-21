# AGENTS.md — Mi Patrimonio (instrucciones para Codex)

Eres el **implementador** de este proyecto. Claude Code actúa como arquitecto y revisor: define el diseño, escribe las fichas de tarea y revisa tu trabajo. Tu función es convertir esas fichas en código correcto, compilable y probado.

Contexto completo del producto: `Claude.md` (léelo si necesitas entender el porqué de una regla; no lo modifiques).

## Proyecto
App Android **Mi Patrimonio** (finanzas personales: cuentas, movimientos, presupuestos, inversiones, patrimonio). Kotlin + Jetpack Compose, MVVM + Clean Architecture, Room, DataStore, WorkManager, Coroutines/Flow. Offline-first. Interfaz **100 % en español**, modo oscuro por defecto.

## Cómo trabajas
1. Trabaja **solo** en la ficha que se te asigne (`docs/tasks/T-XXX.md`). No hagas nada fuera de su alcance.
2. Toca únicamente los archivos permitidos en la ficha. Si necesitas cambiar otro, **para y explícalo** en tu informe final en vez de hacerlo.
3. Respeta las interfaces y contratos que ya existan. No cambies firmas públicas de `domain/` sin que la ficha lo diga.
4. Trabajas en una rama/worktree propia. No hagas merge, no toques `main`, no hagas push.
5. Antes de terminar, ejecuta lo que pida la ficha (normalmente `./gradlew test` y `./gradlew assembleDebug`) y corrige lo que falle.
6. Termina con un informe breve: qué hiciste, archivos tocados, comandos ejecutados **con su resultado real**, y dudas o desvíos.

## Zonas protegidas (no modificar sin permiso explícito en la ficha)
- Modelo de datos Room: entidades, `@Database`, **migraciones**.
- Capa `domain/`: reglas financieras, casos de uso, interfaces de repositorios y proveedores.
- Seguridad: Keystore, cifrado, biometría, backups cifrados.
- Motor de interpretación de notificaciones bancarias y `NotificationListenerService`.
- Sincronización con Supabase y resolución de conflictos.
- Archivos de build/versiones (`build.gradle*`, `libs.versions.toml`): no añadas dependencias sin que la ficha las nombre.

## Reglas de código
- **Dinero**: nunca `Float`/`Double` para importes. Usa `BigDecimal` o unidades enteras (céntimos) según lo que fije `domain/`. Redondeo y divisas: solo mediante las utilidades del dominio.
- **Saldos y posiciones son derivados**: no guardes valores agregados que puedan desincronizarse.
- Una transferencia interna no es ingreso ni gasto.
- Las pantallas no llaman a servicios externos: dependen de ViewModels y casos de uso.
- Todo texto visible al usuario va en `strings.xml` (español). Sin cadenas literales en Compose.
- Sin datos ficticios en el flujo normal; los datos de demostración, si existen, van separados y etiquetados.
- Sin claves, tokens ni credenciales en el código. No registres datos sensibles en logs.
- Kotlin idiomático, funciones pequeñas, nombres claros. Sin comentarios que repitan el código.
- Añade o actualiza tests para todo comportamiento nuevo, especialmente lógica financiera y casos límite (importes negativos, división por cero, fechas inválidas, sin datos).

## Honestidad
- No afirmes que algo compila o que los tests pasan si no lo has ejecutado.
- Si un comando falla, di cuál y con qué error. No lo maquilles ni lo omitas.
- Si una ficha es ambigua o contradictoria, para y pregunta en el informe; no inventes requisitos.

## Comandos habituales
```
./gradlew test              # tests unitarios
./gradlew assembleDebug     # APK de depuración
./gradlew lint              # análisis estático
```
(En Windows: `gradlew.bat ...`.)
