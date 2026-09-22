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

## Importación de Trade Republic (hallazgos del CSV de ejemplo, Fase 8)
Fichero de ejemplo local (`datos-privados/`, ignorado por git; contiene datos reales). Estructura, sin datos personales:
- Cabecera: `datetime,date,account_type,category,type,asset_class,name,symbol,shares,price,amount,fee,tax,currency,original_amount,original_currency,fx_rate,description,transaction_id,counterparty_name,counterparty_iban,payment_reference,mcc_code`. UTF-8, todo entre comillas, punto decimal, fechas ISO (`date` y `datetime` UTC), 465 filas de 2026-01-01 a 2026-08-31, una sola `account_type` (`DEFAULT`).
- **`transaction_id` es único** → clave natural de deduplicación: reimportar el mismo fichero no debe crear movimientos nuevos.
- Mezcla efectivo (`category=CASH`), operaciones (`TRADING`), eventos corporativos (`CORPORATE_ACTION`) y entregas (`DELIVERY`). Tipos vistos: `CARD_TRANSACTION`(+`_INTERNATIONAL`), `TRANSFER_INSTANT_INBOUND/OUTBOUND`, `TRANSFER_DIRECT_DEBIT_INBOUND`, `TRANSFER_INBOUND`, `INTEREST_PAYMENT`, `DIVIDEND`, `BUY`, `SELL`, `PRIVATE_MARKET_BUY`, `BENEFITS_SAVEBACK`, `LIQUIDATION_PROCEEDS`, `IPO_SUBSCRIPTION`, `INTERMEDIATE_SECURITIES_DISTRIBUTION`, `LIQUIDATION_DIVIDEND` (y sus `_CANCELLED`), `FREE_DELIVERY`, `BONUS`, `FEE`.
- Importes con signo (`amount` negativo = salida); `fee` y `tax` en columnas aparte (3 y 19 filas); 5 filas con `original_currency` USD y `fx_rate`; 9 sin divisa.
- Activos identificados por ISIN en `symbol` (no por ticker), con `shares` y `price` decimales largos → `BigDecimal`.
- **Trampa de doble contabilización**: los `BUY` de `PRIVATE_FUND` (9) vienen con `amount` vacío; su salida de caja está en la fila `PRIVATE_MARKET_BUY` (9, negativa). Hay que emparejar y no contar ambas como gasto.
- `CARD_TRANSACTION` trae `mcc_code` en 209 de 212 filas y no trae `counterparty_name` (el comercio hay que sacarlo de `description`): el MCC permite proponer categoría automáticamente.
- Las transferencias entrantes/salientes pueden ser entre cuentas propias o con terceros: no se pueden clasificar sin confirmación del usuario → se importan como propuestas para revisar.
- El adaptador debe ser configurable (vista previa, mapeo de columnas, detección de duplicados, informe de errores) sin asumir que otros bancos comparten formato.

## Carencia de diseño detectada (a resolver antes de la importación)
Las operaciones de inversión **no están vinculadas a una cuenta de efectivo**: comprar un ETF no reduce el saldo de ninguna cuenta. Con el modelo actual, quien registre a la vez el efectivo del bróker y sus compras vería un patrimonio inflado o tendría que compensarlo a mano. Propuesta (migración Room v1→v2): columna opcional `fundingAccountId` en `investment_operation`; el saldo de esa cuenta se ajusta por derivación (compra −(importe+comisión), venta +(importe−comisión), dividendo +neto) en `BalanceCalculator`, con tests, manteniendo que los saldos siguen siendo derivados y que el efectivo de cuentas de inversión no se cuenta dos veces.
