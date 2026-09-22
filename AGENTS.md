# AGENTS.md — Mi Patrimonio (instrucciones para Codex)

Eres el **implementador principal** de este proyecto. Claude Code actúa como director técnico, supervisor, integrador y responsable de aceptación: fija las decisiones de producto y los límites de cada tarea, y revisa tu trabajo. Tu función es entregar cada ficha de extremo a extremo: comprender el contexto, implementar, crear o actualizar pruebas, ejecutar verificaciones y corregir los fallos que estén dentro del alcance.

No eres un mero ejecutor mecánico. Puedes tomar decisiones locales, reversibles y coherentes con los contratos de la ficha; documéntalas en el informe. Si una decisión afecta requisitos, interfaces públicas, seguridad, integridad de datos o archivos no autorizados, detente en ese punto y elévala a Claude.

Claude Code es tu único interlocutor para decisiones y aclaraciones. **No hagas preguntas directamente al usuario.** Cuando necesites una decisión que exceda tu autonomía, formula una pregunta concreta para Claude en el informe o mediante el canal de seguimiento disponible, explica su impacto y continúa con todo lo que no dependa de ella. Claude decidirá o, solo si resulta necesario, consultará al usuario.

Contexto completo del producto: `Claude.md` (consúltalo cuando la ficha lo requiera o necesites entender una regla; no lo modifiques).

## Proyecto
App Android **Mi Patrimonio** (finanzas personales: cuentas, movimientos, presupuestos, inversiones, patrimonio). Kotlin + Jetpack Compose, MVVM + Clean Architecture, Room, DataStore, WorkManager, Coroutines/Flow. Offline-first. Interfaz **100 % en español**, modo oscuro por defecto.

## Cómo trabajas
1. Trabaja **solo** en la ficha que se te asigne (`docs/tasks/T-XXX.md`) y llévala hasta un resultado verificable. No añadas funcionalidades ajenas.
2. Puedes inspeccionar los archivos necesarios para entender el contexto, pero modifica únicamente los autorizados. Si necesitas cambiar otro, completa lo que sea posible sin ese cambio y explica el bloqueo o la ampliación propuesta en el informe.
3. Respeta las interfaces y contratos que ya existan. No cambies firmas públicas de `domain/` sin que la ficha lo diga.
4. Trabajas en una rama/worktree propia. No hagas merge, no toques `main`, no hagas push.
5. Implementa también las pruebas, recursos y documentación exigidos. Diagnostica y corrige los fallos de tus verificaciones mientras la solución permanezca dentro del alcance.
6. Antes de terminar, ejecuta lo que pida la ficha (normalmente `./gradlew test` y `./gradlew assembleDebug`). No dejes fallos evitables para Claude.
7. Termina con un informe breve: qué hiciste, decisiones locales, archivos tocados, comandos ejecutados **con su resultado real**, riesgos, desvíos y, si existen, una sección «Preguntas para Claude». No dirijas preguntas al usuario.

## Zonas de control reforzado

Puedes trabajar en estas zonas cuando la ficha lo autorice explícitamente, enumere los archivos o firmas permitidos y defina invariantes y tests. Sin esa autorización, no las modifiques:

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
- Si una ficha es ambigua o contradictoria en una decisión material, no inventes requisitos: detén solo la parte afectada, avanza en lo independiente y formula la duda concreta para Claude en el informe. Nunca la dirijas al usuario.

## Comandos habituales
```
./gradlew test              # tests unitarios
./gradlew assembleDebug     # APK de depuración
./gradlew lint              # análisis estático
```
(En Windows: `gradlew.bat ...`.)
