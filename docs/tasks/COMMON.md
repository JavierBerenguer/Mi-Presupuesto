# Contrato común de las tareas de UI (léelo entero antes de empezar)

## Entorno y verificación
- Windows. Comandos: `.\gradlew.bat testDebugUnitTest` y `.\gradlew.bat assembleDebug` desde la raíz de tu worktree.
- `JAVA_HOME` (JDK 17) y `ANDROID_HOME` ya están definidos en el entorno. No instales nada ni cambies archivos Gradle.
- Si un comando no puede ejecutarse (sandbox, red), dilo tal cual en el informe con el error; no afirmes que compila.
- No hay emulador: la verificación es compilación + tests unitarios JVM.

## Qué ya existe (léelo, no lo modifiques)
- `domain/model/Money.kt` (`MoneyMath.parse/toMinor/format/formatQuantity/toDecimal`, `Currencies`) y `domain/model/Models.kt` (todas las entidades de dominio).
- `domain/calc/*` (saldos, estadísticas, presupuestos, posiciones, patrimonio) y `domain/usecase/*` (`SnapshotBuilder`, `FinanceSnapshot`, `Period`, `HistoryCalculator`).
- `data/repository/LedgerRepository`, `InvestmentRepository`, `SettingsRepository` (Flows + funciones `suspend` de escritura que validan y lanzan `IllegalArgumentException` con mensaje en español).
- `AppContainer` (`container.ledger`, `container.investments`, `container.settings`).
- `ui/common/`: `appViewModel { c -> MiViewModel(c.ledger, ...) }`, `Components.kt` (`EmptyState`, `LoadingBox`, `SectionCard`, `MoneyText`, `ConfirmDialog`, `DropdownField`, `DateField`, `AmountField`, `MoneyColors`, `formatDate`), `Labels.kt` (`.label()` de enums), `strings_common.xml`.
- Tema Material 3 oscuro/claro en `ui/theme`.

## Reglas de UI
1. **No añadas `Scaffold`, `TopAppBar` ni barra inferior**: la app ya los provee. Tu pantalla es solo contenido. Si necesita un botón flotante, colócalo en un `Box` con `Modifier.align(Alignment.BottomEnd).padding(16.dp)`. Deja 88.dp de `contentPadding` inferior en listas para no tapar el último elemento.
2. Firma de entrada: los composables públicos indicados en tu ficha, con el ViewModel como último parámetro con valor por defecto: `viewModel: XViewModel = appViewModel { c -> XViewModel(c.ledger, c.settings) }`.
3. ViewModel: `androidx.lifecycle.ViewModel`; estado como `StateFlow<UiState>` con `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), <estado inicial>)`; en la UI, `collectAsStateWithLifecycle()`. Mientras no llegue el primer dato muestra `LoadingBox`.
4. Errores: los repositorios lanzan `IllegalArgumentException` con texto en español; captúralos en el ViewModel y muéstralos como texto de error dentro del formulario/diálogo. Nunca dejes que una excepción llegue a cerrar la app.
5. Operaciones destructivas (eliminar, archivar) piden confirmación con `ConfirmDialog`.
6. Estados vacíos con `EmptyState` (mensaje claro + acción cuando proceda).
7. **Dinero**: jamás `Float`/`Double`. Entrada con `MoneyMath.parse` → `BigDecimal` → `MoneyMath.toMinor(valor, divisa)`. Salida con `MoneyMath.format(minor, divisa)`. Divisa base: `settings.first().baseCurrency` / flujo `SettingsRepository.settings`.
8. Ids nuevos: `UUID.randomUUID().toString()`. Marcas de tiempo: `System.currentTimeMillis()`.
9. Textos: TODO texto visible (incluidos `contentDescription`) en tu propio archivo `app/src/main/res/values/strings_<feature>.xml`, en español, con el prefijo de recurso indicado en tu ficha. Sin cadenas literales en Compose. Reutiliza `common_*` para lo genérico (Cancelar, Guardar…) sin duplicarlo.
10. Accesibilidad: `contentDescription` en iconos accionables, objetivos táctiles ≥ 48 dp, no transmitas información solo con color (acompaña con texto/icono).
11. La lógica de filtrado, orden, agrupación o cálculo va en **funciones puras** (sin Android ni Compose) en un archivo aparte de tu carpeta, con tests JUnit en `app/src/test/java/com/mipatrimonio/app/ui/<feature>/`.
12. Código idiomático, funciones pequeñas, sin comentarios que repitan el código, sin `!!` innecesarios, sin datos ficticios en el flujo normal.

## Prohibido tocar (salvo que tu ficha lo diga expresamente)
`domain/`, `data/`, `AppContainer.kt`, `MainActivity.kt`, `ui/navigation/`, `ui/theme/`, `ui/common/`, `res/values/strings.xml`, `res/values/strings_common.xml`, archivos `*.gradle.kts`, `libs.versions.toml`, `AndroidManifest.xml`, y las carpetas de otras funcionalidades. Si necesitas algo de ahí, NO lo hagas: descríbelo en el informe.

## Informe final (obligatorio)
Resumen; lista de archivos creados/modificados; comandos ejecutados con su resultado real (nº de tests, BUILD SUCCESSFUL/FAILED); decisiones que hayas tomado; dudas o desvíos. No maquilles fallos.

## Reglas de tests de ViewModel (obligatorias, lección de T-025 y T-027)
- Los ViewModels usan `stateIn(WhileSubscribed)`. En los tests: `@RunWith(RobolectricTestRunner::class)`, `Dispatchers.setMain(UnconfinedTestDispatcher())` en `@Before` y `resetMain()` en `@After`, `runTest { … }` en cada test (NUNCA `runBlocking` en el cuerpo de un test: con Robolectric bloquea el hilo principal y el test se cuelga).
- Leer el estado con `viewModel.uiState.first { condición }` (nunca `.value` sin recolector) y esperar al estado final antes de comprobar la base de datos.
- Antes de entregar, revisa a mano cada llamada a Compose: `Modifier.padding` acepta `(start, top, end, bottom)` o `(horizontal, vertical)` pero no mezclados; las referencias a propiedades (`Account::name`) no sirven donde se espera una lambda `(T) -> String` composable: usa `{ it.name }`. No puedes compilar: relee el código en busca de errores de sintaxis y de tipos.

## Pantallas de primer nivel con cabecera propia (lección de T-028)
Si una pantalla dibuja su propio título (Fraunces 28 sp) debe: (a) añadirse a `hasOwnTopBar` en `ui/navigation/MiPatrimonioApp.kt` (si no, aparece un segundo título en la barra superior) y (b) aplicar `Modifier.statusBarsPadding()` en su contenedor raíz (si no, el título queda bajo la barra de estado).
