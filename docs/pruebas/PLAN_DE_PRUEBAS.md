# Plan de pruebas manuales — Mi Patrimonio (móvil) + Rumbo (escritorio)

Este documento lo mantiene Claude. Cada bloque indica en qué versión se prueba. Marca `[x]` lo que funcione. Si algo falla, escribe debajo **qué hiciste, qué esperabas y qué pasó** (con una captura si puedes).

> **Leyenda:**
> - `[x]` probado por ti.
> - `[x] ✔` comprobado por Claude con datos reales (Supabase, Rumbo o tus capturas).
> - `~~tachado~~` ya no aplica.

**Versiones actuales:**
- **App:** v1.10.2 (https://github.com/JavierBerenguer/Mi-Presupuesto/releases/tag/v1.10.2).
- **Rumbo:** rama `claude/supabase` de `JavierBerenguer/Rumbo-Patrimonio`, en `D:\XX_Claude\App - Rea - Rumbo\rumbo-main`. Tras actualizarlo, reinícialo con `Iniciar.bat`.

---

## 0. Preparación (una sola vez)
- [X] **Copia de seguridad** de la app actual: Más › Importar/Exportar › Crear copia. Guárdala fuera del móvil.
- [X] Si tienes la versión **de depuración** instalada: desinstálala, instala la versión de release (o añade el repositorio en **ObtainX**) y **restaura la copia**.
- [X] **Rumbo:** ejecuta `Iniciar.bat` en `rumbo-main`. Se abre en el navegador.

## A. Rumbo en modo nube (escritorio)
- [x] A1. Pulsa **☁ Nube**, pega la URL y la clave *publishable* e inicia sesión. Debe quedar conectado.
- [x] A2. Crea un producto cotizado y registra una **compra** con unidades. El panel lo muestra con su valor.
- [x] A3. Cierra Rumbo y vuelve a abrirlo: los datos siguen ahí, porque se leen de la nube.
- [ ] A4. *(opcional)* **Sin internet**, Rumbo abre en **solo lectura** con un aviso, y al guardar dice «Sin conexión con la nube». Si Supabase *rechaza* un cambio, el mensaje ahora dice el motivo: «La nube ha rechazado los cambios (…)».
- ~~A5. «Subir mi cartera local»~~: no aplica, porque empezamos de cero con el inicio limpio.

## B. Sincronización en el móvil
- [x] B1. **Ajustes › Sincronización con Rumbo**: URL, clave publishable, email y contraseña. Se ve «Conectado como…».
- [x] ✔ B2. **Sincronizar ahora**: el perfil es siempre el **Principal**, ya no se elige. Aparece la fecha de la última sincronización y «pendientes: 0».
- [x] B3. En Rumbo aparecen tus **cuentas de la app** como efectivo con su saldo.
- [x] B4. Un apunte nuevo en el móvil actualiza el saldo de la cuenta en Rumbo.

## C. Cartera y liquidez
- [x] C1. Pestaña **Cartera**: valor, ganancia, TIR, reparto y productos, con «Actualizado: <fecha>». El valor **no** incluye tus cuentas.
- [x] C2. Al tocar un producto se ven su detalle y sus movimientos. Los del escritorio dicen «Se edita en Rumbo».
- [x] ✔ C3. **Venta o dividendo con liquidez** en Rumbo, con cuenta Trade Republic. Aparece en Movimientos, en su fecha, como «Venta · …» o «Dividendo · …», y el saldo de la cuenta cambia.
  - Comprobado con la venta de Bitcoin del 16/04/2026 y con los dividendos de Iberdrola, que sumaron +1,33 € al saldo publicado.
- [ ] C4. **Compra desde el móvil:** Cartera › **+**. Elige producto, importe, sin unidades y la cuenta de liquidez.
  - Aparece en el detalle y como apunte de la cuenta, que baja.
  - En Rumbo aparece con **unidades estimadas**.
- [ ] C5. Edita y borra esa compra desde el móvil. El saldo vuelve a su valor y en Rumbo desaparece.
- ~~C6. Inicio › Patrimonio~~: sustituido por el nuevo Inicio (bloque G).

## D. Compras desde notificaciones (v1.3.0)
*Requisito:* sesión iniciada en Sincronización y al menos un producto creado en Rumbo.
- [ ] D1. Cuando llegue una notificación de compra (por ejemplo, de un plan de ahorro de TR), ve a Automatización › Pendientes › **Enseñar**. En **Destino** elige **Compra de inversión** y el **producto**. La vista previa dice «Se registrará una compra de X € en <producto>».
- [ ] D2. Al guardar se crea la compra en la Cartera y, si la app tiene una cuenta EUR asociada, también el apunte de liquidez.
- [ ] D3. La siguiente notificación igual crea la compra sola.
- [ ] D4. En Rumbo aparece con **unidades estimadas**.
- [ ] D5. *(caso límite)* Con la sesión cerrada, la notificación queda pendiente. Al volver a entrar y sincronizar, se crea la compra.

## E. Lo de la v1.1.0 que aún no has probado
- [X] E1. Una notificación de un **comercio nuevo** de una estructura conocida crea el apunte sola. Aparece en Ajustes › Automatización › **Comercios nuevos**.
- [X] E2. Llega el **aviso push** «Automatización pendiente». Al tocarlo se abre Automatización en la pestaña correcta.
- [x] E3. **Agrupar:** una cena con sus Bizums se ve como una fila con el neto y «+N».
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

---

## F. Primera carga de inversiones (Trade Republic + Neverless)
- [x] F1. **Importar** › `primera_carga.xlsx` › **Previsualizar**: sin errores.
- [x] F2. **Confirmar**: 307 movimientos y los productos con sus unidades.
- [x] ✔ F3. **Fondos privados:** valor actual anotado (Apollo 197,78 € y EQT 154,24 €).
- [x] ✔ F4. **Saldo de Trade Republic** cuadrado con el extracto. Quedan pendientes estos ajustes:
  - [ ] gasto de **0,11 €** el 16/04/2026 (comisión de TR, categoría Inversiones › Comisiones);
  - [ ] los **10,10 €** posteriores al extracto, seguramente el redondeo: revisar a final de mes;
  - los **0,02 €** que se quedaron en Neverless se aceptan tal cual.
- [x] ✔ F5. La Cartera del móvil muestra los productos y el total de Rumbo.
- [x] ✔ F6. **Equito** dado de alta en Rumbo: compra de 100 € con cuenta TR y valor actual de 103,48 €.

---

## G. Nuevo Inicio (v1.5.0 → v1.8.x)
- [x] ✔ G1. **Patrimonio total** = Efectivo + Inversiones, y coincide con Rumbo (5.870,27 €). La barra muestra el % de cada parte.
- [x] G2. Al tocar **Efectivo** se abre Cuentas. Al tocar **Inversiones**, la pestaña Cartera.
- [ ] G3. **Operativa del mes:** los ingresos, los gastos y el ahorro coinciden con Movimientos. La tasa de ahorro sale solo si hay ingresos.
- [x] G4. **Colchón de liquidez** (v1.7.1):
  - «X meses sin invertir» usa el gasto de vida de los 6 meses completos anteriores;
  - «Y meses si sigues invirtiendo» suma además lo que pasas a inversiones a través de Rumbo, así que debe salir claramente menor.
- [x] G5. **Resumen de inversiones:** rentabilidad, TIR, reparto por tipo (sin «Efectivo») y el enlace «Ver cartera».
- [x] G6. El **ojo** oculta todos los importes nuevos, también los del eje del gráfico.
- [x] G7. En el menú **Más** ya no aparece «Patrimonio».
- [x] ✔ G8. El **gráfico circular** ya no muestra «Ingresos». Una devolución resta del gasto de su categoría.
- [x] G9. **Operativa neta:** las transferencias propias de salida restan de los ingresos y no suman a los gastos.
- [x] ✔ G10. **Evolución** con chips **Liquidez · Inversión · Total**, los tres visibles (v1.7.1).
- [x] ✔ G11. El gráfico tiene **ejes y cuadrícula** (v1.7.1) y **los importes en el eje vertical** (v1.8.3).
- [x] G12. **Gastos según la vista** (v1.8.1):
  - con **Liquidez** elegida, lo invertido a través de Rumbo se suma a la porción Inversiones del gráfico circular;
  - con **Inversión** o **Total**, no se suma y aparece la nota «Además, en este periodo pasaste X € a inversiones…».
- [x] G13. **Deslizar hacia abajo** en Inicio y en Movimientos sincroniza, con el círculo de carga (v1.8.4). En Cartera ya funcionaba.

---

## H. Panel completo de Rumbo en Cartera (v1.6.1)
*Requisitos:* SQL `20261030000000_paneles_storage.sql` ejecutado (hecho, verificación en true) y Rumbo reiniciado.
- [x] H1. Cartera › **Ver panel completo de Rumbo**: se ve el panel del escritorio, con sus pestañas y gráficos. *(La v1.6.0 decía «no es HTML»; se corrigió en la v1.6.1.)*
- [x] H2. Arriba sale «Actualizado en el escritorio: …».
- [x] H3. Cambia algo en Rumbo y pulsa recargar: el cambio aparece.
- [x] H4. **Modo avión:** abre desde la caché, con el aviso «sin conexión».
- [ ] H5. Un enlace externo del panel se abre en el navegador, no dentro de la app.
- [x] H6. Con el **ojo** activado, primero sale el aviso con el botón «Mostrar panel».
- [x] H7. Al cerrar sesión y volver a entrar, el panel se descarga de nuevo.

---

## I. Historia de las cuentas en el escritorio (v1.8.2)
- [x] ✔ I1. **Sincronizar ahora** sin errores.
- [x] ✔ I2. En Supabase, Trade Republic tiene **26 meses** de historia (el último, 30/09/2026 = 520,65 €).
- [x] ✔ I3. En Rumbo, Trade Republic tiene historia desde **agosto de 2024** y ya no «entra el 29 oct».
- [x] ✔ I4. **El mes** ya no muestra 997 € «nuevos» (solo quedan los 0,08 € de Neverless).

---

## J. Escritorio (Rumbo): mejoras del 29/10
- [x] ✔ J1. Al abrir Rumbo empieza en **Patrimonio**. Al recargar con F5, se queda en la pestaña donde estabas.
- [x] J2. Los botones **Activo / Producto / Entidad / Tipo** de Distribución marcan el que pulsas.
- [x] ✔ J3. En Movimientos, las **unidades pequeñas** se ven bien (0,000001 BTC, no «0»).
- [x] J4. **Aportaciones por mes** tiene leyenda: color, producto, lo aportado en el periodo y su %.
- [x] ✔ J5. **Copias mensuales** en modo nube: existe `mensual_2026-10.json`. Se guardan las 12 últimas.
- [x] J6. **Dónde está tu dinero de verdad:** con el MSCI World 2X referenciado a un MSCI World normal, «en bolsa» sube a ~3.090 €.

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
- Instala la v1.11.0 en el móvil.
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

---

## Resultados y problemas encontrados
*(Escribe aquí lo que falle; Claude lo convierte en fichas de corrección.)*

- **2026-10-28 · A1:** pantalla negra con «Petición bloqueada: no viene de Rumbo». **Corregido** (`Referrer-Policy: same-origin`).
- **2026-10-29 · Sincronización (409):** las cuentas no subían a Supabase tras vaciarlo. **Corregido** en las v1.4.3 a v1.4.5: orden de la cola, perfiles antiguos y filas resucitadas.
- **2026-10-29 · Importación en Rumbo:** «Sin conexión» falso y error 400 «All object keys must match». **Corregido**: se muestra el motivo real y los envíos se agrupan por forma de fila.
- **2026-10-29 · Panel en la app:** «no es HTML». **Corregido** en la v1.6.1.
- **2026-10-29 · Historia de cuentas:** el móvil tardaba en detectar la columna nueva. **Corregido** en la v1.8.2.
