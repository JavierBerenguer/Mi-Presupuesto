# Roadmap

Leyenda: ✅ completado y verificado · 🔄 en curso · ⏳ pendiente

Última revisión: 2026-09-28 (rama `claude/fase-3-mvp`, Room v10, 413 tests).

| Fase | Contenido | Estado |
|---|---|---|
| 0 | Análisis del entorno | ✅ |
| 1 | JDK 17 + Android SDK | ✅ |
| 2 | Proyecto base, tema, navegación, Room | ✅ |
| 3 | MVP financiero: cuentas, movimientos, transferencias, categorías independientes del tipo, presupuestos (con subcategorías y umbrales), inicio, patrimonio | ✅ |
| 4 | Inversiones básicas: carteras, activos (incl. préstamos P2P), compra/venta por importe, fecha y hora, precios manuales, rentabilidad detallada | ✅ |
| 5 | Primer APK verificado (APK de depuración en `.local/apk/`) | ✅ |
| 6 | Notificaciones bancarias: `NotificationListenerService`, apps autorizadas, reglas, autoanotación y propuestas pendientes | ✅ |
| 7 | Órdenes permanentes con recordatorios ✅ · estado ejecutado/previsto por fecha ✅ · saldo por cuentas y modos de cálculo en Movimientos ✅ · dividendos ✅ · inversiones con cuenta visibles en Movimientos ✅ · notificaciones solo como gasto/ingreso ✅ · gestión de carteras ✅ · categorías de Mi Presupuesto por defecto ✅ · multidivisa real (tipos de cambio) ⏳ · objetivos ⏳ · alertas de presupuesto ⏳ | 🔄 |
| 8 | Copias de seguridad cifradas y restauración ✅ · exportación CSV ✅ · importación de Trade Republic con vista previa y reglas del usuario (CASH/TRADING, carteras TR, Ignorar/Aceptar todo) ✅ · importación genérica configurable/XLSX ⏳ | 🔄 |
| 9 | Cotizaciones automáticas (Twelve Data, CoinGecko) ✅ · alta de activos por ISIN (OpenFIGI) ✅ · tipos de cambio ⏳ · sincronización con Supabase (RLS) ⏳ | 🔄 |
| 10 | Calidad: revisión de seguridad, bloqueo biométrico opcional, optimización, APK firmado, documentación | ⏳ |

## Pendiente de verificación manual
- Prueba real de cotizaciones y alta por ISIN con las claves del usuario (Twelve Data obligatoria; OpenFIGI y CoinGecko opcionales).
- Recordatorios de órdenes permanentes en dispositivo con el permiso de notificaciones concedido y denegado.

## Funcionalidades bloqueadas o dependientes de decisiones
- APK de distribución firmado: requiere que el usuario cree o aporte el keystore.
- Supabase: requiere un proyecto y credenciales del usuario; la app funciona completa sin él.
- Clasificación de cartera por sector/país/industria: aplazada por decisión del usuario (no hay fuente gratuita fiable de composición de ETF/fondos).
