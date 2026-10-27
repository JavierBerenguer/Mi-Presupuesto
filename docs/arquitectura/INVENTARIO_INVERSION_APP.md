# Inventario de inversión en Mi Patrimonio

> Alcance: código que depende del modelo de inversión actual y debe eliminarse o desacoplarse antes de integrar Rumbo/Supabase. Las referencias son relativas a este repositorio.

## 1. Persistencia Room

### Entidades y tablas

| Elemento | Qué es / dependencias |
|---|---|
| `portfolio` / `PortfolioEntity` | Cartera con `id`, nombre, creación, cuenta predeterminada y archivado; `defaultAccountId` se usa como vínculo lógico con cuenta (`app/src/main/java/com/mipatrimonio/app/data/db/Entities.kt:120-127`). |
| `asset` / `AssetEntity` | Activo con ticker/ISIN/tipo/mercado/divisa, archivado y configuración de proveedor (`quoteProvider`, `quoteSymbol`, `quoteMic`) (`app/src/main/java/com/mipatrimonio/app/data/db/Entities.kt:129-143`). |
| `investment_operation` / `InvestmentOperationEntity` | FK restrictiva a cartera/activo; compra, venta, dividendo, comisión y patas de traspaso; cantidad/precio como texto decimal, comisión en minor units, cuenta opcional, hora y grupo de traspaso (`app/src/main/java/com/mipatrimonio/app/data/db/Entities.kt:145-170`). |
| `asset_price` / `AssetPriceEntity` | FK con cascade a activo; precio decimal, divisa, instante, fuente y calidad (`app/src/main/java/com/mipatrimonio/app/data/db/Entities.kt:172-185`). |
| Cuenta `INVERSION`/`CRIPTO` | No es tabla exclusiva, pero clasifica cuentas y permite vincular operaciones/carteras (`app/src/main/java/com/mipatrimonio/app/domain/model/Models.kt:7-16`, `app/src/main/java/com/mipatrimonio/app/ui/accounts/AccountsViewModel.kt:128-158`). Debe decidirse aparte si los tipos se conservan para efectivo broker; no eliminarlos mecánicamente. |

`AppDatabase` incluye las cuatro entidades y expone un único `InvestmentDao` (`app/src/main/java/com/mipatrimonio/app/data/db/AppDatabase.kt:24-50`). El DAO contiene backup/restore, flujos, CRUD, comprobación de dependencias, búsquedas por ISIN, operaciones ordenadas/traspasos y candidatos/precios de cotización (`app/src/main/java/com/mipatrimonio/app/data/db/Daos.kt:222-347`).

### Migraciones y esquemas

- Las tablas ya existen en esquema v1; migración 2→3 añade `portfolio.defaultAccountId`, `investment_operation.accountId` e índice (`app/src/main/java/com/mipatrimonio/app/data/db/migrations/Migration2to3.kt:6-16`).
- 4→5 añade `asset.archived` (`app/src/main/java/com/mipatrimonio/app/data/db/migrations/Migration4to5.kt:6-15`); 5→6 añade `secondOfDay` (`app/src/main/java/com/mipatrimonio/app/data/db/migrations/Migration5to6.kt:6-13`).
- 8→9 añade los tres campos de proveedor a activo y `quality` a precio (`app/src/main/java/com/mipatrimonio/app/data/db/migrations/Migration8to9.kt:6-12`); 9→10 añade `portfolio.archived` (`app/src/main/java/com/mipatrimonio/app/data/db/migrations/Migration9to10.kt:6-9`).
- 10→11 añade `transferGroupId` e índice (`app/src/main/java/com/mipatrimonio/app/data/db/migrations/Migration10to11.kt:6-13`).
- Los snapshots `app/schemas/.../1.json` a `14.json` conservan todas las versiones; `AppDatabase` registra explícitamente 1→14 sin fallback destructivo (`app/src/main/java/com/mipatrimonio/app/data/db/AppDatabase.kt:41-73`). Eliminación segura exige una nueva migración que quite tablas/índices o las deje transitoriamente; no borrar archivos históricos de esquema necesarios para validar migraciones publicadas.

### Copias y CSV

Backup incluye nombres de tabla y listas completas de portfolios/assets/operations/prices (`app/src/main/java/com/mipatrimonio/app/data/backup/BackupModels.kt:24-49`). Export y restore leen, borran e insertan esas tablas en orden de FK (`app/src/main/java/com/mipatrimonio/app/data/backup/BackupRepository.kt:14-36`, `app/src/main/java/com/mipatrimonio/app/data/backup/BackupRepository.kt:41-83`); el validador comprueba unicidad y referencias a cartera/activo/cuenta (`app/src/main/java/com/mipatrimonio/app/data/backup/BackupValidator.kt:4-27`, `app/src/main/java/com/mipatrimonio/app/data/backup/BackupValidator.kt:57-63`).

CSV carga esas cuatro colecciones (`app/src/main/java/com/mipatrimonio/app/data/export/CsvExportModels.kt:13-22`, `app/src/main/java/com/mipatrimonio/app/data/export/CsvExportRepository.kt:7-21`) y genera `carteras.csv`, `activos.csv`, `operaciones.csv`, `dividendos.csv`, `precios.csv`; además `cuentas.csv` calcula saldo con operaciones (`app/src/main/java/com/mipatrimonio/app/data/export/CsvExportService.kt:36-51`, `app/src/main/java/com/mipatrimonio/app/data/export/CsvExportService.kt:67-73`). Hay que retirar esas hojas y el efecto de operaciones en cuentas, manteniendo el resto del ZIP.

## 2. Dominio y cálculo

| Elemento | Acoplamiento actual / destino |
|---|---|
| Modelos `Portfolio`, `Asset`, `InvestmentOperation`, `AssetPrice` y enums | Contrato completo actual, incluidos proveedores, tipos, patas de traspaso y calidad (`app/src/main/java/com/mipatrimonio/app/domain/model/Models.kt:139-198`). Se elimina junto al motor actual; cualquier DTO nuevo de Rumbo debe ser otro contrato. |
| `PositionCalculator` | Valida y ordena por fecha/hora/creación; calcula cantidad, **coste medio proporcional** en ventas/traspasos, P&L, dividendos y comisiones; valora a último precio (`app/src/main/java/com/mipatrimonio/app/domain/calc/PositionCalculator.kt:55-124`). Se elimina: no es FIFO Rumbo. |
| `BalanceCalculator.investmentEffectMinor` | Convierte bruto `cantidad×precio` a minor units: compra/comisión restan, venta/dividendo suman, traspasos no afectan; exige cuenta/divisa compatibles (`app/src/main/java/com/mipatrimonio/app/domain/calc/BalanceCalculator.kt:19-64`). Su retirada cambia saldos de cuentas. |
| `HistoryCalculator` | Histórico de patrimonio valora inversiones **a coste**, no con histórico de precios; agrupa por cartera/activo y llama a `PositionCalculator` (`app/src/main/java/com/mipatrimonio/app/domain/usecase/HistoryCalculator.kt:28-87`). Debe quedar solo la serie de cuentas o recibir serie remota nueva. |
| `NetWorthCalculator` | Suma `InvestmentValue` con precio disponible, marca divisas/activos excluidos y resta liabilities (actualmente siempre 0) (`app/src/main/java/com/mipatrimonio/app/domain/calc/NetWorthCalculator.kt:6-29`, `app/src/main/java/com/mipatrimonio/app/domain/calc/NetWorthCalculator.kt:38-69`). Debe sustituir la entrada por el patrimonio de inversión nuevo o eliminar esa porción. |
| Snapshot/distribución | `SnapshotBuilder`/`FinanceSnapshot`, `AssetDistribution` y `PortfolioValueSeries` construyen posiciones y valor de inversión para Inicio/patrimonio (`app/src/main/java/com/mipatrimonio/app/domain/usecase/FinanceSnapshot.kt:40-73`, `app/src/main/java/com/mipatrimonio/app/domain/usecase/AssetDistribution.kt:6-14`, `app/src/main/java/com/mipatrimonio/app/domain/usecase/PortfolioValueSeries.kt:16-48`). Deben desacoplarse antes de borrar modelos. |
| `MovementsBalanceCalculator` | Incluye operaciones con cuenta en saldo mensual mediante `investmentEffectMinor` (`app/src/main/java/com/mipatrimonio/app/domain/calc/MovementsBalanceCalculator.kt:20-45`, `app/src/main/java/com/mipatrimonio/app/domain/calc/MovementsBalanceCalculator.kt:127-129`). Conservar cálculo de transacciones/transferencias y retirar este sumando. |

## 3. Repositorios, cotizaciones y trabajo periódico

- `InvestmentRepository` es la fachada Room: expone flujos de carteras/activos/operaciones/precios, valida duplicados y relaciones, CRUD/archivado, traspasos cripto con coste transferido y precios manual/proveedor (`app/src/main/java/com/mipatrimonio/app/data/repository/InvestmentRepository.kt:38-53`, `app/src/main/java/com/mipatrimonio/app/data/repository/InvestmentRepository.kt:53-175`, `app/src/main/java/com/mipatrimonio/app/data/repository/InvestmentRepository.kt:175-291`). Se elimina completo.
- `QuoteRepository` toma activos abiertos, agrupa por proveedor, actualiza precios mediante `InvestmentRepository` y resume fallos (`app/src/main/java/com/mipatrimonio/app/data/quotes/QuoteRepository.kt:25-88`). Se elimina.
- Proveedores: Twelve Data hace `/quote` por activo y rate limit; CoinGecko agrupa `/simple/price`; EODHD agrupa hasta 15 símbolos en `/real-time` (`app/src/main/java/com/mipatrimonio/app/data/quotes/TwelveDataQuoteProvider.kt:10-66`, `app/src/main/java/com/mipatrimonio/app/data/quotes/CoinGeckoQuoteProvider.kt:10-65`, `app/src/main/java/com/mipatrimonio/app/data/quotes/EodhdQuoteProvider.kt:11-87`). Se eliminan junto con `QuoteModels`, `HttpClient`, `RateLimiter` si ningún consumidor nuevo los reutiliza.
- Descubrimiento: OpenFIGI mapea ISIN; EODHD busca instrumentos, CoinGecko busca cripto y Twelve Data descubre cotización (`app/src/main/java/com/mipatrimonio/app/data/quotes/AssetLookupServices.kt:148-183`, `app/src/main/java/com/mipatrimonio/app/data/quotes/AssetLookupServices.kt:186-294`). Se elimina.
- `KeystoreSecretStore` y ajustes guardan claves OpenFIGI/Twelve Data/CoinGecko/EODHD (`app/src/main/java/com/mipatrimonio/app/data/quotes/KeystoreSecretStore.kt:11-60`, `app/src/main/java/com/mipatrimonio/app/ui/settings/SettingsScreen.kt:108-149`). Retirar únicamente secretos/controles de cotización; no tocar seguridad de backups.
- `QuoteRefreshWorker` refresca diariamente como trabajo único; `AppContainer` instancia servicios y lo agenda al iniciar (`app/src/main/java/com/mipatrimonio/app/data/work/QuoteRefreshWorker.kt:15-35`, `app/src/main/java/com/mipatrimonio/app/AppContainer.kt:40-63`, `app/src/main/java/com/mipatrimonio/app/AppContainer.kt:94-104`). Eliminar worker, registro y cualquier manifest asociado.

## 4. UI y navegación

Directorio `ui/investments/`: pantalla Cartera, listado/formulario/detalle de activos, diálogos de operación/traspaso, desgloses/resúmenes, antigüedad de precio y sus ViewModels. Todos dependen de modelos/repositorio de inversión y, para formularios, de lookup/cotizaciones (`app/src/main/java/com/mipatrimonio/app/ui/investments/InvestmentsViewModel.kt:5-25`, `app/src/main/java/com/mipatrimonio/app/ui/investments/AssetFormViewModel.kt:5-22`, `app/src/main/java/com/mipatrimonio/app/ui/investments/AssetDetailViewModel.kt:5-21`). No hay paquete separado `ui/assets`: `AssetsScreen`/`AssetsViewModel` viven en `ui/investments` (`app/src/main/java/com/mipatrimonio/app/ui/investments/AssetsScreen.kt:1-47`).

`ui/portfolios/` administra carteras, cuenta predeterminada, posiciones y archivado mediante ambos repositorios (`app/src/main/java/com/mipatrimonio/app/ui/portfolios/PortfoliosViewModel.kt:5-21`, `app/src/main/java/com/mipatrimonio/app/ui/portfolios/PortfoliosScreen.kt:1-45`). Se elimina completo.

Navegación:

- `Destino.Cartera` es pestaña principal; rutas secundarias `CARTERAS`, `ACTIVOS`, `ACTIVO/{portfolioId}/{assetId}` y destinos de “Más” para Carteras/Activos (`app/src/main/java/com/mipatrimonio/app/ui/navigation/Destino.kt:23-27`, `app/src/main/java/com/mipatrimonio/app/ui/navigation/Destino.kt:43-63`, `app/src/main/java/com/mipatrimonio/app/ui/navigation/Destino.kt:77-82`).
- `MiPatrimonioApp` importa y registra las pantallas de inversión, abre detalle desde Movimientos/Cartera y muestra gestores en las rutas (`app/src/main/java/com/mipatrimonio/app/ui/navigation/MiPatrimonioApp.kt:31-50`, `app/src/main/java/com/mipatrimonio/app/ui/navigation/MiPatrimonioApp.kt:159-189`, `app/src/main/java/com/mipatrimonio/app/ui/navigation/MiPatrimonioApp.kt:243-253`).
- Ajustes contiene bloque de APIs de cotización; su ViewModel depende de `SecretStore` (`app/src/main/java/com/mipatrimonio/app/ui/settings/SettingsScreen.kt:46-46`, `app/src/main/java/com/mipatrimonio/app/ui/settings/SettingsScreen.kt:108-149`, `app/src/main/java/com/mipatrimonio/app/ui/settings/SettingsViewModel.kt:5-25`).
- Recursos visibles de inversión están principalmente en `strings_investments.xml`, `strings_portfolios.xml` y claves de navegación/ajustes; retirarlos solo después de eliminar referencias (`app/src/main/res/values/strings_investments.xml:1-206`, `app/src/main/res/values/strings_portfolios.xml:1-25`, `app/src/main/res/values/strings.xml:4-15`).

## 5. Importadores

### Trade Republic: frontera TRADING/CASH

El adaptador normaliza filas a un modelo común. Clasifica `BUY`/`PRIVATE_MARKET_BUY`/IPO como compra, `SELL` como venta, varios dividendos como dividendo, `INTEREST_PAYMENT` como interés, tarjeta como gasto y `TRANSFER_*` como transferencia (`app/src/main/java/com/mipatrimonio/app/data/importer/TradeRepublicCsvAdapter.kt:66-112`, `app/src/main/java/com/mipatrimonio/app/data/importer/TradeRepublicCsvAdapter.kt:125-136`).

| Parte | Hoy | Acción |
|---|---|---|
| **TRADING — eliminar** | Categoría `TRADING`: crea activos/carteras y `InvestmentOperation` para BUY/SELL; empareja compras de fondos privados y calcula efecto de caja (`app/src/main/java/com/mipatrimonio/app/data/importer/TradeRepublicImportPlanner.kt:241-368`, `app/src/main/java/com/mipatrimonio/app/data/importer/TradeRepublicImportPlanner.kt:371-398`). Dividendos CASH con ISIN crean además operación de dividendo y activo/cartera (`app/src/main/java/com/mipatrimonio/app/data/importer/TradeRepublicImportPlanner.kt:148-211`). | Quitar compras, ventas, activos, carteras y la operación adicional de dividendo. El CSV de trading quedará fuera de alcance o ignorado/revisado explícitamente. |
| **CASH — conservar** | Categoría `CASH`: crea `Transaction` para tarjeta, transferencias representadas como movimiento de cuenta, intereses y efectivo de dividendos; asigna categorías y mantiene desglose/efecto de caja (`app/src/main/java/com/mipatrimonio/app/data/importer/TradeRepublicImportPlanner.kt:141-147`, `app/src/main/java/com/mipatrimonio/app/data/importer/TradeRepublicImportPlanner.kt:214-238`). | Mantener tarjeta, transferencias y `INTEREST_PAYMENT`. Mantener el **movimiento de efectivo** del dividendo, pero sin `InvestmentOperation`/activo. |

La planificación/contexto y repositorio están mezclados: el plan lleva nuevos activos/carteras y registros `Movement`/`Operation`; la ejecución inserta ambos dentro de una transacción Room (`app/src/main/java/com/mipatrimonio/app/data/importer/TradeRepublicImportPlanner.kt:28-69`, `app/src/main/java/com/mipatrimonio/app/data/importer/TradeRepublicImportRepository.kt:63-111`). La extracción CASH requiere dividir tipos/plan/repositorio, no borrar todo el importador.

### Neverless — eliminar

Todo el flujo es de inversión cripto aunque cree algunos movimientos de caja. Crea cartera “Neverless - Cripto”, activos cripto, compras, dividendos sintéticos para intereses, transferencias entre carteras y depósitos/intereses EUR como transacciones (`app/src/main/java/com/mipatrimonio/app/data/importer/NeverlessImportPlanner.kt:78-127`, `app/src/main/java/com/mipatrimonio/app/data/importer/NeverlessImportPlanner.kt:130-215`). El repositorio depende de `InvestmentRepository` y DAO de inversión para planificar/ejecutar (`app/src/main/java/com/mipatrimonio/app/data/importer/NeverlessImportRepository.kt:9-43`, `app/src/main/java/com/mipatrimonio/app/data/importer/NeverlessImportRepository.kt:43-88`). Según la ficha se elimina adaptador, planificador, repositorio, pantalla/ViewModel, ruta y strings completos, aun cuando se pierdan sus filas CASH.

## 6. Contactos con el día a día

| Contacto | Dependencia / retirada segura |
|---|---|
| Movimientos | `MovementItem.Investment` representa una operación, calcula importe con `investmentEffectMinor`, añade filtro `INVERSIONES`, cuenta y texto; el ViewModel combina repositorios y no permite borrar la operación desde aquí (`app/src/main/java/com/mipatrimonio/app/ui/movements/MovementFilters.kt:18-76`, `app/src/main/java/com/mipatrimonio/app/ui/movements/MovementFilters.kt:95-193`, `app/src/main/java/com/mipatrimonio/app/ui/movements/MovementsViewModel.kt:83-129`, `app/src/main/java/com/mipatrimonio/app/ui/movements/MovementsViewModel.kt:303-310`). Eliminar variante, builder, filtro y ramas UI/búsqueda. |
| Saldos de cuentas | `BalanceCalculator.balance`, `AccountsViewModel`, `MovementsBalanceCalculator` y export CSV incluyen efecto de operaciones vinculadas (`app/src/main/java/com/mipatrimonio/app/domain/calc/BalanceCalculator.kt:19-47`, `app/src/main/java/com/mipatrimonio/app/ui/accounts/AccountsViewModel.kt:60-89`, `app/src/main/java/com/mipatrimonio/app/data/export/CsvExportService.kt:67-73`). Retirar operaciones sin alterar transacciones/transferencias. Históricamente esto cambiará el saldo; requiere decisión/migración de efectivo antes de eliminar datos. |
| Inicio | `HomeViewModel` combina cuatro flujos de inversión; `HomeAggregator` crea snapshot, patrimonio, histórico y distribución, con opción `includeInvestments` (`app/src/main/java/com/mipatrimonio/app/ui/home/HomeViewModel.kt:28-74`, `app/src/main/java/com/mipatrimonio/app/ui/home/HomeAggregator.kt:82-112`, `app/src/main/java/com/mipatrimonio/app/ui/home/HomeAggregator.kt:140-142`). Desacoplar y decidir si el nuevo total remoto entra aquí. |
| Patrimonio | Pantalla/ViewModel y `NetWorthBreakdown` muestran cartera/activos/posiciones y no cotizados (`app/src/main/java/com/mipatrimonio/app/ui/networth/NetWorthViewModel.kt:5-55`, `app/src/main/java/com/mipatrimonio/app/ui/networth/NetWorthBreakdown.kt:3-38`). Mantener cuentas; reemplazar o retirar bloque de inversión. |
| Ajustes | `netWorthIncludeInvestments`, portfolio seleccionado, proveedor por defecto y claves API están en DataStore/secret store (`app/src/main/java/com/mipatrimonio/app/data/repository/SettingsRepository.kt:44-76`, `app/src/main/java/com/mipatrimonio/app/data/repository/SettingsRepository.kt:189-224`). Limpiar preferencias obsoletas con compatibilidad de lectura. |
| Cuentas | Crear cuenta de tipo inversión puede crear cartera vinculada; dependencias de archivado cuentan carteras/operaciones (`app/src/main/java/com/mipatrimonio/app/ui/accounts/AccountsViewModel.kt:128-158`, `app/src/main/java/com/mipatrimonio/app/data/repository/LedgerRepository.kt:29-40`). Quitar creación/vínculo y contadores, conservando reglas de transacciones/notificaciones. |
| Copia/CSV | Incluyen las cuatro tablas y validación FK; CSV de cuentas incorpora operaciones (sección 1). El formato/versionado de backup debe aceptar restauraciones antiguas sin reintroducir tablas o debe migrarlas explícitamente. |

## 7. Pasos de eliminación seguros

1. **Definir conservación de efectivo histórico:** antes de borrar operaciones, decidir cómo conservar en cuentas sus efectos de compra/venta/dividendo/comisión. Hoy el saldo se deriva de ellas (`app/src/main/java/com/mipatrimonio/app/domain/calc/BalanceCalculator.kt:19-47`); borrarlas sin materialización cambia saldos. Esta es la única dependencia con riesgo de pérdida funcional.
2. **Separar Trade Republic CASH:** crear un modelo/plan/repositorio solo de `Transaction`; conservar tarjeta, transferencias, intereses y efectivo de dividendos, y marcar TRADING fuera de alcance (`app/src/main/java/com/mipatrimonio/app/data/importer/TradeRepublicImportPlanner.kt:124-138`, `app/src/main/java/com/mipatrimonio/app/data/importer/TradeRepublicImportPlanner.kt:141-238`).
3. **Desacoplar superficies compartidas:** quitar inversión de movimientos, saldos, Inicio, patrimonio, cuentas, filtros, backup y CSV, o conectar el futuro agregado Rumbo donde corresponda. Mantener tests de cuentas/transacciones durante esta fase.
4. **Retirar navegación/UI:** pestaña Cartera, rutas Activos/Carteras/Detalle, destinos de Más, `ui/investments`, `ui/portfolios`, strings y ajustes de cotización (`app/src/main/java/com/mipatrimonio/app/ui/navigation/Destino.kt:23-82`, `app/src/main/java/com/mipatrimonio/app/ui/navigation/MiPatrimonioApp.kt:159-189`).
5. **Retirar importador Neverless** y su navegación/recursos; confirmar que no quedan referencias desde Backup (`app/src/main/java/com/mipatrimonio/app/ui/navigation/MiPatrimonioApp.kt:227-229`, `app/src/main/java/com/mipatrimonio/app/data/importer/NeverlessImportRepository.kt:9-88`).
6. **Retirar cotizaciones:** worker/agenda, repositorio, proveedores, lookup/OpenFIGI, secretos y settings; luego limpiar `AppContainer` (`app/src/main/java/com/mipatrimonio/app/AppContainer.kt:40-63`, `app/src/main/java/com/mipatrimonio/app/AppContainer.kt:94-104`).
7. **Retirar dominio/repositorio actual:** `InvestmentRepository`, calculadores/modelos/use cases y mappers, una vez que ningún consumidor compartido los importe.
8. **Migrar Room:** nueva versión que elimine `asset_price` → `investment_operation` → `asset` → `portfolio` e índices en ese orden. Actualizar entidad/DAO/database y añadir snapshot de esquema; mantener migraciones antiguas para usuarios que actualicen desde cualquier versión (`app/src/main/java/com/mipatrimonio/app/data/db/AppDatabase.kt:41-73`).
9. **Compatibilidad de copias:** subir versión/formato o tolerar campos antiguos; probar restauración de copia v14 y exportación sin tablas retiradas (`app/src/main/java/com/mipatrimonio/app/data/backup/BackupModels.kt:20-49`, `app/src/main/java/com/mipatrimonio/app/data/backup/BackupValidator.kt:4-63`).
10. **Barrido final:** recursos, manifest/WorkManager, preferencias y claves Keystore obsoletas; `rg` de `Portfolio|Asset|InvestmentOperation|AssetPrice|Quote|Neverless|INVERSIONES` antes de compilar.

## 8. Tests afectados

Eliminar o reemplazar los tests puramente de inversión:

- dominio: `PositionTest`, `InvestmentTransferTest`, `TradeSizingTest`, `PortfolioValueSeriesTest` (`app/src/test/java/com/mipatrimonio/app/domain/PositionTest.kt:1`, `app/src/test/java/com/mipatrimonio/app/domain/InvestmentTransferTest.kt:1`, `app/src/test/java/com/mipatrimonio/app/domain/TradeSizingTest.kt:1`, `app/src/test/java/com/mipatrimonio/app/domain/usecase/PortfolioValueSeriesTest.kt:1`);
- datos: `InvestmentTransferRepositoryTest`, todos los tests de `data/quotes`, Neverless y las ramas TRADING de Trade Republic (`app/src/test/java/com/mipatrimonio/app/data/InvestmentTransferRepositoryTest.kt:1`, `app/src/test/java/com/mipatrimonio/app/data/quotes/QuoteRepositoryTest.kt:1`, `app/src/test/java/com/mipatrimonio/app/data/importer/NeverlessImportPlannerTest.kt:1`, `app/src/test/java/com/mipatrimonio/app/data/importer/TradeRepublicImportPlannerTest.kt:1`);
- UI: todo `ui/investments/*Test`, `PortfoliosViewModelTest`, `NetWorthBreakdownTest` y `QuoteSettingsViewModelTest` (`app/src/test/java/com/mipatrimonio/app/ui/investments/InvestmentsViewModelTest.kt:1`, `app/src/test/java/com/mipatrimonio/app/ui/portfolios/PortfoliosViewModelTest.kt:1`, `app/src/test/java/com/mipatrimonio/app/ui/networth/NetWorthBreakdownTest.kt:1`, `app/src/test/java/com/mipatrimonio/app/ui/settings/QuoteSettingsViewModelTest.kt:1`).

Actualizar, no borrar, los tests compartidos:

- `BackupTest` y `CsvExportTest` para el nuevo formato sin tablas/hojas de inversión (`app/src/test/java/com/mipatrimonio/app/data/backup/BackupTest.kt:1`, `app/src/test/java/com/mipatrimonio/app/data/export/CsvExportTest.kt:1`).
- `AccountsViewModelTest`, `HomeAggregatorTest`, `HomeViewModelTest`, `MovementsBalanceCalculatorTest`, `MovementsViewModelTest`, `MovementFiltersTest`, `SnapshotTest`, `HistoryComponentsTest` para retirar fixtures/expectativas de inversión pero preservar efectivo (`app/src/test/java/com/mipatrimonio/app/ui/accounts/AccountsViewModelTest.kt:1`, `app/src/test/java/com/mipatrimonio/app/ui/home/HomeAggregatorTest.kt:1`, `app/src/test/java/com/mipatrimonio/app/domain/MovementsBalanceCalculatorTest.kt:1`, `app/src/test/java/com/mipatrimonio/app/domain/usecase/HistoryComponentsTest.kt:1`).
- Tests de migración: añadir la migración de eliminación y adaptar los que encadenan hasta la nueva versión; especialmente 2→3, 4→5, 5→6, 8→9, 9→10 y 10→11, que hoy afirman columnas de inversión (`app/src/test/java/com/mipatrimonio/app/data/Migration2to3Test.kt:1`, `app/src/test/java/com/mipatrimonio/app/data/Migration10to11Test.kt:1`).
- Trade Republic: conservar/adaptar adapter, repository, ViewModel e `ImportReviewRulesTest` para CASH; añadir casos explícitos que descarten TRADING y conserven interés, tarjeta, transferencia y efectivo de dividendo (`app/src/test/java/com/mipatrimonio/app/data/importer/TradeRepublicCsvAdapterTest.kt:1`, `app/src/test/java/com/mipatrimonio/app/ui/importer/TradeRepublicImportViewModelTest.kt:1`).
