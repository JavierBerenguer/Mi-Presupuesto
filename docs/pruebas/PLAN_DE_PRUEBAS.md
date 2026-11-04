# Plan de pruebas manuales — Mi Patrimonio (móvil) + Rumbo (escritorio)

Este documento lo mantiene Claude y solo recoge lo que **queda por probar**: lo ya probado se quita. Marca `[x]` lo que funcione. Si algo falla, escribe debajo **qué hiciste, qué esperabas y qué pasó** (con una captura si puedes).

**Versiones actuales:**
- **App:** v1.12.3 (https://github.com/JavierBerenguer/Mi-Presupuesto/releases/tag/v1.12.3).
- **Rumbo:** rama `main` de `JavierBerenguer/Rumbo-Patrimonio`, en `D:\XX_Claude\App - Rea - Rumbo\rumbo-main`. Tras actualizarlo, reinícialo con `Iniciar.bat`.

*El 02/11 Claude y Codex pasaron lo que no escribe en la nube (informes T-113 del escritorio y T-114 del móvil) y se quitó lo comprobado. Lo que queda escribe en la nube, necesita notificaciones reales de TR o tus datos reales en el móvil.*

---

## A. Rumbo en modo nube (escritorio)
- [ ] A4. *(opcional)* **Sin internet**, Rumbo abre en **solo lectura** con un aviso, y al guardar dice «Sin conexión con la nube». Si Supabase *rechaza* un cambio, el mensaje dice el motivo: «La nube ha rechazado los cambios (…)».

## C. Cartera y liquidez
- [ ] C4. **Compra desde el móvil:** Cartera › **+**. Elige producto, importe, sin unidades y la cuenta de liquidez.
  - Aparece en el detalle y como apunte de la cuenta, que baja.
  - En Rumbo aparece con **unidades estimadas**.
- [ ] C5. Edita y borra esa compra desde el móvil. El saldo vuelve a su valor y en Rumbo desaparece.

## D. Compras desde notificaciones (v1.3.0)
*Requisito:* sesión iniciada en Sincronización y al menos un producto creado en Rumbo.
- [ ] D1. Cuando llegue una notificación de compra (por ejemplo, de un plan de ahorro de TR), ve a Automatización › Pendientes › **Enseñar**. En **Destino** elige **Compra de inversión** y el **producto**. La vista previa dice «Se registrará una compra de X € en <producto>».
- [ ] D2. Al guardar se crea la compra en la Cartera y, si la app tiene una cuenta EUR asociada, también el apunte de liquidez.
- [ ] D3. La siguiente notificación igual crea la compra sola.
- [ ] D4. En Rumbo aparece con **unidades estimadas**.
- [ ] D5. *(caso límite)* Con la sesión cerrada, la notificación queda pendiente. Al volver a entrar y sincronizar, se crea la compra.

## E. Notificaciones y redondeo
- [ ] E4. *(v1.9.0)* Con un pago en un **comercio nuevo**, el aviso dice «Nuevo comercio: <comercio>» y «Regla general: <estructura>».
  - El botón **«Seguir usando la regla general»** guarda la regla sin abrir la app: el aviso desaparece y el comercio ya no sale en Automatización › Comercios nuevos.
  - Si tocas el aviso en lugar del botón, se abre Comercios nuevos como antes.
- [ ] E5. *(v1.9.1)* **Cambiar una regla actualiza los apuntes de hoy:** con dos pagos de hoy en el mismo comercio, creados con la regla general, crea o edita su regla y cambia el título o la categoría.
  - Los dos apuntes de hoy cambian.
  - Los de días anteriores y los de otros comercios no cambian.
  - Si antes retocaste a mano un campo de alguno de esos apuntes, ese campo se queda como lo dejaste.
- [ ] E6. *(v1.10.0)* **Redondeo de Trade Republic:**
  - **Configuración:** en la cuenta TR, «Redondeo de la tarjeta» = x2. En la estructura de tus pagos con tarjeta de TR, marca «Genera redondeo».
  - **Tras un pago con tarjeta de 17,80 €:**
    - Cuentas muestra «Redondeo pendiente: −0,40 €» y el saldo baja 0,40 €;
    - también lo reflejan el «Saldo actual» de Movimientos y el Efectivo de Inicio;
    - las estadísticas y los presupuestos no cambian;
    - en el escritorio, el saldo de TR no baja.
  - **Cuando llegue la notificación de TR de la inversión del redondeo:** enséñala con destino «Compra de inversión» y marca «Es la inversión del redondeo».
    - Se registra la compra.
    - El pendiente vuelve a 0 (solo quedan los pagos posteriores).
    - La cuenta muestra «Último redondeo: estimado X €, real Y €».

## G. Inicio
- [ ] G3. *(v1.12.3)* **Operativa del mes:** en un mes con más gastos que ingresos, el **Ahorro** sale en negativo (con «−»). Con ingresos y gastos normales, ya se comprobó en el emulador que todo coincide con Movimientos.

## H. Panel completo de Rumbo en Cartera
- [ ] H5. Un enlace externo del panel se abre en el navegador, no dentro de la app.

## L. Movimientos de inversión integrados (v1.11.0, fichas T-097 a T-104)
**Categorías (T-097)**
- [ ] L2. **Movimientos › Filtros › Categoría › Inversiones:** salen los movimientos de inversión (compras, dividendos…) junto a los apuntes de Inversiones. Con «Dividendo», solo los dividendos.
- [ ] L3. Al buscar «Dividendo» en Movimientos salen los dividendos. Su icono y su color son los de la categoría.

**Totales e Inicio (T-098)**
- [ ] L6. Un dividendo con cuenta suma en ingresos y una comisión con cuenta, en gastos. Una compra no cambia ni ingresos ni gastos.
- [ ] L7. La tarjeta del mes en **Inicio** muestra «Invertido este mes» (compras − ventas), o «Desinvertido» si vendiste más. El saldo de la cuenta es el mismo de antes.
- [ ] L8. Un presupuesto de Inversiones › Comisión cuenta las comisiones de inversión.

**Crear y editar desde el móvil (T-099)**
- [ ] L9. **Movimientos › Nuevo apunte:** hay una cuarta pestaña, **«Inversión»**, y debajo Compra · Venta · Dividendo · Comisión. Sin cuenta de liquidez no deja guardar.
- [ ] L10. **Equito:** registra en el móvil un dividendo de «Equito (Real Estate)» en «Equito Cash». Comprueba que:
  - sube el saldo de Equito Cash;
  - sale en Movimientos;
  - en el escritorio aparece en **Mis datos › Movimientos** al recargar la página (más de 30 s después).
- [ ] L11. **Venta:** «Todas» rellena las participaciones que tenías ese día. Si pones más, no deja guardar y explica cuántas tenías.
- [ ] L12. Edita en el móvil un movimiento creado en el escritorio (por ejemplo, la nota): el cambio llega al escritorio y el movimiento sigue siendo «del escritorio».
- [ ] L13. Borra un movimiento de inversión desde la lista de Movimientos: pide confirmación y desaparece también del escritorio.

**Órdenes permanentes (T-100)**
- [ ] L14. Crea una orden de **dividendo** mensual (por ejemplo, el alquiler de Equito) con fecha de inicio pasada. Se crean las ocurrencias pasadas, **ninguna futura**, y llegan al escritorio.
- [ ] L15. Edita el importe de la orden: los movimientos ya creados no cambian. Borra la orden: tampoco desaparecen.

**Retenciones (T-101 y T-102)**
- [ ] L18. **Móvil:** ese movimiento muestra «bruto 150 € · retención 30 €» y suma 120 al saldo. Quita la retención desde el móvil: también se quita en el escritorio.

**Escritorio siempre al día (T-104)**
- [ ] L22. Cambia algo desde el móvil, espera más de 30 s y pulsa **F5** en el escritorio: aparece sin cerrar la app.

## O. Importar Neverless y cuenta de liquidez sin «Uso en app» (T-111 escritorio y T-112 app, 02/11)
*Hecho por Claude el 02/11:* se borraron con copia los movimientos de Neverless de la primera carga y se importó `2026-11-02 Neverless.csv`: 141 movimientos con la cuenta «Neverless cash» y 7 saldos de fin de mes (último: 0,09 € el 31/10). *Requisito:* cerrar y volver a abrir el escritorio para que cargue la versión nueva.
- [ ] O5. **Mis datos › Importar › Neverless:** vuelve a importar el mismo CSV. Sale «0 nuevos · 141 ya estaban» y no anota saldos.
- [ ] O6. Edita una compra importada (cambia la nota), borra un interés y vuelve a importar: la compra sigue como la dejaste y el interés vuelve.
- [ ] O7. **Editor de movimientos del escritorio:** «Neverless cash» aparece en «Cuenta de liquidez».
- [ ] O8. **App, con la versión que incluya T-112:** edita una compra de Neverless sin tocar la cuenta: se guarda y conserva «Neverless cash».
- [ ] O9. **App:** una compra nueva con la cuenta Neverless cash, con fecha posterior al 31/10, baja su saldo en la app y en el escritorio.

## P. Escritorio: guardar en «Mis datos» ya no recarga la página (02/11)
*Requisito:* pulsa F5 en el escritorio para cargar la versión nueva (no hace falta reiniciar).
- [ ] P2. **Mis datos › Cuentas** (incluido anotar un saldo): guardar no recarga la página. Productos y Saldos y valores ya se comprobaron.

## Q. Notificaciones con varios importes y dividendos (T-115, 04/11)
*Requisito:* **v1.12.4** y sesión de sincronización activa. Antes, borra la estructura de dividendos de TR que ya tenías y vuelve a enseñarla; deja vacío «Producto por defecto» y elige solo «Producto para este comercio o activo». Usa una notificación de dividendo de TR («… dividendo de 1,88 € por acción. Has recibido 0,01 €.»).
- [ ] Q1. **Enseñar:** los dos importes aparecen; el primero en rojo y el segundo con borde. Al pulsar «0,01 €» pasa a rojo y la vista previa muestra 0,01 €.
- [ ] Q2. **Destino › Dividendo:** se pueden elegir productos de la cartera y también de efectivo (no los de deuda). La vista previa dice «Se registrará un dividendo de 0,01 € en …».
- [ ] Q3. Guarda: en la cartera (app y escritorio) aparece un dividendo de 0,01 € del producto elegido, con la cuenta de liquidez asociada a la app del banco.
- [ ] Q4. Con el siguiente dividendo de otra empresa (otro importe por acción), la estructura lo reconoce y registra lo recibido, no el importe por acción.
- [ ] Q5. Las estructuras de compra que ya tenías siguen funcionando igual.

## R. Reinversión de dividendos (T-116, 04/11)
*Requisito:* **v1.12.5** y sesión de sincronización activa. La app actualiza su base de datos al abrirse (versión 24): comprueba que todo sigue en su sitio.
- [ ] R1. **Enseñar** con destino **Dividendo**: aparece la casilla «Reinvertir el dividendo» con su explicación. Con otro destino no aparece.
- [ ] R2. Con la casilla marcada, los selectores de producto no ofrecen productos de efectivo ni inmuebles. La vista previa dice «… y una compra del mismo importe (reinversión)».
- [ ] R3. Con el siguiente dividendo reinvertido de TR: en la cartera (app y escritorio) aparecen un **dividendo** y una **compra** del mismo importe, producto y fecha, con la cuenta de TR. La liquidez de TR no cambia y la compra sale con unidades estimadas en el escritorio.
- [ ] R4. **Estructuras** › editar una de dividendo: la casilla se puede marcar y desmarcar, y se conserva al guardar.

---

## Resultados y problemas encontrados
*(Escribe aquí lo que falle; Claude lo convierte en fichas de corrección. Lo ya corregido se quita.)*
