# ADR-001 — Datos compartidos entre Rumbo (escritorio) y Mi Patrimonio (Android) con Supabase

- **Estado:** aceptado (2026-10-27). Lo redacta Claude a partir de `ANALISIS_RUMBO.md` e `INVENTARIO_INVERSION_APP.md`.
- **Alcance:** fichas T-071 a T-077.

## Contexto
**Rumbo** (fork propio, Python/Flask local) gestiona las inversiones. **Mi Patrimonio** (Android, offline-first con Room) lleva el día a día y muestra un resumen de cartera. Hasta ahora Rumbo guarda un `cartera.json` por perfil, con ids secuenciales y sin control de concurrencia.

Decisiones del usuario:
- El móvil puede crear **compras**, sobre todo desde notificaciones.
- Una venta, un dividendo o una compra con **cuenta de liquidez** aparece como apunte en esa cuenta de la app.
- La app publica los **saldos de sus cuentas** y Rumbo los muestra como `efectivo`.
- Las inversiones antiguas de la app **no se migran**.

## Decisión
### 1. Plataforma y seguridad
- Supabase (Postgres, Auth y PostgREST), plan gratuito y región UE. Un usuario con email y contraseña.
- Todas las tablas llevan `user_id uuid not null default auth.uid()` y RLS `using (user_id = auth.uid()) with check (user_id = auth.uid())` para select, insert, update y delete.
- Los clientes usan **solo la clave anon** con el JWT del usuario; **nunca `service_role`**.
- La URL y la clave anon **no se versionan**:
  - en la app se introducen en Ajustes → Sincronización;
  - en Rumbo van en `mis_datos/supabase.json` (ignorado).
- Los tokens de refresco se guardan:
  - en la app, en el almacén cifrado existente (Keystore);
  - en Rumbo, en `mis_datos/.supabase_sesion`, nunca en el repositorio.

### 2. Esquema (Postgres, `public`)
Columnas comunes en todas las tablas: `id uuid pk default gen_random_uuid()`, `user_id`, `created_at timestamptz default now()`, `updated_at timestamptz default now()` (un trigger lo pone a `now()` en cada UPDATE) y `deleted_at timestamptz null` (borrado lógico; **ningún cliente hace DELETE físico**).

| Tabla | Columnas propias | Escribe |
|---|---|---|
| `perfiles` | `nombre text not null`, `orden int default 0` | Escritorio |
| `productos` | `perfil_id uuid fk perfiles`, `nombre text not null`, `corto text`, `tipo text check in (fondo, etf, accion, cripto, commodity, bono, pension, efectivo, inmueble, deuda, p2p, otro)`, `fuente text check in (morningstar, yahoo, coingecko, manual)`, `codigo text`, `moneda text default 'EUR'`, `identificador text`, `extra jsonb default '{}'` (resto de campos de Rumbo: `entidad`, `clase`, `gestora`, `tipoDetalle`, `papel`, `largoPlazo`, `liquidez`, `slot`, `ter`, `riesgo`, `respaldo`, `respaldoMoneda`, `vivo`, `expoComo`, `exposicion` escrita a mano y campos heredados) | Escritorio |
| `movimientos` | `perfil_id`, `producto_id uuid fk productos`, `fecha date`, `tipo text check in (compra, venta, dividendo, comision, traspaso, ajuste)`, `unidades numeric(28,10) default 0`, `importe numeric(18,2) default 0` (EUR), `comision numeric(18,2)`, `nota text`, `destino_id uuid fk productos null`, `unidades_destino numeric(28,10)`, `total boolean`, `cuenta_id uuid fk cuentas_app null`, `origen text check in (escritorio, movil, notificacion, importacion) default 'escritorio'`, `extra jsonb default '{}'` (`fechaEstimada`, etc.) | Ambos (ver §3) |
| `valoraciones` | `perfil_id`, `producto_id`, `fecha date`, `valor numeric(18,2)`, `aportado numeric(18,2) null`, `unique (producto_id, fecha) where deleted_at is null` | Escritorio |
| `ajustes_cartera` | `perfil_id unique`, `titular text`, `moneda text`, `datos jsonb` (`comparador`, `hitos`, `objetivo`, `colchon`, `objetivoReparto`, `periodicas`) | Escritorio |
| `cuentas_app` | `id` = **el mismo id de la cuenta en Room**, `perfil_id uuid null` (perfil de Rumbo donde se muestra; null = principal), `nombre text`, `tipo text`, `moneda text`, `saldo numeric(18,2)`, `saldo_fecha timestamptz`, `archivada boolean` | Solo la app |
| `resumen_cartera` | `perfil_id uuid unique`, `generado_at timestamptz`, `version int`, `datos jsonb` (contrato en §5) | Solo el escritorio |

**Índices:** `(user_id, updated_at)` en todas las tablas (para la sincronización incremental), `movimientos(producto_id)`, `movimientos(cuenta_id)` y `valoraciones(producto_id, fecha)`.

**Restricciones:**
- `importe >= 0`, `comision >= 0`;
- un traspaso exige `destino_id`;
- un movimiento con `cuenta_id` solo puede ser `compra`, `venta`, `dividendo` o `comision`.

### 3. Propiedad y conflictos
- **El escritorio** hace CRUD de todo salvo `cuentas_app`.
- **El móvil:**
  - lee todo;
  - inserta movimientos `compra` con `origen` = `movil` o `notificacion`;
  - edita y borra (lógicamente) **solo los suyos** (`origen` ≠ `escritorio`/`importacion`);
  - hace upsert de `cuentas_app`.
  - Esta restricción del móvil es una regla de cliente: RLS es por usuario y los dos clientes son el mismo usuario.
- **Conflictos:** gana la última escritura por fila, según el `updated_at` del servidor. Con un solo usuario y una propiedad clara, los conflictos reales son raros.
- **Ids:** UUID generados en el cliente (`uuid4`, `UUID.randomUUID()`) para crear sin conexión e insertar de forma idempotente con `Prefer: resolution=merge-duplicates`.

### 4. Efecto en caja (apunte de liquidez en la app)
- Un `movimiento` con `cuenta_id` mueve la cuenta de la app con un importe **derivado**. No se crea ninguna `Transaction`, y por tanto **nunca es gasto ni ingreso**: es el mismo criterio que el actual `MovementItem.Investment`.
- Regla, coherente con Rumbo, donde el `importe` de la compra incluye la comisión:

  | Tipo | Efecto en la cuenta |
  |---|---|
  | `compra` | −importe |
  | `venta` | +importe |
  | `dividendo` | +importe |
  | `comision` | −importe |
  | `traspaso`, `ajuste` | 0 |

  **T-072 debe confirmar** con el código de Rumbo si el `importe` de una venta es neto de la comisión. Si no lo es, la venta pasa a +importe − comisión, y esta tabla se actualiza.
- Solo vale para cuentas en EUR (Rumbo calcula en EUR). Una cuenta de otra divisa no se puede elegir como cuenta de liquidez.
- **Rumbo** muestra cada `cuentas_app` no archivada como un producto virtual `efectivo` del perfil indicado:
  - su valor es el último `saldo`, con una valoración virtual en `saldo_fecha`;
  - no se guarda como fila en `productos`;
  - su id calculado es `cuenta_app:<uuid>`.

### 5. `resumen_cartera.datos` (contrato v1, JSON)
```json
{"version":1,"generado":"ISO","moneda":"EUR","preciosActualizados":"ISO|null",
 "total":{"valor":0,"aportado":0,"ganancia":0,"rentabilidad":0,"tir":null,"realizado":0},
 "porClase":[{"clase":"fondo","valor":0,"peso":0}],
 "productos":[{"id":"uuid","nombre":"","tipo":"","valor":0,"aportado":0,"ganancia":0,
               "rentabilidad":0,"tir":null,"unidades":0,"precio":null,"peso":0}],
 "avisos":[]}
```
- Los números son JSON `number` en EUR y los porcentajes son fracciones.
- La app los convierte a céntimos con `BigDecimal`.
- Rumbo lo publica (upsert) al terminar cada recálculo del perfil real, nunca en el modo demo.

### 6. Sincronización
- **App:**
  - **Bajada incremental** por tabla con `updated_at >= cursor` (cursor = mayor `updated_at` visto, deduplicando por id). Llega a Room: tablas espejo `sync_*` y `resumen`.
  - Se lanza al abrir la app, al tirar para refrescar y con WorkManager periódico (1 h, con red).
  - **Subida mediante una cola de salida en Room** (`sync_outbox`: tabla, id, json y número de intentos), procesada por WorkManager con *backoff*. Contiene las compras del móvil y los saldos (upsert de `cuentas_app` con *debounce*, cuando cambia el saldo).
  - **Sin conexión,** todo funciona; la Cartera muestra «Actualizado a <fecha>».
- **Rumbo:**
  - Al arrancar y en «Actualizar» baja el `cfg` completo del perfil y lo deja en memoria.
  - **Cada escritura (`cambia(fn)`)** baja primero el perfil, aplica la función, calcula las **diferencias** con el estado anterior (por id: altas o cambios con upsert, bajas con `deleted_at`) y las sube.
  - Si no hay red, abre en solo lectura desde el espejo local `mis_datos/espejo_<perfil>.json`.
  - La caché de precios y exposición, `calculado_*`, `historico.json`, `estado.json` y `.acceso` siguen en local.
- **«Todos juntos»** sigue siendo una proyección de solo lectura (ids prefijados) y nunca se persiste.

### 7. Números
- **Supabase:** `numeric`.
- **Rumbo:** convierte a `float` al cargar, para no tocar el motor, y redondea a 2 decimales (importes) o 10 (unidades) al subir.
- **App:** `BigDecimal` → céntimos y unidades escaladas, con las utilidades de `domain/model/Money.kt`.

## Consecuencias
- Rumbo deja de depender del sistema de archivos para los datos del usuario. Los perfiles se convierten en filas y borrarlos es un borrado lógico.
- Las copias propias de Rumbo pasan a ser una exportación o importación JSON explícita.
- El proyecto gratuito de Supabase se pausa tras 7 días sin actividad. La sincronización periódica de la app lo mantiene activo; si llega a pausarse, se reanuda desde el panel.
- Pendiente de decisión del usuario, que afecta solo a T-074: qué hacer con el efecto en caja de las operaciones antiguas de la app al eliminarlas.
