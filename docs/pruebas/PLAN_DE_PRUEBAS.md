# Plan de pruebas manuales — Mi Patrimonio (móvil) + Rumbo (escritorio)

Este documento lo mantiene Claude. Cada bloque indica en qué versión se prueba. Marca `[x]` lo que funcione. Si algo falla, escribe debajo **qué hiciste, qué esperabas y qué pasó** (con una captura si puedes).

**Versiones actuales:**
- **App:** v1.2.0 (https://github.com/JavierBerenguer/Mi-Presupuesto/releases/tag/v1.2.0).
- **Rumbo:** rama `claude/supabase` de `JavierBerenguer/Rumbo-Patrimonio`, en la carpeta `D:\XX_Claude\App - Rea - Rumbo\rumbo-main`.

---

## 0. Preparación (una sola vez)
- [ ] **Copia de seguridad** de la app actual: Más › Importar/Exportar › Crear copia. Guárdala fuera del móvil.
- [ ] Si tienes la versión **de depuración** instalada: desinstálala, instala la v1.2.0 (o añade el repositorio en **ObtainX**) y **restaura la copia**.
  - Comprueba que siguen tus cuentas, apuntes, categorías, presupuestos y automatizaciones.
  - Las carteras y activos antiguos ya no existen, y es lo esperado.
- [ ] **Rumbo:** ejecuta `Iniciar.bat` en `rumbo-main`. Se abre en el navegador.

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

## D. Compras desde notificaciones — ⏳ pendiente (llegará en v1.3.0, ficha T-077)
*(Se detallará cuando esté lista.)*

## E. Lo de la v1.1.0 que aún no has probado en el móvil
- [ ] E1. Una notificación de un **comercio nuevo** de una estructura conocida crea el apunte solo. Aparece en Ajustes › Automatización › **Comercios nuevos**.
- [ ] E2. Llega el **aviso push** «Automatización pendiente». Al tocarlo se abre Automatización en la pestaña correcta.
- [ ] E3. **Agrupar:** mantén pulsada una cena y selecciona los Bizums › Agrupar › elige la cena.
  - Se ve una fila con el neto y «+N».
  - Presupuestos e Inicio cuentan el neto.

---

## Resultados y problemas encontrados
*(Escribe aquí lo que falle; Claude lo convierte en fichas de corrección.)*
