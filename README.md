# Mi Patrimonio

Aplicación Android de finanzas personales que combina **gestión de presupuestos** y **seguimiento de inversiones** en una sola app. Funciona sin conexión, guarda los datos solo en el dispositivo, está en español y usa modo oscuro por defecto.

> Estado: versión inicial (MVP) en desarrollo. Consulta [ROADMAP.md](ROADMAP.md) para lo implementado y lo pendiente, y [ARCHITECTURE.md](ARCHITECTURE.md) para las decisiones técnicas.

## Funcionalidades (MVP)
- Cuentas (corriente, ahorro, efectivo, inversión, cripto, otras) con saldo **derivado** de los movimientos.
- Ingresos, gastos y transferencias entre cuentas (una transferencia no es ingreso ni gasto).
- Categorías y subcategorías personalizables (se archivan, nunca se borran).
- Presupuestos mensuales y anuales, globales o por categoría, con avisos al 80 % y al superar el límite.
- Inversiones: carteras, activos, compras, ventas, dividendos y comisiones; posiciones por coste medio ponderado y precios manuales.
- Patrimonio neto (activos − pasivos) con distribución por tipo, divisa, cuenta y cartera.
- Dashboard con gráficos interactivos y periodos (mes, 3 meses, 6 meses, año, todo).

## Requisitos para compilar
- Windows/macOS/Linux con **JDK 17**.
- **Android SDK**: plataforma `android-37.0` (compileSdk), `build-tools;35.0.1`, `platform-tools`.
- Variables de entorno `JAVA_HOME` y `ANDROID_HOME` (o `sdk.dir` en `local.properties`, que no se versiona).
- No hace falta instalar Gradle: se usa el wrapper (Gradle 9.7.1).

## Compilación y pruebas
```
./gradlew testDebugUnitTest     # pruebas unitarias (dominio, repositorios con Room en memoria vía Robolectric, UI pura)
./gradlew assembleDebug         # APK de depuración
```
El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`. **No es un APK de distribución.**

## APK de distribución firmado
La configuración de firma **no está incluida** ni se versiona ninguna clave. Para publicar hay que crear un keystore propio (`keytool -genkeypair …`), guardarlo fuera del repositorio y declarar `keystore.properties` (ignorado por git) con la ruta y contraseñas. No se ha generado ningún APK firmado.

## Cotizaciones y Supabase
Aún **no** hay proveedor de cotizaciones ni sincronización con Supabase (Fase 9). Los precios de los activos se introducen manualmente. Cuando se implementen, no se incluirán claves privadas en el APK ni `service_role`; las claves públicas se usarán solo con Row Level Security. Los pasos de configuración se documentarán aquí.

## Limitaciones conocidas
- Sin tipos de cambio: los importes en divisas distintas de la principal **no se convierten**; se excluyen de los totales y se avisa de ello.
- No se registran pasivos (deudas): el patrimonio se presenta siempre como **parcial**.
- El histórico de patrimonio valora las inversiones a coste (no hay histórico de cotizaciones).
- Sin importación/exportación, copias de seguridad, notificaciones bancarias, movimientos recurrentes, objetivos ni alertas (ver roadmap).
- Verificado por compilación y tests JVM; **no se ha probado aún en un dispositivo o emulador**.

## Desarrollo con dos agentes
El código se reparte entre Claude Code (arquitectura, dominio, revisión) y Codex CLI (implementación por fichas en `docs/tasks/`). Ver `Claude.md` (sección 34) y `AGENTS.md`.
