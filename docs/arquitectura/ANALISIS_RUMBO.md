# Análisis técnico de Rumbo

> Código analizado: `D:\XX_Claude\App - Rea - Rumbo\rumbo-main`. Las referencias `archivo:línea` son relativas a esa raíz. No se han leído ni reproducido valores personales; del directorio `demo/` solo se comprobó la forma del JSON.

## 1. Arquitectura, arranque y seguridad

Rumbo es una aplicación monolítica local: Flask sirve una SPA estática y una API; `servidor.py` coordina persistencia, cálculo e importación, mientras `motor.py` produce el objeto `DATOS` consumido por la web (`app/servidor.py:26-44`, `app/servidor.py:390-405`, `app/motor.py:942-954`).

### Arranque

| Entrada | Flujo |
|---|---|
| Windows | `Iniciar.bat` busca `uv`; si no existe busca Python ≥3.10, crea `.venv-pip`, instala `requirements.txt` y ejecuta `python -m app`; puede ofrecer instalar `uv` con `winget` (`Iniciar.bat:33-85`). |
| Linux/macOS | Mismo orden: `uv`, Python ≥3.10 y, por último, instalador de `uv`; ambos terminan en `python -m app` (`Iniciar.sh:23-67`, `Iniciar.command:24-64`). |
| Módulo Python | `app/__main__.py` importa y llama directamente a `servidor.main()` (`app/__main__.py:1-4`). |
| `main()` | Evita una segunda instancia mediante `/api/ping`, recalcula al arrancar, intenta actualizar precios si son antiguos, busca un puerto libre (hasta diez en local) y lanza el servidor Flask multihilo (`app/servidor.py:1161-1229`). |
| Docker | Imagen Python 3.12, usuario sin privilegios, volumen `/data`, puerto 8765 y healthcheck `/api/ping`; ejecuta `python -m app` (`Dockerfile:6-15`, `Dockerfile:21-40`). Compose publica `8765:8765` y conserva `rumbo_datos` (`docker-compose.yml:10-26`). |

Variables de entorno:

| Variable | Efecto |
|---|---|
| `PATRIMONIO_DATOS` | Sustituye `mis_datos/` como raíz persistente (`app/servidor.py:31-35`). |
| `PATRIMONIO_PUERTO` | Puerto base; 8765 por defecto (`app/servidor.py:36`). |
| `PATRIMONIO_HOST` | Interfaz de escucha; `127.0.0.1` por defecto, `0.0.0.0` en Docker (`app/servidor.py:103-110`, `Dockerfile:10-14`). |
| `PATRIMONIO_NO_ABRIR` | Impide abrir el navegador, usado en Docker/pruebas (`app/servidor.py:1176-1181`, `app/servidor.py:1220-1223`). |
| `PATRIMONIO_DOCKER` | Marca la instalación como Docker para la API de versión (`app/servidor.py:113-115`, `app/servidor.py:947-960`). |
| `PATRIMONIO_HOSTS_PERMITIDOS` | Lista adicional separada por comas para nombres de host aceptados (`app/servidor.py:98-100`). |

Seguridad: crea una llave aleatoria URL-safe de 32 bytes en `.acceso`, la acepta una vez en `?acceso=`, la retira mediante redirección y la fija en cookie `HttpOnly`, `SameSite=Strict` por 400 días; no marca `Secure`, coherente con HTTP local (`app/servidor.py:74-95`, `app/servidor.py:180-193`, `app/servidor.py:241-245`). Valida `Host` contra loopback o red privada/nombres locales cuando escucha en red, y permite una lista explícita (`app/servidor.py:117-149`). Rechaza peticiones cross-site con `Sec-Fetch-Site` y, para mutaciones, `Origin`/`Referer`; exige además `X-Rumbo-Perfil` para impedir escribir en un perfil cambiado desde otra pestaña (`app/servidor.py:160-194`, `app/servidor.py:224-238`). Añade CSP, aislamiento same-origin, `nosniff`, `DENY`, `no-referrer` y `no-store`; la CSP solo permite conexiones del navegador a CoinGecko/Binance además del propio origen (`app/servidor.py:212-221`, `app/servidor.py:241-253`). La subida máxima es 25 MiB (`app/servidor.py:59-62`).

La única salida HTTP del backend pasa normalmente por `red.abrir`: solo HTTPS, GET y seis hosts permitidos; el ping local de instancia es la excepción directa (`app/red.py:15-35`, `app/servidor.py:1161-1166`).

### Endpoints completos

Convención: salvo `/api/ping`, todos pasan por llave/host/origen; las mutaciones de cartera terminan en `cambia()`, que lee `cartera.json`, valida, crea copia automática, escribe y recalcula `calculado_*` (`app/servidor.py:451-465`).

| Método y ruta | Entrada | Respuesta | Colecciones/archivos |
|---|---|---|---|
| `GET /` | `acceso` opcional | `index.html` o redirección para fijar cookie | Lee web estática y `.acceso` (`app/servidor.py:180-193`, `app/servidor.py:390-392`). |
| `GET /datos.js` | — | JS con `window.DATOS` y `window.PERFILES` | Lee/recalcula `calculado_<modo>.json`, `cartera.json`, `perfiles.json` (`app/servidor.py:395-405`). |
| `POST /api/actualizar` | Sin cuerpo | `{ok, avisos}` | Lee cartera/caché; escribe caché, `estado.json`, `calculado_*`, `historico.json` (`app/servidor.py:363-378`, `app/servidor.py:408-415`). |
| `GET /api/cartera` | — | `{modo, cartera, tipos, fuentes, tiposMovimiento}` | Lee cartera activa o demo (`app/servidor.py:346-350`, `app/servidor.py:418-421`). |
| `GET /api/buscar?q=` | Texto | `{resultados, sinConexion}` | Red/caché en memoria de fallos; no persiste usuario (`app/servidor.py:424-428`). |
| `GET /api/cambio?moneda=&fecha=` | ISO 4217/fecha ISO | `{ok,cambio}` | Lee/actualiza caché FX (`app/servidor.py:511-551`). |
| `POST /api/<coleccion>` | `coleccion` ∈ `productos`, `movimientos`, `valoraciones`; JSON del elemento; movimientos admiten `moneda` | `{ok,item,cartera,avisos}` | Lee/escribe la colección homónima en cartera y derivados; comprueba precio del producto online (`app/servidor.py:431-434`, `app/servidor.py:519-572`). |
| `DELETE /api/<coleccion>/<ident>` | Colección anterior e ID en ruta | Respuesta común de `cambia()` | Borra el elemento; un producto arrastra movimientos/valoraciones y pesos (`app/servidor.py:575-579`, `app/almacen.py:288-298`). |
| `POST /api/objetivo` | `{activo,importe,etiqueta}` | Respuesta común | Escribe `objetivo` (`app/servidor.py:582-585`, `app/almacen.py:142-151`). |
| `POST /api/colchon` | `{borrar}` o `{modo,meses,gastoMensual}`/`{modo,importe}` | Respuesta común | Escribe/elimina `colchon` (`app/servidor.py:588-591`, `app/almacen.py:156-176`). |
| `POST /api/reparto` | `{por,pesos,vigilar,rebalancear,conEfectivo,cuenta}` | Respuesta común | Escribe/elimina `objetivoReparto` (`app/servidor.py:594-597`, `app/almacen.py:181-208`). |
| `GET /api/unidades?producto=&fecha=&excluir=` | IDs/fecha | `{ok,unidades}` | Lee productos/movimientos (`app/servidor.py:600-609`). |
| `POST /api/periodicas/ver` | Regla periódica | `{ok,plan}` | Solo lee cartera/precios (`app/servidor.py:620-631`). |
| `POST /api/periodicas/crear` | Regla periódica | Respuesta común | Añade compras y registra regla en `periodicas` (`app/servidor.py:634-637`, `app/periodicas.py:103-121`). |
| `POST /api/periodicas/olvidar` | Clave de regla | Respuesta común | Elimina regla de `periodicas`, no compras (`app/servidor.py:640-643`, `app/periodicas.py:124-136`). |
| `GET /api/fiscal?ano=` | Año opcional | `{ok,csv,ano,anos,ventas,rescates,cobros,...}` | Solo lee cartera y ejecuta FIFO (`app/servidor.py:646-656`, `app/fiscal.py:71-183`). |
| `GET /api/precio?producto=&fecha=` | ID/fecha | `{ok,precio,fecha}` | Lee producto y caché; puede completar precio (`app/servidor.py:659-671`). |
| `POST /api/repartir-colores` | Sin cuerpo | Respuesta común | Lee calculado; escribe `slot` de productos (`app/servidor.py:674-679`). |
| `POST /api/empezar` | `{desde:"ejemplo"}` o vacío | `{ok}` | Crea cartera propia vacía o copia del demo; recalcula (`app/servidor.py:682-694`). |
| `POST /api/importar/previsualizar` | `multipart`: `origen`, `archivos[]`, `texto` | `{ok,token,informe}` | Lee cartera y precios; guarda `Plan` solo en memoria `PLANES` (`app/servidor.py:699-730`). |
| `POST /api/importar/revisar` | `{token,respuestas}` | `{ok,token,informe}` | Lee cartera/plan en memoria; no persiste (`app/servidor.py:733-756`). |
| `POST /api/importar/confirmar` | `{token,respuestas}` | Respuesta común + `informe` | Consume `PLANES`; escribe productos/movimientos/valoraciones (`app/servidor.py:759-777`, `app/importar.py:715-837`). |
| `GET /api/plantilla.xlsx` | — | XLSX | Generado en memoria (`app/servidor.py:780-783`). |
| `GET /api/plantilla.csv` | — | CSV vacío | Generado en memoria (`app/servidor.py:786-789`). |
| `GET /api/exportar-excel` | — | XLSX | Lee cartera; generado en memoria (`app/servidor.py:792-798`, `app/plantilla.py:114-162`). |
| `GET /api/prompt` | — | `{texto}` | Lee `app/prompt_ia.txt` (`app/servidor.py:801-804`). |
| `GET /api/copias` | — | `{copias:[archivo,fecha,motivo,productos,movimientos]}` | Lista/lee `copias/*.json` (`app/servidor.py:853-870`). |
| `GET /api/copia/descargar` | — | JSON adjunto | Lee `cartera.json` (`app/servidor.py:873-881`). |
| `POST /api/copia/subir` | `multipart archivo` | `{ok}` | Valida y reemplaza cartera; borra derivados y recalcula (`app/servidor.py:817-850`, `app/servidor.py:884-899`). |
| `POST /api/copia/recuperar` | `{archivo}` | `{ok}` | Lee copia; guarda copia previa, restaura cartera y derivados (`app/servidor.py:902-921`). |
| `GET /api/exportar-web?ocultar=1` | Bandera opcional | HTML autocontenido | Lee calculado y activos web; no escribe (`app/servidor.py:926-935`, `app/exportar.py:86-131`). |
| `GET /api/version` | — | `{actual,ultima,repo,docker,hayNueva}` | Lee `VERSION`; consulta GitHub y cachea 24 h en memoria (`app/servidor.py:938-960`). |
| `GET /api/novedades` | — | `{actual,tuya,nueva,repo,docker}` | Lee `novedades.json`; consulta GitHub y cachea 24 h en memoria (`app/servidor.py:967-1001`). |
| `POST /api/reiniciar` | `{a:"vacia"}` u otro | `{ok}` | Mueve cartera a copia, borra derivados y crea vacía o vuelve a demo (`app/servidor.py:1004-1024`). |
| `GET /api/perfiles` | — | `{ok,activo,todos,lista}` | Lee `perfiles.json` y presencia de carteras (`app/servidor.py:1027-1036`). |
| `POST /api/perfiles` | `{nombre}` | `{ok,id}` | Crea carpeta/cartera, actualiza perfiles y activo (`app/servidor.py:1039-1056`). |
| `POST /api/perfiles/activar` | `{id}` | `{ok}` | Escribe perfil activo y recalcula (`app/servidor.py:1059-1071`). |
| `POST /api/perfiles/renombrar` | `{id,nombre}` | `{ok}` | Escribe nombre en perfiles (`app/servidor.py:1074-1087`). |
| `POST /api/perfiles/borrar` | `{id}` | `{ok}` | Mueve carpeta a `copias/perfil_borrado_*`, actualiza perfiles y recalcula (`app/servidor.py:1090-1108`). |
| `GET /api/diagnostico` | — | `{ok,texto}` | Lee cartera, perfiles, estado, calculado y últimos errores en memoria; no incluye importes/nombres (`app/servidor.py:1111-1146`). |
| `GET /api/ping` | — | `{app:"patrimonio"}` | Sin datos; único endpoint sin llave (`app/servidor.py:165-166`, `app/servidor.py:1149-1151`). |
| `GET /<path:archivo>` | Ruta estática | Archivo web | Lee `app/web` mediante `send_from_directory` (`app/servidor.py:1154-1156`). |

## 2. Modelo de datos persistido

No hay esquema/migración: `cartera.json` es un objeto flexible y los consumidores usan `get()`/valores por defecto. La validación fuerte solo se aplica al editar o restaurar; por ello “opcional” significa que el motor tolera ausencia, no que toda combinación tenga sentido (`app/almacen.py:217-276`, `app/servidor.py:817-840`). Los números persistidos son JSON `number` basado en `float`, no decimal exacto (`app/almacen.py:83-101`).

### Raíz de `cartera.json`

| Campo | Tipo / obligación | Valores y escritor |
|---|---|---|
| `version` | entero, presente en nuevas | `1`; plantilla/arranque (`app/almacen.py:22-28`). |
| `titular` | texto, presente en nuevas | Nombre del panel; plantilla, perfil nuevo o copia demo (`app/almacen.py:22-28`, `app/servidor.py:1048-1053`). |
| `productos` | lista, obligatoria al restaurar | Productos; editor/importador (`app/servidor.py:821-830`, `app/importar.py:715-837`). |
| `movimientos` | lista, obligatoria al restaurar | Movimientos; editor/periódicas/importador (`app/servidor.py:831-835`, `app/periodicas.py:103-121`). |
| `valoraciones` | lista, obligatoria al restaurar | Valores/saldos manuales; editor/importador (`app/servidor.py:836-839`). |
| `comparador` | lista opcional | Nueva cartera incluye referencia real; el motor también acepta carteras ponderadas (`app/almacen.py:25`, `app/motor.py:1535-1546`). |
| `hitos` | lista de números opcional | Umbrales de patrimonio; plantilla y edición histórica/manual (`app/almacen.py:26`, `app/motor.py:1346-1350`). |
| `objetivo` | objeto opcional | Objetivo propio (`app/almacen.py:142-151`). |
| `colchon` | objeto opcional | Fondo de emergencia (`app/almacen.py:156-176`). |
| `objetivoReparto` | objeto opcional | Rebalanceo (`app/almacen.py:181-208`). |
| `periodicas` | lista opcional | Reglas recordadas (`app/periodicas.py:103-136`). |
| `moneda` | texto opcional | Divisa de visualización, `EUR` por defecto; el cálculo interno sigue en EUR (`app/motor.py:845-860`, `app/motor.py:1678-1683`). |

### Producto

| Campo | Tipo / obligación | Valores/semántica y quién escribe |
|---|---|---|
| `id` | texto, obligatorio | Slug único derivado de `corto`; editor/importador. Referencia externa de movimientos, valores y pesos (`app/almacen.py:117-128`, `app/almacen.py:256-269`). |
| `nombre` | texto, obligatorio al editar | Máx. 200; editor/importador (`app/almacen.py:213-225`). |
| `corto` | texto opcional | Por defecto primeros 24 caracteres de nombre (`app/almacen.py:251-252`). |
| `tipo` | enum obligatorio al editar | `fondo`, `etf`, `accion`, `cripto`, `commodity`, `bono`, `pension`, `efectivo`, `inmueble`, `deuda`, `p2p`, `otro` (`app/motor.py:432-437`). |
| `fuente` | enum | `morningstar`, `yahoo`, `coingecko`, `manual`; forzada a manual para efectivo/deuda (`app/motor.py:444-445`, `app/almacen.py:223-228`). |
| `codigo` | texto condicional | Obligatorio si fuente no manual; ticker, secid o coin-id según fuente (`app/almacen.py:229-231`, `app/motor.py:569-570`). |
| `moneda` | texto | Tres letras, `GBp`/`GBX`, `EUR` por defecto; editor (`app/almacen.py:232-235`). |
| `identificador` | texto opcional | Normalmente ISIN; editor/importador/buscador (`app/almacen.py:213-220`, `app/exposicion.py:188-204`). |
| `entidad`, `clase`, `gestora`, `tipoDetalle`, `papel` | texto opcional | Metadatos de presentación/importación; `papel` admite 400 caracteres, resto 200 (`app/almacen.py:213-220`). |
| `largoPlazo` | booleano | `true` por defecto; editor (`app/almacen.py:243-248`). |
| `liquidez` | booleano opcional | Clasificación manual; editor (`app/almacen.py:243-248`). |
| `slot` | entero opcional | Color 1..12 de facto; si falta se asigna el menos usado (`app/almacen.py:243-269`, `app/almacen.py:279-285`). |
| `ter` | número opcional | Fracción anual (el formulario recibe porcentaje y divide por 100), ≥0 (`app/almacen.py:236-248`). |
| `riesgo` | entero opcional | 1..7 (`app/almacen.py:237-248`). |
| `respaldo`, `respaldoMoneda` | texto opcional | Ticker Yahoo y divisa usados para sustituir cierres que diverjan >3 %; editor/datos preparados (`app/almacen.py:213-250`, `app/motor.py:312-358`). |
| `vivo` | texto opcional | Coin-id para ticker intradía; conservado por editor, consumido por motor (`app/almacen.py:213-214`, `app/motor.py:1664-1676`). |
| `expoComo` | ISIN opcional | Hace obtener exposición como otro fondo; editor valida 12 caracteres (`app/almacen.py:252-254`, `app/exposicion.py:188-217`). |
| `exposicion` | objeto opcional | `paises`/`sectores`: listas `[nombre, porcentaje]`; `rv`: fracción 0..1; `top10`: lista o `null`; `actualizado`, `fuente`, posible `empresa`. Puede venir de datos preparados; la descarga se cachea y el motor la incorpora solo a su copia calculada, no guarda de vuelta en cartera (`app/exposicion.py:123-166`, `app/motor.py:1193-1204`). |
| `indice`, `indiceDetalle`, `politica`, `mercado`, `isin` | texto opcional legado/importado | El motor los sanea para mostrar; no los genera el formulario actual (`app/motor.py:478-492`). |

### Movimiento

| Campo | Tipo / obligación | Semántica y escritor |
|---|---|---|
| `id` | texto obligatorio | `m` + entero creciente; editor/importador/periódicas (`app/almacen.py:126-128`, `app/almacen.py:370-379`). |
| `fecha` | `YYYY-MM-DD` obligatorio | 1950..hoy (`app/almacen.py:104-114`). |
| `producto` | id obligatorio | Producto origen/único (`app/almacen.py:322-340`). |
| `tipo` | enum | Persistidos por editor: `compra`, `venta`, `dividendo`, `comision`, `traspaso`, `ajuste`; importación puede producir los mismos. `traspaso_salida/entrada` son solo expansión interna (`app/almacen.py:18-20`, `app/motor.py:573-584`). |
| `unidades` | número | Compra/venta; en ajuste significa saldo final; 0 para manuales (`app/almacen.py:343-365`, `app/almacen.py:442-461`). |
| `importe` | número | Positivo, EUR; 0 en ajuste (`app/almacen.py:341-365`, `app/servidor.py:519-540`). |
| `comision` | número opcional | ≥0 y menor que importe; editor/importador (`app/almacen.py:350-367`). |
| `nota` | texto opcional | Máx. 200, sin `<`/`>`; editor/importador/conversión de divisa (`app/almacen.py:135-137`, `app/almacen.py:368-369`). |
| `destino`, `unidadesDestino`, `total` | id, número, booleano; solo traspaso | Destino, nuevas participaciones y si sale todo (`app/almacen.py:382-439`). |
| `fechaEstimada`, `origen` | booleano/texto opcional de importación | Marca traspaso reconstruido y procedencia; importador MyInvestor (`app/importar.py:597-712`, `app/motor.py:718-741`). |

### Valoración, comparador, objetivo y auxiliares

| Objeto | Campos exactos y reglas |
|---|---|
| Valoración | `id` (`v` + entero), `fecha`, `producto`, `valor` ≥0 y `aportado` ≥0 opcional (se omite en efectivo/deuda). Una por producto/día (`app/almacen.py:483-508`). |
| Comparador | `id`, `nombre`, `real` booleano o `pesos` `{productoId:número}`. El inicial es `{id:"real", nombre, real:true}`; borrar producto poda pesos y comparadores vacíos (`app/almacen.py:25`, `app/almacen.py:295-298`, `app/motor.py:1535-1546`). |
| Objetivo | `activo` booleano, `importe` positivo o `null`, `etiqueta`; el editor añade `elegido:true`. El antiguo objetivo 100000 sin `elegido` se ignora como valor de fábrica (`app/almacen.py:142-151`, `app/motor.py:530-540`). |
| Colchón | `{modo:"meses", meses, gastoMensual}` (meses ≤60) o `{modo:"importe", importe}`; todos positivos (`app/almacen.py:156-176`). |
| `objetivoReparto` | `por`: `clase`/`producto`; `pesos` con suma 100±0,5; `vigilar` ≥0 (3 por defecto); `rebalancear` ≥`vigilar` (5 por defecto); `conEfectivo`; `cuenta` opcional `{productoId:boolean}` (`app/almacen.py:181-208`). |
| `hitos` | Números; el motor devuelve fecha del primer cruce o `null` (`app/motor.py:1346-1350`). |
| Regla periódica | `producto`, `importe`, `dia`, `desde`, `hasta`, `creada`; la planificación añade compras no duplicadas (`app/periodicas.py:68-121`). |

### Otros JSON

| Archivo | Forma / propietario |
|---|---|
| `perfiles.json` | `{activo, lista:[{id,nombre}]}`. Se sanea el id a `[a-z0-9_]{1,40}`, se inyecta `principal` si falta y `todos` solo vale con ≥2 carteras (`app/servidor.py:280-305`). Dato de usuario, global. |
| `estado.json` | `{preciosActualizados: ISO datetime, versionCache: 2}`; escrito tras descarga completa y usado para caducidad de seis horas (`app/servidor.py:357-385`). Estado local derivado. |
| `historico.json` | Lista de `{fecha,patrimonio,aportado,plusvalia,porProducto:{id:valor}}`, reemplazando el punto del día; no se genera para “Todos juntos” (`app/motor.py:1730-1741`, `app/servidor.py:366-368`). Derivado reproducible, aunque preserva instantáneas históricas. |
| `calculado_demo.json`, `calculado_propio.json`, `calculado_todos.json` | Serialización de `DATOS` más `modo` y `preciosActualizados`; se reescribe en cada recálculo (`app/servidor.py:353-378`). Derivado reproducible. |
| `cache/*.json` | Series `{fecha:precio}` con nombres saneados; `MS_*`, `CG_*`, tickers/FX; `EXPO_*.json` tiene `{consulta,datos}` (`app/motor.py:106-148`, `app/motor.py:151-182`, `app/motor.py:403-427`, `app/exposicion.py:221-244`). Caché local compartida entre perfiles. |
| `.acceso` | Token local secreto (`app/servidor.py:74-91`). Nunca debe sincronizarse. |
| `copias/*.json` | Carteras completas automáticas/manuales y carpetas de perfiles borrados (`app/almacen.py:44-62`, `app/servidor.py:1090-1103`). Datos del usuario; política de backup separada, no tabla operativa. |

### Ejemplo mínimo válido

Válido para restauración y para `construir()`; el motor devuelve `None` por estar vacío, lo cual es una salida prevista (`app/servidor.py:817-840`, `app/motor.py:1089-1091`):

```json
{
  "version": 1,
  "titular": "Mi patrimonio",
  "productos": [],
  "movimientos": [],
  "valoraciones": [],
  "comparador": [{"id": "real", "nombre": "Mi cartera real", "real": true}],
  "hitos": [],
  "objetivo": {"activo": false, "importe": null, "etiqueta": "Mi objetivo"}
}
```

## 3. Identificadores y compatibilidad con UUID

- Producto/perfil: `slug()` normaliza NFKD a ASCII, minúsculas y `_`, limita la base a 30 caracteres y resuelve colisiones con `_2`, `_3`… (`app/almacen.py:117-123`). Productos se crean con el slug de `corto`; perfiles con el de nombre y reservan `principal`/`todos` (`app/almacen.py:256-269`, `app/servidor.py:1039-1055`).
- Movimiento/valoración: `siguiente_id()` reconoce exclusivamente `prefijo + dígitos`, toma el máximo numérico y crea `mN`/`vN` (`app/almacen.py:126-128`, `app/almacen.py:376-379`, `app/almacen.py:505-508`). El orden de las listas no depende del número: movimientos se ordenan por fecha; valoraciones por producto/fecha (`app/almacen.py:378-379`, `app/almacen.py:506-507`).
- Backend usa igualdad opaca para editar/borrar y claves foráneas (`app/almacen.py:131-132`, `app/almacen.py:370-378`, `app/almacen.py:474-478`, `app/almacen.py:498-508`). La excepción es el generador que corta el primer carácter y parsea el resto (`app/almacen.py:126-128`).
- La validación de copias ya admite cualquier ID de 1..80 con letras, números, `_`, `.`, `-`; por tanto un UUID canónico pasa (`app/servidor.py:813-839`). Los perfiles no: están limitados a minúsculas/`_` y 40 caracteres (`app/servidor.py:284-295`).
- JavaScript trata todos los IDs como cadenas opacas: los inserta escapados en `data-id`, los usa para localizar con igualdad y los reenvía a la API; no corta ni convierte `mN`/`vN` (`app/web/editor.js:270-271`, `app/web/editor.js:460-482`, `app/web/editor.js:974-981`, `app/web/editor.js:1542`). `app.js` usa IDs de producto/comparador como claves, también opacas (`app/web/app.js:1172-1188`).
- “Todos juntos” sí cambia el formato: antepone `perfilId + "__"` a producto, movimiento, valoración, `producto`, `destino`; el código calculado y el JS reciben esos IDs compuestos (`app/servidor.py:312-334`). Un UUID de entidad funciona si el perfil sigue siendo slug; un UUID de perfil requeriría relajar su regex. Conviene no persistir IDs compuestos: son una proyección.

**Qué se rompería al migrar a UUID:** sustituir `mN`/`vN` sin cambiar `siguiente_id()` causaría que el siguiente alta volviera a `m1`/`v1`, con colisiones lógicas; hay que cambiar solo ese generador. Slugs de producto pueden convertirse a UUID porque backend y JS los tratan opacamente, pero deben migrarse atómicamente todas las referencias (`movimientos.producto/destino`, `valoraciones.producto`, `comparador.pesos`, `objetivoReparto.pesos/cuenta`, `periodicas.producto`, histórico/caché derivada). Los nombres de caché de cotización no dependen del ID salvo `_COTIZACIONES` en memoria (`app/motor.py:309-358`); los de exposición dependen del identificador de mercado (`app/exposicion.py:188-227`).

## 4. Motor financiero

### Semántica y ejemplos

| Tema | Semántica exacta | Ejemplo numérico inventado |
|---|---|---|
| FIFO global | `fifo_cartera()` expande todos los traspasos y `aplicar_todos()` ordena todos los productos por `(fecha, ORDEN_TIPO)`. `_saca_fifo` consume lotes antiguos y reparte coste proporcional; la venta realiza `cobrado − coste` (`app/motor.py:587-600`, `app/motor.py:614-664`, `app/motor.py:919-939`). | Compra 10 u por 100 y luego 10 por 200; vende 15 por 240: salen coste 100 + 100 = 200, quedan 5 u/coste 100, plusvalía realizada 40. |
| Traspaso | Se duplica internamente en salida/entrada con `_tid`; la salida extrae FIFO, no realiza ganancia, y el destino recibe el mismo coste y fecha de compra, repartidos según unidades/coste; para TIR individual genera venta/compra por el importe, que se cancela en total (`app/motor.py:573-584`, `app/motor.py:686-741`). | Lote 10 u, coste 100, comprado 2020; se traspasan 4 u por valor 60 a 6 u nuevas: destino recibe 6 u, coste 40 y antigüedad 2020; origen queda 6 u/coste 60. |
| Ajuste | Reescala las unidades de todos los lotes por igual sin cambiar coste/fecha; si no había lotes crea coste cero (`app/motor.py:674-685`). | Lotes 6+4 u, coste 60+80; ajuste a 12 u: pasan a 7,2+4,8 u, coste sigue 140. |
| Dividendos/intereses | Dividendo aumenta realizado y es flujo positivo de TIR. En un producto `efectivo`, los dividendos se presentan como intereses; sus comisiones son gastos (`app/motor.py:665-673`, `app/motor.py:810-842`, `app/motor.py:1289-1296`). | Dividendo 8: realizado +8 y flujo +8. Cuenta con 12 de intereses y 2 de gasto: realizado 10. |
| Comisiones | Comisión aislada resta realizado, suma gastos/comisiones y flujo negativo. Comisión de compra se registra en estadística pero la compra usa `importe` completo como coste; al mostrar precio unitario usa `(importe−comisión)/unidades` (`app/motor.py:646-650`, `app/motor.py:669-673`, `app/motor.py:1063-1073`). | Compra: importe 105, comisión 5, 10 u → lote/coste 105, precio mostrado 10; comisiones pagadas 5. Comisión aislada 3 → realizado −3. |
| Valor diario | Cotizados: participaciones acumuladas × último cierre disponible; manuales: interpolación lineal entre valoraciones y último valor plano después (`app/motor.py:1100-1144`, `app/motor.py:1145-1191`). | 3 u a cierre 12 → 36. Manual 100 el día 1 y 130 el día 4 → 110/120 en días 2/3. |
| Divisas | Yahoo usa `{MONEDA}EUR=X`; cada cierre se multiplica por el FX del mismo día o hasta cinco días anterior; GBp/GBX añade factor 0,01. Sin FX se descarta la serie y avisa (`app/motor.py:258-306`). | Acción 20 USD y 0,90 EUR/USD → 18 EUR; 500 GBp y 1,17 EUR/GBP → 5×1,17=5,85 EUR. |
| XIRR | Flujos reales: compras/comisiones negativos, ventas/dividendos positivos y valor final positivo; bisección anual ACT/365 entre −99,99 % y 1000 %, exige signos opuestos (`app/motor.py:363-398`, `app/motor.py:1210-1223`, `app/motor.py:1306-1311`). | −100 hoy y +110 exactamente un año después → XIRR ≈10 %. Total agrega todos los flujos y solo valores finales de productos con flujos. |
| TWR anual | Índice diario base 100 neutraliza aportaciones con `(valor−flujo)/valorAnterior`; el arranque compara primer valor con primer flujo y limita pérdidas diarias a −95 %. Rentabilidad anual encadena desde fin del año previo o primera compra (`app/motor.py:1447-1469`, `app/motor.py:1548-1588`). | Día 0: 100; día 1 se aportan 50 y cierra 165 → factor `(165−50)/100=1,15`, TWR +15 %, no +65 %. |
| Comparador propio | `real` usa TWR de la cartera; un comparador con `pesos` construye buy-and-hold base 100 con precios de productos, sin rebalanceo (`app/motor.py:1471-1494`, `app/motor.py:1535-1546`). | 60 % A sube 10 %, 40 % B baja 5 % → índice 100×(0,6×1,10+0,4×0,95)=104. |
| Indexados `REFERENCIAS` | MSCI World, S&P 500, 60/40 y monetario usan ETF Yahoo. Replican cada flujo externo del usuario el mismo día con pesos fijos y conservan unidades; si falta precio antiguo usan el primero y avisan (`app/motor.py:449-464`, `app/motor.py:1624-1662`). | Aporte 100 a referencia 60/40 con precios 10/20 → compra 6 u World y 2 u bonos; si pasan a 11/19 vale 104. |
| Métricas | Requiere ≥30 puntos: rentabilidad total, CAGR por 365,25 días, volatilidad muestral de retornos laborables anualizada `×√252`, drawdown desde máximo y Sharpe `(CAGR−rf)/vol`; `rf` es CAGR de un producto propio cuyo texto contiene “monetari”, o `null` (`app/motor.py:1496-1533`). | 100→121 en 2 años: CAGR 10 %. Si vol=20 % y monetario=2 %, Sharpe=0,40. Pico 120 y valle 90 → máxima caída −25 %. |
| Alquiler | Para inmueble, `dividendo` es alquiler y `comision` gasto; calcula total y últimos 365 días (`app/motor.py:513-527`, `app/motor.py:1238-1239`). | Alquiler 12×800 y gastos 600: cobrado 9.600, gastos 600, neto informativo 9.000. |
| Deuda | Solo admite saldos manuales; la última valoración se suma con signo negativo y su serie arrastra el último saldo, sin aportado/TIR (`app/almacen.py:20`, `app/motor.py:1005-1012`, `app/motor.py:1264-1296`). | Deuda anotada 20.000 reduce patrimonio en 20.000; nuevo saldo 18.000 reduce desde ese día en 18.000. |

### Entrada mínima y salida de `construir()`

Firma: `construir(cfg, carpeta, descargar=True, cache=None, historico=True)`. `cfg` puede contener solo las tres listas; `carpeta` debe ser escribible si `historico=True`; `cache` puede separar caché compartida. Con cartera sin productos utilizables retorna `None` (`app/motor.py:942-980`, `app/motor.py:1089-1091`). Para obtener `DATOS` no vacío hace falta al menos:

- producto `efectivo`/`deuda` + una valoración, o
- producto manual + valoración, o
- producto cotizado + movimiento de compra y una serie disponible; los demás campos tienen defaults (`app/motor.py:956-1012`, `app/motor.py:1024-1087`).

Claves raíz de `DATOS` usadas por la web: `generado`, `titular`, `moneda`, `fechaExtracto`, `fechas`, `productos`, `otrosActivos`, `pasivos`, `total`, `aportacionesMensuales`, `objetivo`, `objetivoReparto`, `colchon`, `comisiones`, `rentabilidadAnual`, `dividendos`, `monedas`, `resumenMensual`, `comparador`, `comparacion`, `indiceCartera`, `vivo`, `avisos`; servidor añade `modo` y `preciosActualizados` (`app/motor.py:1678-1724`, `app/servidor.py:369-377`). `total` contiene patrimonio/aportado/plusvalía/rentabilidad/TIR/realizado, series, agregados por clase/entidad/tipo, hitos, racha, ritmo, número de aportaciones, días invertido y máxima caída (`app/motor.py:1687-1708`). Cada producto calculado conserva metadatos y añade precios, series, participaciones, valor, aportado, rentabilidades/TIR, flujos, cobros, traspasos, ajustes y ventanas según corresponda (`app/motor.py:982-1087`, `app/motor.py:1100-1254`).

## 5. Fuentes de precios y exposición

| Fuente | Endpoint/identificador/límite | Caché y fallo |
|---|---|---|
| Yahoo | `query1.finance.yahoo.com/v8/finance/chart/{ticker}?period1=&period2=&interval=1d`; ticker `codigo`, FX `{moneda}EUR=X`. Pide todo el histórico (o años indicados), timeout 30 s (`app/motor.py:106-121`, `app/motor.py:285-306`). | `<ticker_saneado>.json`; fusiona el tramo nuevo. Si respuesta no diaria/red falla, conserva caché; sin caché queda vacío (`app/motor.py:123-148`). |
| Morningstar VL | `lt.morningstar.com/.../timeseries_price/...`; `codigo` secid, 30 años por defecto, EUR, diario (`app/motor.py:151-166`). Para códigos `0P...` combina Yahoo `.F` y decide discrepancias a favor de Morningstar (`app/motor.py:197-220`, `app/motor.py:881-886`). | `MS_<secid>.json`; conserva caché, mueve precios de fin de semana al viernes (`app/motor.py:169-194`). |
| CoinGecko | `/api/v3/coins/{coin}/market_chart?vs_currency=eur&days=365&interval=daily`; `codigo` coin-id. La gratuita se limita a un año (`app/motor.py:403-416`). | `CG_<coin>.json`; fusiona y usa lo guardado sin red (`app/motor.py:417-427`). |
| Respaldo | Ticker Yahoo `respaldo` + `respaldoMoneda`; sustituye principal si difieren >3 %, rellena huecos y descarta saltos aislados >15 % (`app/motor.py:312-358`). | Las dos series usan caché normal. |
| Exposición | Buscador/detalle clásico de Morningstar y API SAL para planes; clave por ISIN/secid/acción (`app/exposicion.py:23-31`, `app/exposicion.py:109-183`, `app/exposicion.py:188-218`). | `EXPO_<clave>.json`, caduca a 30 días; sin red devuelve el último `datos`. Paraleliza 6 en motor/4 en servidor (`app/exposicion.py:221-244`, `app/motor.py:1193-1204`, `app/servidor.py:471-508`). |

Refresco: al arrancar descarga si falta calculado, cambia `VERSION_CACHE` o han pasado seis horas; guardar usa modo “faltan”, y actualizar fuerza red (`app/servidor.py:37-40`, `app/servidor.py:381-385`, `app/servidor.py:1187-1197`). `descargar=False` impide red; `"faltan"` descarga solo series sin caché (`app/motor.py:942-953`, `app/motor.py:106-112`).

## 6. Perfiles, periódicas, fiscal, importar y exportar

- **Perfiles:** principal en raíz, secundarios en `perfiles/<slug>`, caché común; “Todos juntos” fusiona carteras con IDs prefijados y es solo lectura (`app/servidor.py:275-335`, `app/servidor.py:441-448`). Crear escribe cartera y `perfiles.json`; borrar mueve la carpeta a copias, no la elimina (`app/servidor.py:1039-1108`).
- **Periódicas:** valida producto, importe, día 1..31 y rango pasado; calcula fechas mensuales, evita compras a menos de cinco días, usa cierre de mercado o importe/unidades manuales, inserta compras y recuerda la regla en la propia cartera (`app/periodicas.py:21-24`, `app/periodicas.py:39-121`). “Olvidar” solo borra el recordatorio (`app/periodicas.py:124-136`).
- **Fiscal:** usa los lotes FIFO del motor para ventas, rescates de pensión, dividendos/intereses y alquileres; avisa por recompra (2 meses para ETF/acción, 12 para fondo), coste ausente y traspasos no fiscales; produce JSON y CSV en memoria, sin escribir (`app/fiscal.py:29-30`, `app/fiscal.py:71-183`, `app/fiscal.py:187-229`).
- **Importar:** acepta CSV/XLSX genérico o extractos MyInvestor. Construye un `Plan` en memoria con productos, movimientos, valoraciones, sustituciones y preguntas de traspaso; vista previa aplica sobre `deepcopy`, confirmación muta la cartera mediante `cambia()` (`app/importar.py:285-387`, `app/importar.py:387-547`, `app/importar.py:715-854`, `app/servidor.py:699-777`). El plan pendiente no sobrevive a reinicio y se limita a uno (`app/servidor.py:699-730`).
- **Exportar:** plantilla vacía CSV/XLSX; `mis_datos()` genera Excel reimportable desde cartera; exportación web incrusta `DATOS` y puede anonimizar importes, todo en memoria (`app/plantilla.py:39-111`, `app/plantilla.py:114-162`, `app/exportar.py:35-83`, `app/exportar.py:86-131`). Copia JSON descarga `cartera.json` crudo (`app/servidor.py:873-881`).

## 7. Inventario exhaustivo de E/S y adaptación remota

| Punto de E/S | Clasificación | Adaptación |
|---|---|---|
| `almacen.carga/guarda(cartera.json)`; copia previa, temporal y `os.replace`; poda 20 copias (`app/almacen.py:39-62`) | **Usuario** + backups | Repositorio transaccional remoto para cartera normalizada/versionada; copias como snapshots/backup, no caché. |
| `lee_json/escribe_json` genéricos, escritura temporal (`app/servidor.py:258-272`) | Mixta | Sustituir llamadas de usuario por repositorios; conservar helper solo para local derivado/estático. |
| Lectura de cartera activa/demo/todos (`app/servidor.py:308-350`) | **Usuario**; demo estático | Consultar perfil remoto; construir “todos” como proyección, demo permanece local. |
| `perfiles.json` y carpetas (`app/servidor.py:284-305`, `app/servidor.py:1039-1108`) | **Usuario** | Tabla de perfiles/miembros y perfil activo local por dispositivo. Borrar debe ser soft-delete/archivo; no mover carpetas. |
| `estado.json` (`app/servidor.py:357-385`) | Derivado local | Mantener local por instalación; no sincronizar salvo telemetría explícita. |
| `calculado_*.json` (`app/servidor.py:353-378`, `app/servidor.py:395-405`) | Derivado local | Caché materializada local con clave perfil/revisión; invalidar al cambiar remoto. |
| `historico.json` (`app/motor.py:1730-1741`) | Derivado, pero conserva foto diaria | Preferible recalcular desde precios/operaciones; si se necesita reproducibilidad, tabla de snapshots derivada con `profile_id/date` y upsert. |
| Caché Yahoo/MS/CG (`app/motor.py:106-182`, `app/motor.py:270-282`, `app/motor.py:403-427`) | Caché local compartida | Mantener local o servicio común de cotizaciones; no mezclar con datos de usuario. |
| Caché de exposición (`app/exposicion.py:221-244`) | Caché local compartida | Igual: caché por identificador, TTL 30 días, fuera de RLS de cartera. |
| `.acceso` (`app/servidor.py:74-91`) | Secreto local | Mantener local; no subir a Supabase. Autenticación remota separada. |
| Copias: listar/leer/descargar/subir/recuperar (`app/servidor.py:809-921`) | **Usuario** | Export/import explícito; si se aloja, bucket privado cifrado y versionado. |
| Reinicio mueve cartera y borra calculado/histórico (`app/servidor.py:1004-1024`) | **Usuario** + derivados | Transacción remota/soft reset; invalidación local posterior. |
| Borrado de perfil mueve carpeta completa (`app/servidor.py:1090-1108`) | **Usuario** | Soft-delete remoto atómico de perfil y descendientes. |
| Importación lee uploads en memoria y escribe por `cambia()` (`app/servidor.py:702-777`) | **Usuario** | Plan local efímero; confirmación como transacción/idempotente remota. |
| Restauración elimina `calculado_propio`/`historico` (`app/servidor.py:843-850`) | Derivado | Invalidar cachés, no borrar historia fuente. |
| Lecturas directas de `index.html`, `prompt_ia.txt`, icono/scripts, `VERSION`, `novedades.json` (`app/servidor.py:197-209`, `app/servidor.py:801-804`, `app/servidor.py:942-985`, `app/exportar.py:86-120`) | Estático local | Sin cambio; nunca nube de usuario. |
| Consulta remota de versión/novedades (`app/servidor.py:947-1001`) | Caché en memoria | Sin cambio. |
| Exportaciones Excel/web (`app/plantilla.py:114-162`, `app/exportar.py:86-131`) | Lectura de usuario, salida efímera | Alimentar desde repositorio/snapshot consistente; no persistir servidor. |

Límite recomendado: la nube contiene hechos del usuario (perfiles, productos, movimientos, valoraciones, objetivos/reglas) y quizá snapshots versionados; precio/exposición, calculados, estado de refresco, llave y planes pendientes siguen siendo locales/derivados.

## 8. Concurrencia y estado global

`cerrojo` es un `threading.RLock` de proceso y serializa cálculo/escritura/importación, permitiendo que `cambia()` llame reentrante a `recalcula()` (`app/servidor.py:42-44`, `app/servidor.py:363-378`, `app/servidor.py:451-465`). No protege entre procesos ni dispositivos: con Supabase hacen falta transacciones, control optimista (revisión/`updated_at`) e idempotencia.

Estado global mutable:

- `motor.CACHE`, `SIN_RED`, `SOLO_FALTAN`, `AVISOS`, `_COTIZACIONES` se reconfiguran/limpian por cálculo; dos cálculos concurrentes mezclarían perfil, red, avisos y series (`app/motor.py:31-53`, `app/motor.py:309-358`, `app/motor.py:942-954`). `cerrojo` evita esto en rutas normales, pero `completa_exposiciones` usa hilo y llama después a `recalcula`; su lock `_COMPLETANDO` evita dos trabajos de exposición (`app/servidor.py:468-508`).
- `PLANES` mantiene una única importación pendiente en memoria; cualquier previsualización borra la anterior (`app/servidor.py:699-730`).
- `_LLAVES`, `_VERSION`, `_NOVEDADES` y `ERRORES` son cachés de proceso (`app/servidor.py:63-91`, `app/servidor.py:938-1001`, `app/servidor.py:1111-1125`).
- Flask sirve multihilo (`app/servidor.py:1201-1204`); una futura capa remota no debe confiar en esas variables como autoridad compartida.

## 9. Riesgos y observaciones

1. No existe directorio de tests en el repositorio analizado; la lógica financiera, importación, seguridad y persistencia carecen de regresión automatizada observable (inventario de archivos del repositorio; las funciones críticas están concentradas en `app/motor.py:573-745` y `app/importar.py:715-854`).
2. Importes/cantidades usan `float` y redondeos a 2/4 decimales, con tolerancias ad hoc; una integración con Android/Supabase debe fijar Decimal/unidades menores sin cambiar resultados históricos silenciosamente (`app/almacen.py:83-101`, `app/motor.py:96-101`, `app/motor.py:587-600`).
3. `cartera.json` no tiene versión de esquema efectiva ni migrador; campos opcionales/legados se interpretan dinámicamente (`app/almacen.py:22-28`, `app/motor.py:956-960`).
4. La API SAL lleva una `apikey` fija de Morningstar en código y depende de endpoints no contractuales (`app/exposicion.py:23-30`). Debe retirarse del cliente/repositorio público o tratarse como credencial pública revocable con proxy/límites.
5. El backup valida estructura básica pero acepta números JSON no finitos si el parser los admite y no valida todos los enums/campos del producto (`app/servidor.py:817-840`); la base remota necesita constraints.
6. El secreto `.acceso` queda en texto plano y la cookie no es `Secure`; adecuado solo al modelo HTTP local/red privada advertido, no a exposición pública (`app/servidor.py:74-95`, `app/servidor.py:241-245`, `docker-compose.yml:7-8`).
7. La caché/estado global del motor hace el cálculo no reentrante; escalar a varios usuarios exige encapsular contexto por ejecución (`app/motor.py:942-954`).
8. `demo/cartera.json` contiene una cartera de ejemplo con nombres e importes concretos. Se constató su existencia y forma, pero este informe no reproduce ningún nombre ni importe personal, conforme a la ficha.
