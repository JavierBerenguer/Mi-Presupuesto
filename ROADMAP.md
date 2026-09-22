# Roadmap

Leyenda: ✅ completado y verificado · 🔄 en curso · ⏳ pendiente

| Fase | Contenido | Estado |
|---|---|---|
| 0 | Análisis del entorno | ✅ |
| 1 | JDK 17 + Android SDK | ✅ |
| 2 | Proyecto base, tema, navegación, Room | ✅ |
| 3 | MVP financiero: dominio, Room, cuentas, movimientos, transferencias, categorías, presupuestos, dashboard, patrimonio | 🔄 dominio y datos ✅ (49 tests); pantallas en curso (Codex) |
| 4 | Inversiones básicas (carteras, activos, compras/ventas, precios manuales) | 🔄 dominio ✅; pantalla en curso |
| 5 | Primer APK verificado | ⏳ |
| 6 | Notificaciones bancarias (`NotificationListenerService`, reglas por banco, propuestas pendientes) | ⏳ |
| 7 | Recurrentes, dividendos (UI), multidivisa real, objetivos, estadísticas avanzadas, alertas | ⏳ |
| 8 | Importación CSV/XLSX, exportación, copias de seguridad cifradas y restauración | ⏳ |
| 9 | Cotizaciones, tipos de cambio, sincronización con Supabase (RLS) | ⏳ |
| 10 | Calidad: revisión de seguridad, optimización, APK firmado, documentación | ⏳ |

## Funcionalidades bloqueadas o dependientes de decisiones
- APK de distribución firmado: requiere que el usuario cree/aporte el keystore.
- Cotizaciones y Supabase: requieren elegir proveedor/credenciales; el sistema sigue funcionando con precios manuales.
- Importación de Trade Republic: el CSV de ejemplo mencionado en `Claude.md` no está en el repositorio. Hay tres archivos «Exportación de transacción» en `Descargas\Telegram Desktop` que probablemente lo sean; se inspeccionarán en la Fase 8 (contienen datos personales: no se versionarán) para diseñar el adaptador.
