# Arquitectura de Mi Patrimonio

## Tecnología
Kotlin + Jetpack Compose (Material 3), MVVM + capas, Room, DataStore, Coroutines/Flow. Elegida frente a Flutter/React Native por el acceso nativo a `NotificationListenerService`, Keystore y WorkManager, y por el funcionamiento offline-first sin capa puente.

Versiones verificadas: Gradle 9.7.1, AGP 9.4.1 (Kotlin integrado; no se aplica `kotlin-android`), Kotlin 2.4.20, KSP 2.3.12, Compose BOM 2026.09.00, Room 2.8.5. `compileSdk` 37, `targetSdk` 36, `minSdk` 26.

## Capas (`app/src/main/java/com/mipatrimonio/app`)
```
domain/   modelo y reglas financieras puras (sin Android): model/, calc/, usecase/
data/     Room (db/) y repositorios (repository/), DataStore de ajustes
ui/       Compose: common/ (componentes, etiquetas, gráficos), theme/, navigation/, <feature>/
```
Las pantallas dependen de ViewModels y estos de repositorios/dominio; ninguna pantalla llama a servicios externos. La inyección es manual (`AppContainer`), suficiente por ahora.

## Reglas financieras (fuente de verdad: `domain/`)
- **Dinero**: `Long` en unidades menores de la divisa (decimales ISO 4217; BTC/ETH 8). Nunca coma flotante. Cálculos intermedios y cantidades/precios de activos con `BigDecimal` (`DECIMAL128`). Redondeo global **HALF_EVEN** al pasar a unidades menores.
- **Saldos derivados**: saldo = inicial + ingresos − gastos + transferencias recibidas − enviadas. No se almacena.
- **Transferencias**: entidad propia con importe de origen y de destino (el tipo de cambio es implícito). No son ingreso ni gasto.
- **Presupuestos**: gasto del periodo (mes/año natural) de la categoría y sus subcategorías; aviso ≥ 80 %, superado > límite. Solo cuenta la divisa del presupuesto; el resto se excluye y se informa.
- **Posiciones**: precio medio ponderado; comisiones de compra capitalizadas en el coste; venta retira coste proporcional y realiza plusvalía; dividendos netos (bruto − retención); ventas en descubierto rechazadas.
- **Rentabilidad**: *simple no realizada* = plusvalía latente / coste. No es TWR ni MWR y así se etiqueta.
- **Multidivisa (MVP)**: sin tipos de cambio reales. Los importes en divisa distinta de la principal se excluyen de totales y se avisa; nunca se inventa un tipo. `MoneyMath.convert` ya existe para cuando haya tipos de cambio identificables y fechados.
- **Patrimonio** = activos − pasivos. Efectivo (incluido el de cuentas de inversión) una sola vez; posiciones a valor de mercado aparte. Siempre «parcial» mientras no se registren pasivos.
- **Histórico de patrimonio**: efectivo por fecha + inversiones a coste (no hay histórico de cotizaciones).

## Base de datos (Room v1, `data/db`)
Tablas: `account`, `category`, `txn`, `transfer`, `budget`, `portfolio`, `asset`, `investment_operation`, `asset_price`. Claves foráneas `RESTRICT` (cuentas/categorías se archivan, no se borran; `asset_price` en cascada). Índices en claves foráneas y fecha. Enums como texto, `BigDecimal` como texto, fechas como `epochDay`. Esquema exportado en `app/schemas/`.

**Migraciones**: cada cambio de esquema sube `version`, añade un `Migration` explícito en `AppDatabase.create` y se prueba con `room-testing`. Nunca `fallbackToDestructiveMigration`. **Restauración**: aún no hay copias de seguridad (Fase 8); se documentará aquí.

## Pruebas
- Dominio y agregaciones: JUnit puro.
- Repositorios: Robolectric + Room en memoria (`@Config(sdk=[34])`).
- Lógica de UI (filtros, ordenaciones, agregadores): funciones puras con tests JVM.
No hay pruebas instrumentadas ni emulador todavía.

## Extensibilidad prevista
Proveedores de cotizaciones y tipos de cambio tras interfaces; motor de notificaciones bancarias basado en reglas/adaptadores por banco; sincronización con Supabase con ids estables (UUID) y RLS. Ver [ROADMAP.md](ROADMAP.md).
