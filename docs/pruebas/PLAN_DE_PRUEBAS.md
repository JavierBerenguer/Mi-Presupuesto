# Plan de pruebas manuales — Mi Patrimonio (móvil) + Rumbo (escritorio)

Este documento lo mantiene Claude. Cada bloque indica en qué versión se prueba. Marca `[x]` lo que funcione. Si algo falla, escribe debajo **qué hiciste, qué esperabas y qué pasó** (con una captura si puedes).

**Versiones actuales:**
- **App:** v1.3.0 (https://github.com/JavierBerenguer/Mi-Presupuesto/releases/tag/v1.3.0). Se actualiza encima de la v1.2.0 sin perder datos.
- **Rumbo:** rama `claude/supabase` de `JavierBerenguer/Rumbo-Patrimonio`, en la carpeta `D:\XX_Claude\App - Rea - Rumbo\rumbo-main`.

---

## 0. Preparación (una sola vez)
- [X] **Copia de seguridad** de la app actual: Más › Importar/Exportar › Crear copia. Guárdala fuera del móvil.
- [X] Si tienes la versión **de depuración** instalada: desinstálala, instala la v1.2.0 (o añade el repositorio en **ObtainX**) y **restaura la copia**.
  - Comprueba que siguen tus cuentas, apuntes, categorías, presupuestos y automatizaciones.
  - Las carteras y activos antiguos ya no existen, y es lo esperado.
- [X] **Rumbo:** ejecuta `Iniciar.bat` en `rumbo-main`. Se abre en el navegador.

## A. Rumbo en modo nube (escritorio) — ✅ disponible ya
- [ ] A1. Pulsa **☁ Nube**, pega la URL y la clave *publishable* (las tienes en `rumbo-main\supabase.env`) e inicia sesión con tu email y contraseña. Debe quedar conectado.
- [ ] A2. Crea un producto cotizado, por ejemplo un ETF buscado por ISIN, y registra una **compra** con unidades. El panel lo muestra con su valor.
- [ ] A3. Cierra Rumbo y vuelve a abrirlo: los datos siguen ahí, porque se leen de la nube.
- [ ] A4. *(opcional)* Sin internet, Rumbo abre en **solo lectura** con un aviso. Al intentar guardar, aparece el mensaje «Sin conexión con la nube».
- [ ] A5. *(opcional)* Si usabas Rumbo en local antes, prueba «Subir mi cartera local».

## B. Sincronización en el móvil — ✅ disponible ya (v1.2.0)
- [ ] B1. En **Ajustes › Sincronización con Rumbo** pon la URL, la clave publishable, tu email y tu contraseña, y pulsa **Iniciar sesión**. Se debe ver «Conectado como…».
- [ ] B2. Elige el **perfil** (Principal) y pulsa **Sincronizar ahora**. Debe aparecer la fecha de la última sincronización.
- [ ] B3. En Rumbo, pulsa Actualizar: tus **cuentas de la app** aparecen como efectivo con su saldo actual.
- [ ] B4. Crea un apunte en el móvil. En 1 minuto o al pulsar Sincronizar ahora, el saldo de esa cuenta se actualiza en Rumbo tras recargar.

## C. Cartera y liquidez — ✅ disponible ya (v1.2.0)
- [ ] C1. Pestaña **Cartera** del móvil: muestra el valor, la ganancia, la TIR, el reparto y el producto creado en A2, con «Actualizado: <fecha>».
  - El valor **no** incluye tus cuentas: en Rumbo el patrimonio sí las suma; en la Cartera del móvil no.
- [ ] C2. Al tocar el producto se ve su detalle y sus movimientos. Los de escritorio dicen «Se edita en Rumbo».
- [ ] C3. **Venta con liquidez:** en Rumbo, registra una venta (o un dividendo) eligiendo **Cuenta de liquidez (app)** = una cuenta tuya en EUR. Sincroniza el móvil.
  - Aparece un apunte «Venta · <producto>» en esa cuenta.
  - El saldo de la cuenta sube.
  - **No** cuenta como ingreso en Inicio ni en Presupuestos.
- [ ] C4. **Compra desde el móvil:** Cartera › botón **+**. Elige producto, importe, sin unidades y la cuenta de liquidez.
  - Aparece en el detalle y como apunte de la cuenta, que baja.
  - En Rumbo, tras pulsar Actualizar, aparece con **unidades estimadas** (marcadas así).
- [ ] C5. Edita y borra esa compra desde el móvil. El saldo vuelve a su valor y en Rumbo desaparece tras actualizar.
- [ ] C6. **Inicio › Patrimonio** suma cuentas + «Inversiones (Rumbo)».

## D. Compras desde notificaciones — ✅ disponible (v1.3.0)
*Requisito:* sesión iniciada en Sincronización y al menos un producto creado en Rumbo.
- [ ] D1. Cuando llegue una notificación de compra (por ejemplo de Trade Republic o de un plan de ahorro), ve a Automatización › Pendientes › **Enseñar**.
  - Marca el texto clave y el variable.
  - En **Destino** elige **Compra de inversión** y selecciona el **producto**.
  - La vista previa dice «Se registrará una compra de X € en <producto>».
- [ ] D2. Al guardar se crea la compra:
  - aparece en el detalle del producto, en la Cartera;
  - si la app de la notificación tiene una cuenta EUR asociada, aparece como apunte de liquidez en esa cuenta.
- [ ] D3. La siguiente notificación igual crea la compra sola, sin preguntar.
- [ ] D4. En Rumbo, tras Actualizar, aparece con **unidades estimadas**.
- [ ] D5. *(caso límite)* Con la sesión cerrada en Sincronización, la notificación queda pendiente. Al volver a iniciar sesión y sincronizar, se crea la compra.

## E. Lo de la v1.1.0 que aún no has probado en el móvil
- [ ] E1. Una notificación de un **comercio nuevo** de una estructura conocida crea el apunte solo. Aparece en Ajustes › Automatización › **Comercios nuevos**.
- [ ] E2. Llega el **aviso push** «Automatización pendiente». Al tocarlo se abre Automatización en la pestaña correcta.
- [ ] E3. **Agrupar:** mantén pulsada una cena y selecciona los Bizums › Agrupar › elige la cena.
  - Se ve una fila con el neto y «+N».
  - Presupuestos e Inicio cuentan el neto.

---

## F. Primera carga de inversiones (Trade Republic + Neverless) — ✅ disponible
*Archivos generados:* `D:\XX_Claude\App - Rea - Rumbo\Primera Carga\primera_carga.xlsx` y `informe_primera_carga.txt` (léelo antes).
*Requisitos:* bloques A y B hechos. El móvil tiene que haber sincronizado, para que Rumbo vea la cuenta «Trade Republic».
- [ ] F1. En Rumbo (modo nube, con sesión iniciada): **Importar** › elige `primera_carga.xlsx` › **Previsualizar**.
  - Buscará cada ISIN en internet: tarda un poco.
  - Revisa que no haya errores. Si alguno sale, cópiamelo.
- [ ] F2. **Confirmar.** Aparecen unos 18 productos, cada uno con su número de unidades. Compáralo con lo que ves en Trade Republic y en Neverless; la lista de posiciones esperadas está en el informe.
- [ ] F3. **Fondos privados** («Private Equity (LU…)»): no tienen precio público. Añade su **valor actual** en «Saldos y valores», con el dato que te dé TR.
- [ ] F4. Sincroniza el móvil:
  - la cuenta «Trade Republic» muestra los apuntes de compras y ventas;
  - su saldo debe parecerse al saldo real de efectivo de TR (el efecto en caja total está en el informe).
  - Si no cuadra, dime la diferencia.
- [ ] F5. La Cartera del móvil muestra todos los productos y el total que calcula Rumbo.

## Resultados y problemas encontrados
*(Escribe aquí lo que falle; Claude lo convierte en fichas de corrección.)*

- **2026-10-28 · A1 (Rumbo › Nube › «Guardar y continuar»):** daba una pantalla negra con «Petición bloqueada: no viene de Rumbo». **Corregido** (`Referrer-Policy: same-origin`, commit en `claude/supabase`). Cierra Rumbo y vuelve a abrirlo con `Iniciar.bat` para que se aplique.
