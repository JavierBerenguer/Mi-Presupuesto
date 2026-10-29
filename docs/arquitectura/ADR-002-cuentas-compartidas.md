# ADR-002 — Cuentas compartidas entre la app y el escritorio

- **Estado:** aceptado (2026-10-29). Amplía ADR-001 §2-§4.
- **Fichas:** T-084 (Supabase + escritorio) y T-085 (app).

## Contexto
Hoy las cuentas son de la app:
- la tabla `cuentas_app` solo la escribe el móvil;
- Rumbo la proyecta como productos virtuales `efectivo` (`cuenta_app:<uuid>`);
- en Rumbo existen además productos `efectivo` propios (p. ej. «Neverless (efectivo)», valorados con `valoraciones`).

El usuario quiere **una sola lista de cuentas, creada y editada desde los dos lados con los mismos datos**, y una casilla que decida si la cuenta se usa en los apuntes de la app.

## Decisión (usuario + Claude)
1. **La tabla `cuentas_app` pasa a ser la tabla de cuentas compartida.** Se conserva el nombre para no romper T-072, T-073 ni T-075. Columnas nuevas (migración aditiva de Supabase `20261029…_cuentas_compartidas.sql`):

   | Columna | Tipo | Significado |
   |---|---|---|
   | `corto` | text | Nombre corto (opcional) |
   | `entidad` | text | Banco o bróker (opcional) |
   | `color` | int | 1..12, como `slot` en Rumbo |
   | `liquidez` | bool | Por defecto `true`. Cuenta para «Liquidez y fondo de emergencia» |
   | `uso_app` | bool | Por defecto `true`. **«Uso en app»** |
   | `saldo_inicial` | numeric(18,2) | Por defecto 0 |

   - El `tipo` existente usa los valores de la app: `corriente`, `ahorro`, `efectivo`, `inversion`, `cripto` u `otra`.
   - `moneda`, `nombre`, `archivada` y `perfil_id` ya existen.
2. **Tabla nueva `saldos_cuenta`** (con RLS por usuario y borrado lógico): `id`, `cuenta_id` (FK compuesta con `user_id`), `fecha` date, `saldo` numeric(18,2) y `unique (cuenta_id, fecha) where deleted_at is null`. Es el historial de saldos de las cuentas **sin «Uso en app»**.
3. **Cómo se calcula el saldo:**
   - **Con «Uso en app»:** saldo inicial + apuntes y transferencias de la app + liquidez de Rumbo (ADR-001 §4). Lo calcula la app y lo publica en `saldo` y `saldo_fecha`, como hoy. Solo estas cuentas:
     - aparecen en el desplegable **Cuenta** de los apuntes, en las transferencias, en las cuentas de las notificaciones bancarias y en la liquidez de compras desde el móvil;
     - son elegibles como **cuenta de liquidez** en Rumbo.
   - **Sin «Uso en app»:** el saldo es **el último registro de `saldos_cuenta`**, que se puede anotar desde el móvil («Actualizar saldo») o desde el escritorio. `cuentas_app.saldo` y `saldo_fecha` reflejan ese último registro (lo escribe quien lo anota). No admiten apuntes ni liquidez.
4. **Edición desde los dos lados:**
   - crear, editar y archivar cuentas se puede hacer desde la app o desde el escritorio, con los **mismos campos**: nombre, nombre corto, tipo, entidad, moneda, saldo inicial, cuenta como liquidez, color, uso en app y archivada;
   - conflictos: gana la última escritura por fila (`updated_at`);
   - no hay borrado físico: borrar marca `deleted_at` y solo se permite sin apuntes ni movimientos vinculados (si los hay, se archiva).
5. **Cambiar «Uso en app»:**
   - **De sí a no:** solo si la cuenta no tiene apuntes ni transferencias; si los tiene, se muestra un mensaje («Archiva la cuenta o mueve sus apuntes»). Al hacerlo se crea un `saldos_cuenta` con el saldo actual.
   - **De no a sí:** el saldo inicial pasa a ser el último saldo registrado.
6. **En Rumbo:**
   - todas las cuentas vivas del perfil (o con `perfil_id` nulo) se proyectan como productos `efectivo`:
     - las de uso en app, con su `saldo` actual;
     - las demás, con su historial de `saldos_cuenta` como valoraciones, para que la evolución del patrimonio sea correcta;
   - **las cuentas se crean y editan en Rumbo desde una sección «Cuentas»** (y la opción «Cuenta / efectivo» del alta de producto lleva allí). Rumbo deja de crear productos `efectivo` propios; los existentes siguen funcionando;
   - el color y la liquidez de la cuenta se usan en las gráficas y en «Liquidez y fondo de emergencia».
7. **En la app (Room):** la entidad `account` gana `shortName`, `institution`, `color`, `liquidity`, `useInApp` y la marca de sincronización. Las cuentas se sincronizan **en las dos direcciones** con `cuentas_app`. Las cuentas sin uso en app se ven en **Cuentas** y en **Patrimonio**, pero **no** en los selectores de apuntes, transferencias, notificaciones ni compras.

## Consecuencias
- La primera carga ya no crea el producto «Neverless (efectivo)». Neverless será una **cuenta sin uso en app** con su saldo en `saldos_cuenta`, y se puede crear desde cualquiera de los dos lados.
- Es la primera tabla con escritura bidireccional real en la app. Las tablas de cartera siguen el modelo de ADR-001.
