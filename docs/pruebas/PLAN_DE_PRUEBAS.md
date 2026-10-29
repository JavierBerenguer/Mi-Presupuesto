# Plan de pruebas manuales — Mi Patrimonio (móvil) + Rumbo (escritorio)

Este documento lo mantiene Claude. Cada bloque indica en qué versión se prueba. Marca `[x]` lo que funcione. Si algo falla, escribe debajo **qué hiciste, qué esperabas y qué pasó** (con una captura si puedes).

> **Leyenda:**
> - `[x]` probado por ti.
> - `[x] ✔` comprobado por Claude con datos reales (Supabase, Rumbo o tus capturas).
> - `~~tachado~~` ya no aplica.

**Versiones actuales:**
- **App:** v1.8.4 (https://github.com/JavierBerenguer/Mi-Presupuesto/releases/tag/v1.8.4).
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
- [ ] E1. Una notificación de un **comercio nuevo** de una estructura conocida crea el apunte sola. Aparece en Ajustes › Automatización › **Comercios nuevos**.
- [ ] E2. Llega el **aviso push** «Automatización pendiente». Al tocarlo se abre Automatización en la pestaña correcta.
- [x] E3. **Agrupar:** una cena con sus Bizums se ve como una fila con el neto y «+N».

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
- [ ] G2. Al tocar **Efectivo** se abre Cuentas. Al tocar **Inversiones**, la pestaña Cartera.
- [ ] G3. **Operativa del mes:** los ingresos, los gastos y el ahorro coinciden con Movimientos. La tasa de ahorro sale solo si hay ingresos.
- [ ] G4. **Colchón de liquidez** (v1.7.1):
  - «X meses sin invertir» usa el gasto de vida de los 6 meses completos anteriores;
  - «Y meses si sigues invirtiendo» suma además lo que pasas a inversiones a través de Rumbo, así que debe salir claramente menor.
- [ ] G5. **Resumen de inversiones:** rentabilidad, TIR, reparto por tipo (sin «Efectivo») y el enlace «Ver cartera».
- [ ] G6. El **ojo** oculta todos los importes nuevos, también los del eje del gráfico.
- [ ] G7. En el menú **Más** ya no aparece «Patrimonio».
- [x] ✔ G8. El **gráfico circular** ya no muestra «Ingresos». Una devolución resta del gasto de su categoría.
- [ ] G9. **Operativa neta:** las transferencias propias de salida restan de los ingresos y no suman a los gastos.
- [x] ✔ G10. **Evolución** con chips **Liquidez · Inversión · Total**, los tres visibles (v1.7.1).
- [x] ✔ G11. El gráfico tiene **ejes y cuadrícula** (v1.7.1) y **los importes en el eje vertical** (v1.8.3).
- [ ] G12. **Gastos según la vista** (v1.8.1):
  - con **Liquidez** elegida, lo invertido a través de Rumbo se suma a la porción Inversiones del gráfico circular;
  - con **Inversión** o **Total**, no se suma y aparece la nota «Además, en este periodo pasaste X € a inversiones…».
- [ ] G13. **Deslizar hacia abajo** en Inicio y en Movimientos sincroniza, con el círculo de carga (v1.8.4). En Cartera ya funcionaba.

---

## H. Panel completo de Rumbo en Cartera (v1.6.1)
*Requisitos:* SQL `20261030000000_paneles_storage.sql` ejecutado (hecho, verificación en true) y Rumbo reiniciado.
- [ ] H1. Cartera › **Ver panel completo de Rumbo**: se ve el panel del escritorio, con sus pestañas y gráficos. *(La v1.6.0 decía «no es HTML»; se corrigió en la v1.6.1.)*
- [ ] H2. Arriba sale «Actualizado en el escritorio: …».
- [ ] H3. Cambia algo en Rumbo y pulsa recargar: el cambio aparece.
- [ ] H4. **Modo avión:** abre desde la caché, con el aviso «sin conexión».
- [ ] H5. Un enlace externo del panel se abre en el navegador, no dentro de la app.
- [ ] H6. Con el **ojo** activado, primero sale el aviso con el botón «Mostrar panel».
- [ ] H7. Al cerrar sesión y volver a entrar, el panel se descarga de nuevo.

---

## I. Historia de las cuentas en el escritorio (v1.8.2)
- [x] ✔ I1. **Sincronizar ahora** sin errores.
- [x] ✔ I2. En Supabase, Trade Republic tiene **26 meses** de historia (el último, 30/09/2026 = 520,65 €).
- [x] ✔ I3. En Rumbo, Trade Republic tiene historia desde **agosto de 2024** y ya no «entra el 29 oct».
- [x] ✔ I4. **El mes** ya no muestra 997 € «nuevos» (solo quedan los 0,08 € de Neverless).

---

## J. Escritorio (Rumbo): mejoras del 29/10
- [x] ✔ J1. Al abrir Rumbo empieza en **Patrimonio**. Al recargar con F5, se queda en la pestaña donde estabas.
- [ ] J2. Los botones **Activo / Producto / Entidad / Tipo** de Distribución marcan el que pulsas.
- [x] ✔ J3. En Movimientos, las **unidades pequeñas** se ven bien (0,000001 BTC, no «0»).
- [ ] J4. **Aportaciones por mes** tiene leyenda: color, producto, lo aportado en el periodo y su %.
- [x] ✔ J5. **Copias mensuales** en modo nube: existe `mensual_2026-10.json`. Se guardan las 12 últimas.
- [ ] J6. **Dónde está tu dinero de verdad:** con el MSCI World 2X referenciado a un MSCI World normal, «en bolsa» sube a ~3.090 €.

---

## Resultados y problemas encontrados
*(Escribe aquí lo que falle; Claude lo convierte en fichas de corrección.)*

- **2026-10-28 · A1:** pantalla negra con «Petición bloqueada: no viene de Rumbo». **Corregido** (`Referrer-Policy: same-origin`).
- **2026-10-29 · Sincronización (409):** las cuentas no subían a Supabase tras vaciarlo. **Corregido** en las v1.4.3 a v1.4.5: orden de la cola, perfiles antiguos y filas resucitadas.
- **2026-10-29 · Importación en Rumbo:** «Sin conexión» falso y error 400 «All object keys must match». **Corregido**: se muestra el motivo real y los envíos se agrupan por forma de fila.
- **2026-10-29 · Panel en la app:** «no es HTML». **Corregido** en la v1.6.1.
- **2026-10-29 · Historia de cuentas:** el móvil tardaba en detectar la columna nueva. **Corregido** en la v1.8.2.
