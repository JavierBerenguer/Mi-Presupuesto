# Plan de pruebas manuales — Mi Patrimonio (móvil) + Rumbo (escritorio)

Este documento lo mantiene Claude y solo recoge lo que **queda por probar**: lo ya probado se quita. Marca `[x]` lo que funcione. Si algo falla, escribe debajo **qué hiciste, qué esperabas y qué pasó** (con una captura si puedes).

**Versiones actuales:**
- **App:** v1.12.0 (https://github.com/JavierBerenguer/Mi-Presupuesto/releases/tag/v1.12.0).
- **Rumbo:** rama `claude/supabase` de `JavierBerenguer/Rumbo-Patrimonio`, en `D:\XX_Claude\App - Rea - Rumbo\rumbo-main`. Tras actualizarlo, reinícialo con `Iniciar.bat`.

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

## F. Ajustes pendientes de la primera carga
- [ ] F4a. Gasto de **0,11 €** el 16/04/2026 (comisión de TR, categoría Inversiones › Comisiones).
- [ ] F4b. Los **10,10 €** posteriores al extracto, seguramente el redondeo: revisar a final de mes.

## G. Inicio
- [ ] G3. **Operativa del mes:** los ingresos, los gastos y el ahorro coinciden con Movimientos. La tasa de ahorro sale solo si hay ingresos.

## H. Panel completo de Rumbo en Cartera
- [ ] H5. Un enlace externo del panel se abre en el navegador, no dentro de la app.

## K. Categorías y edición de apuntes (v1.10.1–v1.10.2)
- [ ] K1. **Filtros de Movimientos › Categoría:** se abre el selector completo (búsqueda, árbol y A-Z). El botón inferior dice «Todas las categorías» y quita el filtro. Al elegir una categoría padre salen también sus subcategorías.
- [ ] K2. **Ajustes › Categorías › nueva o editar › Color:** debajo de la paleta hay un área de color, una barra de tono y un campo «#». Al arrastrar cambia el icono de la vista previa. Al escribir `915959` el color se aplica; con menos de 6 cifras el campo se marca en rojo y no cambia nada.
- [ ] K3. Guarda una categoría con un color hecho a mano, ciérrala y vuelve a abrirla: aparece el mismo código hexadecimal.
- [ ] K4. **Editar un apunte que vino de una notificación:** se puede pasar de **gasto a ingreso** (y al revés) y guardar. Sigue saliendo como automático y conserva la notificación original. La pestaña Transferencia no hace nada al editar.
- [ ] K5. *(v1.10.2)* El selector completo de categorías aparece también en estos sitios:
  - **Recurrentes**, al crear o editar uno («Sin categoría» la quita);
  - **Propuestas pendientes**;
  - **Importador de Trade Republic**, sin botón para dejarla sin categoría;
  - **Categorías › Categoría padre**: solo salen categorías principales, y está desactivado si la categoría tiene subcategorías;
  - **Borrar una categoría en uso › destino**: «Sin categoría» solo aparece si ningún presupuesto la usa.

## L. Movimientos de inversión integrados (v1.11.0, fichas T-097 a T-104)
**Antes de empezar:**
- Instala la v1.11.0 o posterior en el móvil.
- Cierra y vuelve a abrir el escritorio con su lanzador: así arranca con el código nuevo.

**Categorías (T-097)**
- [ ] L1. **Ajustes › Categorías › Inversiones:** «Compra», «Venta», «Dividendo» y «Comisión» llevan la nota «Movimientos de inversión». Se pueden renombrar y cambiar de color (prueba `#B5E8D5`), pero no borrar, archivar ni mover a otra categoría.
- [ ] L2. **Movimientos › Filtros › Categoría › Inversiones:** salen los movimientos de inversión (compras, dividendos…) junto a los apuntes de Inversiones. Con «Dividendo», solo los dividendos.
- [ ] L3. Al buscar «Dividendo» en Movimientos salen los dividendos. Su icono y su color son los de la categoría.
- [ ] L4. Al crear un apunte normal (o una regla de notificación, o al importar de TR), en el selector de categorías **no** aparecen esas cuatro.

**Totales e Inicio (T-098)**
- [ ] L5. Un ingreso apuntado en una categoría que no es «Ingresos» (por ejemplo, un regalo en «Otros») suma en los **ingresos** del mes, no resta de los gastos.
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
- [ ] L16. **Escritorio:** un dividendo con «Retención» 30 € e importe 120 € muestra «bruto 150 · ret. 30» en la tabla. El panel suma 120.
- [ ] L17. **Escritorio › Renta (informe fiscal):** ese cobro sale con bruto 150 y retención 30, y el total de retenciones del año lo incluye.
- [ ] L18. **Móvil:** ese movimiento muestra «bruto 150 € · retención 30 €» y suma 120 al saldo. Quita la retención desde el móvil: también se quita en el escritorio.
- [ ] L19. **Escritorio:** quita la **nota** o la **comisión** de un movimiento y recarga la página: no vuelve a aparecer (fallo corregido en T-101).

**Importación de TR (T-103)**
- [ ] L20. Importa un extracto de TR con dividendos: los que ya están en el escritorio salen como «Ya está en la cartera». Los nuevos van «a la cartera», al producto de su ISIN y con su retención. Intereses y comisiones siguen como apuntes.
- [ ] L21. Un dividendo de un ISIN que no tienes en la cartera queda en revisión, y puedes elegir el producto o ignorarlo.

**Escritorio siempre al día (T-104)**
- [ ] L22. Cambia algo desde el móvil, espera más de 30 s y pulsa **F5** en el escritorio: aparece sin cerrar la app.
- [ ] L23. Guarda o borra algo en **Mis datos**: el panel, los gráficos y las tablas se actualizan solos, y sigues en la misma pestaña y en la misma posición.

**Agrupar apuntes y cuenta fantasma (v1.11.1, T-106 y T-105)**
- [ ] L24. Agrupa **dos ingresos** (por ejemplo, 50 y 20): se puede, y el diálogo dice «Resultado: ingreso de 70,00 €». Con **dos gastos** (40 y 15) dice «gasto de 55,00 €».
- [ ] L25. Al agrupar, cualquier apunte se puede elegir como principal. Un grupo antiguo (gasto con devolución) sigue dando el mismo importe que antes.
- [ ] L26. Abre el apunte principal de un grupo de ingresos: debajo pone «Agrupa N apuntes · ingreso de X», sin signo menos.
- [ ] L27. Tras instalar la v1.11.1 y abrir la app, no aparece ninguna cuenta llamada «null» en ningún selector de cuentas, y los saldos son los de antes.

## M. Escritorio: valor de los manuales y nuevo «El mes» (T-107 y T-108, 02/11)
Antes de empezar, **reinicia Rumbo con `Iniciar.bat`**, para que cargue el motor nuevo, y pulsa **↻ Actualizar precios** una vez.

**Productos con precio manual (T-107)**
- [ ] M1. **Productos › Apollo:** aparece desde el **02/02/2026**, no desde el 29/10. Su gráfica sube en cada compra semanal. Ahora muestra participaciones y la vista de precio.
- [ ] M2. **Productos › EQT:** aparece desde el **02/04/2026**.
- [ ] M3. **Productos › EQT-0127:** ya tiene **aportado (100 €)** y **TIR**. Antes salían vacíos.
- [ ] M4. **El patrimonio de hoy y la TIR total** son los mismos que antes de actualizar.
- [ ] M5. Anota una **compra** en EQT o Apollo con fecha de hoy, sin anotar una valoración nueva. El valor del producto y el patrimonio suben ese importe enseguida. Luego borra la compra de prueba.

**«El mes» nuevo (T-108)**
- [ ] M6. Las tarjetas son **Ahorraste**, **Lo puso el mercado** e **Invertiste** (esta última, con «de tus cuentas a la cartera · no suma»). La cifra grande = Ahorraste + Lo puso el mercado.
- [ ] M7. **Abril de 2026:** el mercado ya no sale con una pérdida grande por las compras de Apollo. **Octubre:** ya no sale una ganancia grande del mercado por la valoración de Apollo y EQT.
- [ ] M8. **Tabla del mes:** cada fila cuadra: Al empezar + Ahorraste + Invertiste + Mercado (+ Entra al panel) = Al cerrar. En la fila de **Trade Republic**, Invertiste sale en negativo el mes que compras con esa cuenta.
- [ ] M9. **Junio a septiembre de 2026:** aparece la fila **«Fuera de tus cuentas»**, con las compras que no llevan cuenta.
- [ ] M10. **Mayo de 2026, cuenta Equito:** aparece desde el **01/05**, y el dividendo de EQT-0127 de ese día va en «Mercado», no en «Ahorraste».
- [ ] M11. **Compartir mi mes:** dice «Ahorraste» y «Lo puso el mercado» (también cuando el mercado es negativo).

**Gráfico y peor caída (T-108)**
- [ ] M12. **Patrimonio › vista «dinero»:** tres bandas, **Lo que invertiste**, **En tus cuentas** y **Lo que puso el mercado**, que juntas suman tu patrimonio.
- [ ] M13. **Vista «total»:** la línea se llama **«Lo que ahorraste»**.
- [ ] M14. **Cifras de Patrimonio:** «**Peor caída de la cartera**», alrededor del **−44 %** el 09/04/2025, con la nota de que los productos valorados a mano se interpolan.
- [ ] M15. Oculta todos los componentes menos uno con los filtros: la peor caída cambia y es la de ese producto.
- [ ] M16. **Ayuda:** están las preguntas «¿Qué diferencia hay entre Ahorraste e Invertiste?» y «¿Cómo se calcula la peor caída?».

## N. Saldo de apertura de las cuentas (T-110, v1.12.0)
*Requisito:* instalar la v1.12.0, sincronizar y reiniciar Rumbo.
- [ ] N1. **Escritorio › El mes › mayo de 2026:** la cuenta **Equito** entra con su saldo de apertura del **30/04** (normalmente 0), y el dividendo del 01/05 va en «Mercado».
- [ ] N2. **Trade Republic** empieza un día antes de su primera actividad de agosto de 2024, no el 31/08, y su saldo de hoy no cambia.

## O. Importar Neverless y cuenta de liquidez sin «Uso en app» (T-111 escritorio y T-112 app, 02/11)
*Hecho por Claude el 02/11:* se borraron con copia los movimientos de Neverless de la primera carga y se importó `2026-11-02 Neverless.csv`: 141 movimientos con la cuenta «Neverless cash» y 7 saldos de fin de mes (último: 0,09 € el 31/10). *Requisito:* cerrar y volver a abrir el escritorio para que cargue la versión nueva.
- [ ] O1. **Escritorio › Bitcoin:** 0,00835725 BTC, lo mismo que la app de Neverless.
- [ ] O2. **Escritorio › Cuentas › Neverless cash:** los saldos de fin de mes de abril a octubre, y 0,09 € hoy.
- [ ] O3. **El mes de julio de 2026:** en la fila de Neverless cash salen el ingreso de 71 € (Ahorraste) y la compra (Invertiste en negativo); ya no está en «Fuera de tus cuentas».
- [ ] O4. **Trade Republic:** su saldo ya no baja 70,98 € por la compra del 21/04, que antes estaba mal cargada a esa cuenta.
- [ ] O5. **Mis datos › Importar › Neverless:** vuelve a importar el mismo CSV. Sale «0 nuevos · 141 ya estaban» y no anota saldos.
- [ ] O6. Edita una compra importada (cambia la nota), borra un interés y vuelve a importar: la compra sigue como la dejaste y el interés vuelve.
- [ ] O7. **Editor de movimientos del escritorio:** «Neverless cash» aparece en «Cuenta de liquidez».
- [ ] O8. **App, con la versión que incluya T-112:** edita una compra de Neverless sin tocar la cuenta: se guarda y conserva «Neverless cash».
- [ ] O9. **App:** una compra nueva con la cuenta Neverless cash, con fecha posterior al 31/10, baja su saldo en la app y en el escritorio.
- [ ] O10. **Pendiente de hacer a mano:** el traspaso de 0,001456 BTC de TR a Neverless del 16/04 no se anota. Las unidades ya están en Bitcoin, por las compras de TR.

## P. Escritorio: guardar en «Mis datos» ya no recarga la página (02/11)
*Requisito:* pulsa F5 en el escritorio para cargar la versión nueva (no hace falta reiniciar).
- [ ] P1. **Mis datos › Movimientos:** baja por la lista, añade, edita y borra un movimiento. La página no parpadea, no vuelve arriba y la lista sale al día.
- [ ] P2. Lo mismo en **Productos**, **Cuentas** (incluido un saldo) y **Saldos y valores**. Cambiar entre estas subpestañas tampoco recarga.
- [ ] P3. **Mi objetivo**, el fondo de emergencia y el reparto: guardar y «Quitar…» cierran la ventana y no recargan.
- [ ] P4. Después de cambiar algo, pasa a **Patrimonio**: se recarga una vez y las cifras ya incluyen el cambio. Sin cambios, cambiar de pestaña no recarga.
- [ ] P5. **Actualizar precios** sigue recargando y te deja en la pestaña en la que estabas.

---

## Resultados y problemas encontrados
*(Escribe aquí lo que falle; Claude lo convierte en fichas de corrección. Lo ya corregido se quita.)*
