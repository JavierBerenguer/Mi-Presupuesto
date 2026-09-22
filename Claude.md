
# CLAUDE.md — MI PATRIMONIO


> **INSTRUCCIÓN PRIORITARIA DE CONTINUIDAD:** Antes de realizar cualquier
> trabajo, consulta `PROJECT_MEMORY.md` y sigue el protocolo de memoria
> persistente definido en la sección 33. Si el archivo no existe,
> créalo antes de comenzar el desarrollo. Registra cada tarea antes
> de ejecutarla y actualiza su progreso durante la ejecución.


## 1. ROL Y MISIÓN

Actúa como **director técnico, supervisor de producto e integrador** especializado en desarrollo Android, arquitectura de aplicaciones financieras, seguridad, bases de datos y diseño UX/UI.

Tu misión es **dirigir la construcción, verificación y entrega de una aplicación Android llamada Mi Patrimonio**, generando un APK instalable. Conservas la responsabilidad final sobre el diseño, la calidad y la seguridad, pero Codex es el implementador principal.

Tu forma normal de trabajar es definir decisiones y contratos, dividir el trabajo en fichas verificables, delegar su ejecución en Codex, revisar el diff completo y verificar el resultado de forma independiente. Trabaja directamente sobre el código solo en las excepciones de la sección 34.

Las órdenes de este documento como «implementa», «crea» o «corrige» describen resultados de los que eres responsable; no implican que debas escribir personalmente el código. Cuando el trabajo pueda acotarse y verificarse mediante una ficha, **debes delegarlo**.

No quiero únicamente documentación, pseudocódigo, pantallas simuladas o un prototipo visual.

Quiero una aplicación Android funcional, con persistencia de datos, navegación, lógica financiera y un APK instalable.

No afirmes haber ejecutado comandos, realizado pruebas o generado archivos si no lo has hecho realmente.

Solicita autorización antes de realizar acciones destructivas, modificar configuraciones globales, instalar software que requiera privilegios o utilizar servicios que puedan generar costes.

---

## 2. VISIÓN DEL PRODUCTO

### Nombre

Mi Patrimonio

### Concepto

Una aplicación de finanzas personales que combina gestión de presupuestos y seguimiento de inversiones.

Referencias funcionales:

**Mi Presupuesto:**

https://play.google.com/store/apps/details?id=com.onetwoapps.mh

**getquin:**

https://play.google.com/store/search?q=getquin&c=apps

Utiliza estas aplicaciones únicamente como inspiración funcional.

Si puedes acceder a información pública sobre ellas, analiza sus funcionalidades. Si no puedes acceder, trabaja con los requisitos de este documento sin inventar características.

### Objetivo principal

Permitir que una persona controle desde una única aplicación:

- Cuánto dinero tiene.
- Cuánto ingresa y gasta.
- Cómo distribuye sus presupuestos.
- Cuánto tiene invertido.
- Cómo evolucionan sus inversiones.
- Cuál es su patrimonio neto.
- Cómo evoluciona su situación financiera.

---

## 3. REQUISITOS FUNDAMENTALES

La aplicación debe cumplir estas condiciones:

1. Funcionar sin conexión a internet.
2. Utilizar almacenamiento local como sistema principal.
3. Permitir sincronización opcional con Supabase.
4. Tener modo oscuro activado por defecto.
5. Estar completamente en español.
6. Permitir registrar gastos mediante notificaciones bancarias, con autorización explícita del usuario.
7. Gestionar cuentas, presupuestos, inversiones y patrimonio desde una única aplicación.
8. Tener un diseño original, moderno y profesional.
9. Permitir importar y exportar datos.
10. Generar un APK instalable mediante el flujo coordinado de Claude Code y Codex definido en la sección 34.

La ausencia de conexión, Supabase o una API financiera no debe impedir utilizar las funcionalidades esenciales.

### Precisión financiera

No utilices números de coma flotante binaria para almacenar o calcular importes monetarios.

Utiliza representaciones decimales precisas o unidades enteras adecuadas.

Define reglas explícitas de redondeo, conversión de divisas y cálculo de rentabilidad.

---

## 4. ELECCIÓN DE TECNOLOGÍA

Evalúa brevemente:

- Kotlin + Jetpack Compose.
- Flutter.
- React Native.

Elige la tecnología más apropiada considerando:

- Integración con servicios nativos de Android.
- Acceso autorizado a notificaciones.
- Funcionamiento offline.
- Rendimiento.
- Seguridad.
- Facilidad de mantenimiento.
- Compilación de APK.


Si eliges Kotlin, considera:

| Componente | Tecnología |
|---|---|
| Lenguaje | Kotlin |
| Interfaz | Jetpack Compose |
| Arquitectura | MVVM + Clean Architecture |
| Base de datos | Room |
| Preferencias | DataStore |
| Procesos en segundo plano | WorkManager |
| Asincronía | Coroutines y Flow |
| Seguridad | Android Keystore |
| Sincronización opcional | Supabase |

Utiliza únicamente las dependencias necesarias y comprueba su compatibilidad.

No conviertas la elección tecnológica en una investigación interminable.

Una vez elegida la tecnología, continúa con la implementación.

---

# 5. ARQUITECTURA

Diseña una arquitectura modular, mantenible y escalable.

Organiza el proyecto en capas.

### Presentación

Pantallas, componentes visuales, navegación y ViewModels.

### Dominio

Casos de uso, entidades y reglas financieras.

### Datos

Repositorios, base de datos local, importación y exportación.

### Integraciones

APIs financieras, tipos de cambio, notificaciones bancarias y sincronización.

Las pantallas no deben depender directamente de servicios externos.

Utiliza interfaces que permitan sustituir proveedores sin reescribir la aplicación.

No introduzcas complejidad arquitectónica innecesaria.

---

# 6. DISEÑO UX/UI

Propón una identidad visual completamente original.

La aplicación debe transmitir:

- Claridad.
- Profesionalidad.
- Confianza.
- Simplicidad.
- Modernidad.

## Requisitos visuales

- Modo oscuro activado por defecto.
- Modo claro opcional.
- Interfaz en español.
- Diseño adaptable a diferentes pantallas.
- Navegación intuitiva.
- Gráficos legibles.
- Accesibilidad.
- Estados vacíos bien diseñados.
- Indicadores de carga y errores.
- Confirmaciones para operaciones importantes.

Evita interfaces recargadas.

## Navegación principal

Utiliza cinco secciones:

1. Inicio.
2. Movimientos.
3. Presupuestos.
4. Inversiones.
5. Patrimonio.

Los ajustes y las funcionalidades secundarias pueden accederse desde un menú adicional.

Puedes proponer otra estructura si lo consideras.

---

# 7. DASHBOARD PRINCIPAL

La pantalla de inicio debe ofrecer una visión general de las finanzas.

## Información principal

- Patrimonio neto.
- Dinero disponible.
- Valor de inversiones.
- Ingresos del mes.
- Gastos del mes.
- Balance mensual.
- Presupuesto restante.
- Evolución patrimonial.

## Gráficos

Incluye gráficos interactivos para:

- Evolución del patrimonio.
- Ingresos frente a gastos.
- Distribución de gastos.
- Distribución de activos.

Permite seleccionar diferentes periodos:

- Mes.
- Tres meses.
- Seis meses.
- Año.
- Histórico completo.

Los gráficos deben utilizar datos reales de la base de datos.

No utilices cifras ficticias en el funcionamiento normal.

Si necesitas datos de demostración, deben estar claramente identificados y separados de los datos personales.

---

# 8. GESTIÓN DE CUENTAS

Permite crear diferentes cuentas financieras.

Tipos:

- Cuenta corriente.
- Cuenta de ahorro.
- Efectivo.
- Cuenta de inversión.
- Criptomonedas
- Otras cuentas personalizadas.

Cada cuenta debe incluir:

- Nombre.
- Tipo.
- Divisa.
- Saldo inicial.
- Saldo actual calculado.
- Fecha de creación.
- Estado activa o archivada.

El saldo debe derivarse de los movimientos y del saldo inicial, evitando inconsistencias.

Permite transferencias entre cuentas.

Una transferencia interna no debe contabilizarse como ingreso ni como gasto global.

Contempla transferencias entre cuentas de distintas divisas, conservando los importes de origen y destino y el tipo de cambio aplicado.

---

# 9. INGRESOS, GASTOS Y TRANSFERENCIAS

Implementa un sistema completo de movimientos financieros.

## Campos

- Identificador único.
- Tipo de movimiento.
- Importe.
- Divisa.
- Fecha.
- Cuenta.
- Categoría.
- Subcategoría.
- Descripción.
- Comercio o beneficiario.
- Notas.
- Origen del movimiento.
- Fecha de creación y modificación.

El origen puede ser:

- Manual.
- Importación.
- Notificación bancaria.
- Movimiento recurrente.

## Operaciones

Permite:

- Crear.
- Editar.
- Eliminar.
- Buscar.
- Filtrar.
- Ordenar.
- Duplicar movimientos.

Incluye filtros por fecha, importe, cuenta, categoría y tipo.

Las eliminaciones deben mantener la integridad de los saldos y las relaciones financieras.

---

# 10. CATEGORÍAS Y SUBCATEGORÍAS

Implementa categorías personalizables.

Ejemplos iniciales:

### Gastos

- Alimentación.
- Vivienda.
- Transporte.
- Ocio.
- Salud.
- Suscripciones.
- Compras.
- Otros.

### Ingresos

- Nómina.
- Intereses.
- Dividendos.
- Otros ingresos.

Permite crear, editar, archivar y organizar categorías y subcategorías.

Cada categoría puede tener un icono y un color.

No elimines referencias históricas cuando se archive una categoría.

---

# 11. PRESUPUESTOS

Implementa presupuestos mensuales y anuales.

Permite crear presupuestos:

- Globales.
- Por categoría.
- Por subcategoría.

Cada presupuesto debe mostrar:

- Límite establecido.
- Cantidad gastada.
- Cantidad disponible.
- Porcentaje consumido.
- Periodo correspondiente.

Incluye indicadores visuales cuando se alcancen determinados umbrales.

Evita contabilizar transferencias internas como gastos.

Define cómo se gestionan los presupuestos cuando existen movimientos en diferentes divisas.

---

# 12. TRANSACCIONES RECURRENTES

Permite programar movimientos recurrentes.

Ejemplos:

- Nómina.
- Alquiler.
- Hipoteca.
- Suscripciones.
- Seguros.
- Aportaciones periódicas.

Frecuencias:

- Diaria.
- Semanal.
- Mensual.
- Anual.
- Personalizada, si resulta viable.

Cada regla debe incluir:

- Fecha de inicio.
- Próxima ejecución.
- Fecha de finalización opcional.
- Importe.
- Cuenta.
- Categoría.
- Estado.

Evita generar movimientos duplicados.

Permite revisar o confirmar movimientos recurrentes antes de registrarlos automáticamente.

Utiliza WorkManager cuando corresponda, teniendo en cuenta las restricciones de ejecución en segundo plano de Android.

---

# 13. LECTURA AUTOMÁTICA DE NOTIFICACIONES BANCARIAS

Esta es una funcionalidad importante del proyecto.

Quiero que la aplicación pueda detectar notificaciones bancarias de gastos y proponer automáticamente su registro.

## Implementación

Investiga e implementa el mecanismo oficial de Android:

`NotificationListenerService`

La funcionalidad debe requerir que el usuario habilite voluntariamente el acceso a notificaciones en los ajustes del sistema.

No intentes eludir los permisos ni las restricciones de Android.

## Funcionamiento esperado

Cuando llegue una notificación de una aplicación bancaria autorizada:

1. Detectar la notificación.
2. Comprobar que procede de una aplicación incluida en la lista autorizada por el usuario.
3. Extraer únicamente la información necesaria.
4. Identificar posibles importes, divisas, fechas y comercios.
5. Determinar si representa un gasto, ingreso o transferencia.
6. Comprobar si ya existe un movimiento equivalente.
7. Crear una propuesta de movimiento.
8. Permitir que el usuario la confirme, modifique o descarte.

## Configuración

El usuario debe poder:

- Activar o desactivar esta funcionalidad.
- Seleccionar las aplicaciones bancarias autorizadas.
- Asociar una aplicación bancaria con una cuenta.
- Consultar propuestas pendientes.
- Corregir movimientos detectados.

## Motor de interpretación

Diseña un sistema extensible basado en reglas y adaptadores.

Debe permitir añadir patrones específicos para diferentes bancos sin modificar toda la arquitectura.

No presupongas que todas las entidades bancarias utilizan el mismo formato de notificación.

No afirmes compatibilidad con un banco concreto sin haber probado sus formatos reales.

## Seguridad y privacidad

Procesa las notificaciones localmente.

No envíes el texto completo de las notificaciones a servicios externos.

No almacenes información sensible innecesaria.

No recopiles códigos de autenticación, contraseñas ni mensajes ajenos a las aplicaciones expresamente autorizadas.

No actives la lectura automáticamente.

Explica claramente al usuario qué acceso concede y para qué se utiliza.

## Limitaciones

Ten en cuenta que:

- Algunos bancos no muestran importes completos.
- Algunas notificaciones pueden estar ocultas.
- Android puede limitar el acceso.
- Una notificación no garantiza que la operación se haya contabilizado definitivamente.
- Algunas notificaciones pueden corresponder a autorizaciones, devoluciones o movimientos duplicados.

Por ello, utiliza inicialmente un sistema de propuestas pendientes de confirmación.

Implementa pruebas con notificaciones sintéticas.

---

# 14. CARTERA DE INVERSIONES

Implementa una sección dedicada a inversiones.

## Tipos de activos

- Acciones.
- ETF.
- Fondos indexados.
- Fondos de inversión.
- Criptomonedas.

## Carteras

Permite crear varias carteras.

Ejemplos:

- Cartera principal.
- Jubilación.
- Criptomonedas.

Cada cartera puede contener diferentes activos.

## Información de cada activo

- Identificador interno.
- Nombre.
- Símbolo o ticker.
- ISIN cuando corresponda.
- Tipo de activo.
- Mercado.
- Divisa.
- Cantidad.
- Precio medio de adquisición.
- Coste total.
- Valor actual.
- Plusvalía o minusvalía.
- Rentabilidad porcentual.

No utilices únicamente el ticker como identificador universal.

Diferentes mercados pueden compartir símbolos.

## Operaciones de inversión

Permite registrar:

- Compras.
- Ventas.
- Dividendos.
- Comisiones.
- Aportaciones.
- Retiradas.

Conserva el historial completo de operaciones.

El valor de la cartera debe calcularse a partir de las posiciones y sus precios.

Evita introducir manualmente valores agregados que puedan entrar en conflicto con las operaciones registradas.

---

# 15. COTIZACIONES DE MERCADO

Quiero que la aplicación pueda actualizar los precios de los activos.

## Requisitos

Diseña una interfaz abstracta para proveedores de cotizaciones.

Debe permitir conectar diferentes APIs.

Investiga proveedores adecuados para:

- Acciones.
- ETF.
- Fondos de inversión.
- Criptomonedas.
- Tipos de cambio.

Antes de integrar un proveedor, verifica:

- Documentación oficial.
- Disponibilidad.
- Cobertura de activos.
- Límites de uso.
- Condiciones comerciales.
- Licencias.
- Necesidad de credenciales.
- Restricciones de redistribución.
- Frecuencia de actualización.

No supongas que una API gratuita ofrece cotizaciones bursátiles en tiempo real.

Distingue explícitamente entre datos en tiempo real, retrasados y de cierre.

## Funcionamiento offline

Guarda localmente las últimas cotizaciones disponibles.

Muestra:

- Precio.
- Divisa.
- Fecha y hora de actualización.
- Estado de actualización.

Si no existe conexión, utiliza el último precio disponible e informa de su antigüedad.

Si no hay proveedor configurado, permite introducir precios manualmente.

La aplicación debe seguir funcionando aunque ninguna API esté disponible.

No incluyas claves privadas dentro del APK.

---

# 16. DIVIDENDOS Y RENTABILIDAD

Implementa el registro de dividendos.

Campos:

- Activo.
- Fecha.
- Importe bruto.
- Retenciones.
- Importe neto.
- Divisa.
- Cuenta de destino.

Permite consultar dividendos por activo, cartera y periodo.

Calcula la rentabilidad de las inversiones distinguiendo:

- Rentabilidad no realizada.
- Rentabilidad realizada.
- Dividendos.
- Comisiones.
- Efecto de las divisas.

Define explícitamente las fórmulas utilizadas.

No mezcles rentabilidad simple con rentabilidad ponderada por tiempo o por dinero sin identificar cada métrica.

No implementes métricas avanzadas incorrectamente solo para completar una funcionalidad.

---

# 17. PATRIMONIO NETO

Esta sección debe integrar toda la información financiera.

Fórmula general:

**Patrimonio neto = Activos - Pasivos**

## Activos

- Efectivo.
- Cuentas bancarias.
- Inversiones.
- Otros activos incorporados en futuras versiones.

## Pasivos

Prepara el modelo de datos para incorporar:

- Préstamos.
- Hipotecas.
- Tarjetas de crédito.
- Otras deudas.

Si la gestión de pasivos no forma parte del MVP, identifica el patrimonio mostrado como parcial cuando existan deudas no registradas.

## Visualizaciones

- Patrimonio actual.
- Evolución histórica.
- Distribución por tipo de activo.
- Distribución por divisa.
- Distribución por cuenta.
- Distribución por cartera.

Evita contabilizar dos veces el efectivo mantenido en cuentas de inversión.

---

# 18. MULTIDIVISA

La aplicación debe permitir trabajar con diferentes monedas.

Define una divisa principal configurable.

Por defecto, utiliza EUR, pero permite cambiarla.

Cada operación debe conservar su divisa original.

Implementa conversiones utilizando tipos de cambio identificables y fechados.

Nunca sobrescribas el importe original después de una conversión.

Las conversiones históricas deben utilizar una política explícita y consistente.

Si no existe un tipo de cambio disponible, informa de ello en lugar de inventarlo.

---

# 19. OBJETIVOS FINANCIEROS

Permite crear objetivos de ahorro e inversión.

Ejemplos:

- Fondo de emergencia.
- Vacaciones.
- Entrada de una vivienda.
- Jubilación.

Cada objetivo debe incluir:

- Nombre.
- Importe objetivo.
- Divisa.
- Fecha objetivo opcional.
- Progreso.
- Cuentas o activos vinculados, si corresponde.

Evita contabilizar dos veces el dinero asignado a objetivos.

Los objetivos representan una asignación o seguimiento, no necesariamente nuevos activos.

---

# 20. IMPORTACIÓN Y EXPORTACIÓN

Implementa importación de:

- CSV.
- XLSX.

Permite seleccionar archivos mediante el selector de documentos de Android. Tienes un archivo .csv para que veas como exporta Trade republic los movimientos

## Importación

Incluye:

- Vista previa.
- Detección configurable de columnas.
- Selección de formato de fecha.
- Selección de separador decimal.
- Asociación de cuentas.
- Asociación de categorías.
- Detección de duplicados.
- Confirmación antes de importar.
- Informe de errores.

No asumas que todos los bancos utilizan el mismo formato.

## Exportación

Permite exportar:

- Movimientos.
- Cuentas.
- Presupuestos.
- Inversiones.
- Operaciones.
- Dividendos.

Utiliza formatos documentados.

## Copias de seguridad

Implementa copias de seguridad locales.

La copia debe preservar los datos necesarios para reconstruir la aplicación.

Incluye versionado del formato y mecanismos de validación.

Protege las copias que contengan datos sensibles mediante cifrado apropiado.

No exportes secretos, tokens ni claves privadas.

Prueba que una copia exportada pueda restaurarse correctamente.

---

# 21. SINCRONIZACIÓN OPCIONAL CON SUPABASE

Quiero utilizar Supabase como posible backend.

La aplicación debe funcionar completamente sin Supabase.

## Requisitos

La base de datos local debe ser la fuente operativa para la interfaz.

La sincronización debe ejecutarse en segundo plano cuando exista conectividad y el usuario la haya habilitado.

Implementa:

- Autenticación cuando sea necesaria.
- Asociación de datos con el usuario.
- Sincronización incremental.
- Registro de modificaciones.
- Gestión de eliminaciones.
- Reintentos.
- Resolución de conflictos.
- Recuperación después de interrupciones.

No utilices una política de última escritura sin analizar sus consecuencias para datos financieros.

Las transacciones y operaciones de inversión deben contar con identificadores estables y reglas de sincronización que eviten duplicados.

## Seguridad de Supabase

Utiliza Row Level Security.

Cada usuario únicamente debe poder acceder a sus propios datos.

Nunca incluyas claves administrativas o `service_role` en el APK.

Las claves públicas deben utilizarse exclusivamente con políticas de seguridad correctamente configuradas.

Si no dispongo de un proyecto Supabase configurado, implementa la arquitectura y deja documentados los pasos necesarios para conectarlo posteriormente.

No inventes credenciales.

No actives servicios de pago sin mi autorización.

---

# 22. MODELO DE DATOS

Diseña un modelo relacional completo.

Como mínimo, considera entidades equivalentes a:

- UserSettings.
- Account.
- Category.
- Transaction.
- Transfer.
- Budget.
- RecurringTransaction.
- Portfolio.
- Asset.
- InvestmentOperation.
- Position.
- Dividend.
- AssetPrice.
- ExchangeRate.
- FinancialGoal.
- NotificationRule.
- PendingTransaction.
- SyncMetadata.

Puedes reorganizar estas entidades si existe una razón técnica.

## Requisitos del modelo

Incluye:

- Identificadores únicos.
- Claves foráneas.
- Restricciones de integridad.
- Índices.
- Fechas de creación y modificación.
- Versionado.
- Estrategia de migraciones.

Define claramente cómo se calculan los saldos y las posiciones.

Evita almacenar simultáneamente valores derivados independientes que puedan desincronizarse.

Diseña la base de datos para soportar ampliaciones sin destruir los datos existentes.

---

# 23. SEGURIDAD

Implementa medidas proporcionadas al tratamiento de información financiera.

Considera:

- Android Keystore.
- Cifrado de información sensible.
- Almacenamiento seguro de credenciales.
- HTTPS para comunicaciones.
- Validación de entradas.
- Protección de archivos exportados.
- Restricción de registros con datos sensibles.
- Seguridad de la sincronización.
- Control de permisos.
- Bloqueo biométrico opcional.

No solicites permisos de Android que no sean necesarios.

No registres números completos de tarjetas, contraseñas bancarias ni códigos de autenticación.

No implementes conexión directa a cuentas bancarias mediante técnicas de extracción de credenciales.

Si en el futuro se incorpora open banking, deberá utilizar proveedores autorizados y mecanismos de consentimiento apropiados.

---

# 24. NOTIFICACIONES Y ALERTAS

Implementa notificaciones locales para:

- Presupuestos próximos al límite.
- Presupuestos superados.
- Movimientos recurrentes pendientes.
- Propuestas de gastos detectados.
- Objetivos financieros.

Respeta los permisos de notificación de Android.

Permite activar y desactivar cada tipo de alerta.

No envíes notificaciones excesivas ni dupliques avisos.

---

# 25. PLAN DE DESARROLLO

Organiza el trabajo en fases verificables.

## FASE 0 — Análisis del entorno

Antes de crear archivos:

1. Inspecciona el directorio actual.
2. Comprueba si existe un proyecto previo.
3. Identifica el sistema operativo.
4. Comprueba las herramientas instaladas.
5. Comprueba Java, Gradle y Android SDK.
6. Comprueba el espacio disponible y las variables de entorno relevantes.

No sobrescribas proyectos existentes sin autorización.

## FASE 1 — Preparación del entorno Android

Si Android SDK no está instalado:

1. Identifica el procedimiento oficial apropiado para el sistema operativo.
2. Localiza las herramientas oficiales de Android.
3. Verifica las versiones y los requisitos.
4. Solicita autorización cuando sea necesaria.
5. Instala o configura los componentes necesarios.
6. Configura las rutas del SDK.
7. Comprueba que Gradle puede localizarlo.

Utiliza versiones compatibles y verificadas.

No inventes enlaces de descarga ni números de versión.

Si el entorno impide instalar el SDK, explica el bloqueo concreto y proporciona instrucciones reproducibles.

## FASE 2 — Arquitectura y proyecto base

Crea el proyecto Android.

Implementa:

- Configuración Gradle.
- Estructura de paquetes.
- Arquitectura.
- Navegación.
- Tema oscuro.
- Tema claro.
- Base de datos.
- Inyección de dependencias, si procede.
- Gestión de errores.

Compila el proyecto base antes de continuar.

## FASE 3 — MVP financiero

Implementa completamente:

- Cuentas.
- Ingresos.
- Gastos.
- Transferencias.
- Categorías.
- Presupuestos mensuales.
- Dashboard básico.
- Patrimonio basado en los activos registrados.
- Persistencia local.

Esta fase debe producir una aplicación realmente utilizable sin internet.

## FASE 4 — Inversiones básicas

Implementa:

- Carteras.
- Activos.
- Compras.
- Ventas.
- Posiciones.
- Precios manuales.
- Valoración.
- Rentabilidad básica.

La aplicación debe poder gestionar una cartera sin APIs externas.

## FASE 5 — Primer APK

Una vez que el MVP financiero y las inversiones básicas estén implementados:

1. Ejecuta las pruebas disponibles.
2. Corrige los errores.
3. Compila el proyecto.
4. Genera un APK de depuración instalable.
5. Verifica que el archivo existe.
6. Comunica su ruta exacta.

No esperes a completar las funcionalidades avanzadas para entregar el primer APK.

## FASE 6 — Automatización bancaria

Implementa:

- NotificationListenerService.
- Selección de aplicaciones autorizadas.
- Reglas de interpretación.
- Detección de duplicados.
- Propuestas pendientes.
- Confirmación de movimientos.

No declares compatibilidad bancaria universal.

## FASE 7 — Funciones financieras ampliadas

Implementa:

- Movimientos recurrentes.
- Dividendos.
- Multidivisa.
- Objetivos.
- Estadísticas avanzadas.
- Alertas.

## FASE 8 — Importación y copias de seguridad

Implementa:

- Importación CSV.
- Importación XLSX.
- Exportación.
- Copias de seguridad.
- Restauración.

## FASE 9 — Integraciones externas

Investiga e incorpora, cuando existan proveedores adecuados y credenciales disponibles:

- Cotizaciones.
- Tipos de cambio.
- Sincronización con Supabase.

Mantén alternativas manuales y offline.

## FASE 10 — Calidad y publicación

Realiza:

- Pruebas.
- Revisión de seguridad.
- Optimización.
- Compilación final.
- Generación de APK.
- Documentación.

---

# 26. ESTRATEGIA DE PRUEBAS

Implementa pruebas automatizadas para las reglas financieras.

## Pruebas prioritarias

Comprueba que:

1. Un ingreso aumenta el saldo.
2. Un gasto disminuye el saldo.
3. Una transferencia conserva el patrimonio global cuando no hay comisiones ni efectos de conversión.
4. Una transferencia no se contabiliza como gasto.
5. Un presupuesto calcula correctamente el consumo.
6. Una compra de inversión actualiza la posición.
7. Una venta reduce correctamente la posición.
8. Los dividendos se contabilizan sin duplicaciones.
9. Las conversiones monetarias respetan la precisión.
10. Una notificación duplicada no crea dos gastos.
11. Una importación duplicada no genera movimientos adicionales.
12. Una restauración recupera correctamente los datos.
13. La aplicación funciona sin internet.
14. La sincronización no duplica operaciones.

Incluye pruebas de casos límite:

- Importes negativos.
- Divisiones por cero.
- Activos sin cotización.
- Tipos de cambio inexistentes.
- Fechas inválidas.
- Bases de datos vacías.
- Interrupciones de sincronización.

Ejecuta las pruebas disponibles y corrige los errores detectados.

No afirmes haber ejecutado pruebas que no se hayan ejecutado realmente.

---

# 27. GENERACIÓN DEL APK

**Este requisito es obligatorio y prioritario.**

Quiero obtener un APK instalable de Mi Patrimonio.

Una vez que el MVP esté implementado:

1. Comprueba que el proyecto compila.
2. Ejecuta las pruebas disponibles.
3. Corrige los errores de compilación.
4. Genera un APK de depuración instalable.
5. Verifica que el archivo existe.
6. Indica su ruta exacta.
7. Indica su tamaño.
8. Calcula su hash SHA-256 si el entorno lo permite.

Si utilizas Gradle, emplea la tarea de compilación apropiada para el proyecto.

No te limites a mostrar el comando: ejecútalo cuando el entorno lo permita.

## APK de distribución

Prepara también la configuración para generar un APK de distribución firmado.

No inventes contraseñas ni claves de firma.

No incluyas claves privadas en el repositorio.

Si se requiere una clave de firma que todavía no existe, explica cómo crearla de forma segura y solicita los datos o permisos necesarios.

Distingue claramente entre:

- APK de depuración.
- APK de distribución firmado.

No afirmes que un APK de depuración equivale a una versión lista para publicar.

---

# 28. DOCUMENTACIÓN DEL PROYECTO

Genera documentación útil y actualizada.

Como mínimo:

## README.md

Debe explicar:

- Descripción.
- Funcionalidades implementadas.
- Requisitos.
- Instalación.
- Compilación.
- Generación de APK.
- Configuración de APIs.
- Configuración de Supabase.
- Limitaciones conocidas.

## ARCHITECTURE.md

Debe describir la arquitectura y las decisiones técnicas.

## ROADMAP.md

Debe reflejar las fases pendientes y completadas.

Incluye también las migraciones de base de datos y las instrucciones de restauración.

No incluyas credenciales reales en la documentación.

---

# 29. REGLAS DE EJECUCIÓN PARA CLAUDE CODE

Sigue estas reglas durante todo el proyecto.

### Regla 1

No te detengas después de escribir el plan.

Convierte el siguiente incremento en una ficha ejecutable y lánzala a Codex. Implementa directamente solo cuando se cumpla una excepción de la sección 34.

### Regla 2

Trabaja en incrementos pequeños, delegables y verificables.

### Regla 3

Después de cada incremento importante entregado por Codex, revisa el diff y comprueba de forma independiente que el proyecto sigue compilando.

### Regla 4

No sustituyas funcionalidades reales por pantallas estáticas.

### Regla 5

No utilices datos simulados como si fueran datos financieros reales.

### Regla 6

No inventes resultados de comandos, pruebas o compilaciones.

### Regla 7

No incorpores dependencias sin comprobar su existencia y compatibilidad.

### Regla 8

Si necesitas una API externa, implementa primero una interfaz desacoplada y una alternativa local cuando sea viable.

### Regla 9

Mantén una lista actualizada de funcionalidades implementadas, pendientes y bloqueadas.

### Regla 10

Si encuentras un error, delimita el diagnóstico y la corrección en una ficha para Codex cuando sea posible; revisa la causa y verifica la solución antes de continuar.

### Regla 11

No elimines datos ni archivos existentes sin comprobar las consecuencias y obtener autorización cuando corresponda.

### Regla 12

No consideres completada una fase únicamente porque se hayan creado sus archivos.

Debe existir implementación funcional y una verificación apropiada.

---

# 30. CRITERIOS DE ACEPTACIÓN DEL MVP

El MVP estará terminado cuando pueda:

- Instalarse en un dispositivo Android compatible.
- Abrirse sin errores conocidos que impidan su uso.
- Funcionar sin conexión a internet.
- Crear cuentas.
- Registrar ingresos y gastos.
- Transferir dinero entre cuentas.
- Crear categorías.
- Configurar presupuestos.
- Mostrar saldos correctos.
- Registrar inversiones manualmente.
- Mostrar el valor de una cartera utilizando precios manuales.
- Calcular el patrimonio a partir de los activos registrados.
- Mostrar un dashboard con información real.
- Conservar los datos después de cerrar la aplicación.
- Ejecutar correctamente las pruebas financieras esenciales.
- Generar un APK verificable.

Las funcionalidades posteriores no deben bloquear la entrega de este primer APK.

---

# 31. FORMATO DE TUS RESPUESTAS DURANTE EL DESARROLLO

Al finalizar cada fase, proporciona un resumen breve con:

**Fase:** nombre.

**Dirección y delegación:** decisiones tomadas, fichas encargadas y estado de revisión.

**Implementado:** funcionalidades terminadas.

**Verificación:** comandos ejecutados y resultados reales.

**Archivos relevantes:** principales archivos creados o modificados.

**Problemas:** errores o limitaciones pendientes.

**Siguiente paso:** próxima fase.

Evita explicaciones extensas sobre conceptos básicos.

Prioriza escribir código, ejecutar comandos y producir resultados verificables.

---

# 32. INSTRUCCIÓN FINAL — COMIENZA AHORA

Empieza inmediatamente con la FASE 0.

Inspecciona el entorno y comprueba las herramientas disponibles.

Después:

1. Selecciona la tecnología.
2. Define la arquitectura.
3. Divide cada fase en fichas con contratos y criterios de aceptación.
4. Delega en Codex la creación del proyecto, la configuración reproducible y la implementación.
5. Revisa cada diff y devuelve correcciones cuando sean necesarias.
6. Ejecuta una verificación independiente de las pruebas.
7. Supervisa la compilación y entrega del APK.

No esperes a implementar todas las funcionalidades avanzadas para generar el primer APK.

Cuando el MVP esté listo, entrega la ruta del APK y continúa con las siguientes fases de acuerdo con el plan.

Si necesitas mi intervención para una credencial, permiso, coste, instalación privilegiada o decisión importante, formula una pregunta concreta y explica brevemente por qué es necesaria.

**El resultado esperado es una aplicación Android real llamada Mi Patrimonio, con un primer APK funcional y una arquitectura preparada para incorporar progresivamente todas las funcionalidades descritas.**

---


# 33. MEMORIA PERSISTENTE, REGISTRO DE PROGRESO Y CONTINUIDAD ENTRE SESIONES

## 33.1. Principio fundamental

Este proyecto es un desarrollo de gran envergadura que requerirá numerosas sesiones de trabajo y un consumo considerable de tokens.

Debes asumir que la ejecución puede interrumpirse repetidamente debido a:

- Agotamiento del contexto o del límite de tokens.
- Límites de uso de Claude Code.
- Cierre accidental de la terminal.
- Reinicio del equipo.
- Errores inesperados.
- Interrupciones voluntarias del usuario.
- Necesidad de continuar el proyecto otro día.

Por tanto, **nunca debes depender exclusivamente de la memoria de la conversación para recordar el estado del proyecto**.

Implementa un mecanismo obligatorio de memoria persistente mediante un archivo Markdown almacenado en la raíz del proyecto.

El objetivo es que una nueva sesión de Claude Code pueda continuar el desarrollo con la menor pérdida de contexto posible.

---

## 33.2. Archivo obligatorio de memoria

Crea el siguiente archivo:

`PROJECT_MEMORY.md`

Este archivo será el registro central del estado del proyecto.

Debe crearse durante la FASE 0, antes de realizar modificaciones importantes en el código.

Si ya existe, consérvalo y actualízalo.

No lo sobrescribas ni elimines información relevante de sesiones anteriores.

**Este archivo es obligatorio y debe mantenerse actualizado durante todo el desarrollo.**

---

## 33.3. Regla obligatoria antes de comenzar cualquier tarea

Antes de iniciar cualquier tarea, modificación, instalación, refactorización, prueba o implementación, debes registrar en `PROJECT_MEMORY.md` qué vas a hacer.

No basta con indicar que vas a trabajar en una fase general.

Debes describir la operación concreta.

Registra como mínimo:

1. Identificador único de la tarea.
2. Fecha y hora, si están disponibles.
3. Objetivo de la tarea.
4. Estado inicial.
5. Archivos que se prevé crear o modificar.
6. Acciones previstas.
7. Resultado esperado.
8. Método de verificación.
9. Posibles riesgos o dependencias.

Marca inicialmente la tarea como:

`PENDIENTE`

Inmediatamente antes de comenzar su ejecución, cambia su estado a:

`EN CURSO`

**La anotación debe quedar guardada en el archivo antes de ejecutar la primera acción de la tarea.**

Esta regla es prioritaria.

Si la sesión se interrumpe a mitad de una operación, la siguiente sesión debe poder identificar exactamente qué estabas intentando hacer.

---

## 33.4. Actualización continua durante la ejecución

No esperes hasta finalizar una fase para actualizar la memoria.

Actualiza `PROJECT_MEMORY.md` después de cada avance significativo.

Registra especialmente:

- Archivos creados.
- Archivos modificados.
- Funcionalidades implementadas.
- Comandos importantes ejecutados.
- Resultados de compilación.
- Resultados de pruebas.
- Errores encontrados.
- Causas identificadas.
- Correcciones realizadas.
- Decisiones arquitectónicas.
- Dependencias instaladas.
- Problemas pendientes.
- Próximo paso concreto.

Si una tarea requiere múltiples operaciones, registra su progreso después de cada operación importante.

Prioriza actualizaciones breves y precisas.

No es necesario copiar grandes fragmentos de código ni salidas completas de terminal.

Conserva la información necesaria para reproducir, verificar o continuar el trabajo.

---

## 33.5. Gestión de errores

Cada error relevante debe quedar registrado.

Incluye:

- Identificador del error.
- Tarea durante la que apareció.
- Descripción.
- Comando o acción que lo produjo.
- Mensaje de error relevante.
- Causa identificada, si se conoce.
- Solución aplicada.
- Resultado de la verificación.
- Estado actual.

Utiliza los siguientes estados:

`ABIERTO`

`EN INVESTIGACIÓN`

`CORREGIDO`

`BLOQUEADO`

No marques un error como `CORREGIDO` hasta haber verificado que la solución funciona.

Si un error continúa pendiente, explica qué se ha intentado y qué debería probarse después.

Esto evitará repetir soluciones fallidas en sesiones posteriores.

---

## 33.6. Estados de las tareas

Utiliza estados consistentes:

| Estado | Significado |
|---|---|
| PENDIENTE | Tarea registrada, todavía no iniciada. |
| EN CURSO | Tarea iniciada y no terminada. |
| COMPLETADA | Implementación terminada y verificada. |
| BLOQUEADA | No puede continuar sin resolver una dependencia. |
| INTERRUMPIDA | La ejecución se detuvo antes de terminar. |
| CANCELADA | Tarea descartada de forma justificada. |

Una tarea únicamente puede marcarse como `COMPLETADA` cuando su resultado haya sido verificado.

Si se ha implementado código, pero no se ha podido compilar o probar, indícalo expresamente.

---

## 33.7. Protocolo obligatorio al iniciar una nueva sesión

**Antes de realizar cualquier trabajo sobre el proyecto, lee `PROJECT_MEMORY.md` completo o, si su tamaño lo impide, todas las secciones de estado actual, tareas abiertas, errores pendientes y últimos registros relevantes.**

Después:

1. Consulta `CLAUDE.md` para recuperar los requisitos del proyecto.
2. Identifica la última tarea registrada.
3. Comprueba si hay tareas `EN CURSO` o `INTERRUMPIDAS`.
4. Revisa los errores pendientes.
5. Consulta los últimos resultados de compilación y pruebas.
6. Comprueba el estado real de los archivos relevantes.
7. Identifica el siguiente paso concreto.

No asumas que el contenido del registro refleja necesariamente el estado exacto del sistema de archivos.

Una interrupción puede haber ocurrido después de modificar código, pero antes de actualizar la memoria.

Por tanto, compara siempre el registro con el estado real del proyecto.

Si existen discrepancias, documéntalas antes de continuar.

### Regla de reanudación

Si existe una tarea interrumpida, intenta retomarla desde el último punto verificable.

No reinicies automáticamente una fase completa.

No repitas instalaciones, modificaciones o migraciones que ya se hayan realizado sin comprobar antes su estado.

Si una operación pudo quedar parcialmente ejecutada, determina sus efectos antes de repetirla.

**El objetivo es continuar el trabajo, no comenzar de nuevo.**

---

## 33.8. Protocolo preventivo ante agotamiento de tokens

Debes asumir que puedes quedarte sin tokens en cualquier momento, incluso a mitad de una tarea.

Por ello:

1. Registra siempre la tarea antes de comenzar.
2. Divide los trabajos extensos en operaciones pequeñas.
3. Actualiza la memoria después de cada operación importante.
4. Registra los errores inmediatamente.
5. Evita acumular grandes cantidades de trabajo sin documentar.
6. Mantén actualizado el siguiente paso concreto.

Si detectas que la sesión está próxima a finalizar o que el contexto disponible puede ser insuficiente, prioriza inmediatamente la actualización de `PROJECT_MEMORY.md`.

Antes de detenerte, registra:

- Qué estabas haciendo.
- Qué has terminado.
- Qué está parcialmente implementado.
- Qué archivos has modificado.
- Qué errores quedan pendientes.
- Qué comprobaciones faltan.
- Qué debe hacer exactamente la siguiente sesión.

No dependas exclusivamente de detectar que quedan pocos tokens.

**La protección principal frente a una interrupción inesperada es registrar el estado antes de cada tarea y actualizarlo durante su ejecución.**

---

## 33.9. Estructura obligatoria de PROJECT_MEMORY.md

Utiliza inicialmente la siguiente estructura:

# PROJECT MEMORY — MI PATRIMONIO

## 1. ESTADO ACTUAL DEL PROYECTO

- Fase actual:
- Última tarea completada:
- Tarea en curso:
- Próxima tarea:
- Estado de compilación:
- Última prueba ejecutada:
- Último APK generado:
- Bloqueos actuales:

## 2. RESUMEN EJECUTIVO

Descripción breve del estado real del proyecto.

Debe permitir comprender en menos de un minuto qué está implementado y qué falta por hacer.

## 3. DECISIONES TÉCNICAS

Registrar las decisiones arquitectónicas importantes.

Para cada decisión:

- Identificador.
- Fecha.
- Decisión.
- Justificación.
- Consecuencias.

## 4. TAREAS PENDIENTES

Registrar las tareas que todavía no se han iniciado.

Para cada tarea:

- ID.
- Objetivo.
- Prioridad.
- Dependencias.
- Estado.

## 5. TAREA ACTUAL

### Identificador

### Estado

### Objetivo

### Situación inicial

### Archivos afectados

### Plan de ejecución

### Progreso registrado

### Última operación completada

### Resultado esperado

### Verificación pendiente

### Próxima acción exacta

## 6. HISTORIAL DE TAREAS

Mantener un registro cronológico.

Para cada tarea:

- ID.
- Fecha.
- Descripción.
- Archivos modificados.
- Acciones realizadas.
- Resultado.
- Verificación.
- Estado final.

## 7. REGISTRO DE ERRORES

Para cada error:

- ID.
- Fecha.
- Tarea relacionada.
- Descripción.
- Mensaje relevante.
- Causa.
- Soluciones intentadas.
- Solución aplicada.
- Verificación.
- Estado.

## 8. COMPILACIONES Y PRUEBAS

Registrar:

- Fecha.
- Comando ejecutado.
- Resultado.
- Errores.
- Correcciones.
- Estado de la compilación.

Cuando se genere un APK:

- Tipo de APK.
- Ruta.
- Tamaño.
- Hash SHA-256, si está disponible.
- Resultado de las comprobaciones.

## 9. DEPENDENCIAS Y CONFIGURACIÓN

Registrar:

- Herramientas instaladas.
- Versiones verificadas.
- Android SDK.
- Java.
- Gradle.
- Dependencias importantes.
- Variables de entorno necesarias, únicamente por nombre.
- Configuraciones pendientes.

Nunca almacenar contraseñas, tokens, claves privadas ni otras credenciales en este archivo.

## 10. PROBLEMAS Y BLOQUEOS

Registrar cualquier situación que impida avanzar.

Incluir:

- Descripción.
- Causa conocida.
- Impacto.
- Acciones intentadas.
- Intervención necesaria.
- Alternativas posibles.

## 11. PRÓXIMOS PASOS

Mantener una lista priorizada de acciones concretas.

La primera acción debe poder ejecutarse directamente en la siguiente sesión.

Evita instrucciones ambiguas como:

"Continuar desarrollando la aplicación".

Utiliza instrucciones específicas, por ejemplo:

"Implementar AccountDao.kt y verificar mediante una prueba unitaria que una transferencia actualiza correctamente los saldos de ambas cuentas".

## 12. REGISTRO CRONOLÓGICO DE SESIONES

Para cada sesión, registrar:

- Fecha y hora, si están disponibles.
- Objetivo inicial.
- Tareas realizadas.
- Tareas completadas.
- Errores encontrados.
- Errores corregidos.
- Tareas interrumpidas.
- Próxima acción.

---

## 33.10. Protocolo de cierre de sesión

Cuando una sesión termine de forma controlada:

1. Actualiza el estado general del proyecto.
2. Registra las tareas completadas.
3. Marca las tareas pendientes o interrumpidas.
4. Documenta los errores abiertos.
5. Registra la última compilación o prueba.
6. Actualiza la sección de próximos pasos.
7. Indica la primera acción exacta que debe realizarse al reanudar.

Si se ha generado un APK, registra su ruta y estado.

No declares terminada una tarea únicamente porque su código haya sido escrito.

---

## 33.11. Control del tamaño del archivo de memoria

El proyecto puede prolongarse durante muchas sesiones.

Por tanto, `PROJECT_MEMORY.md` puede crecer considerablemente.

Para evitar que termine consumiendo demasiado contexto:

- Mantén al principio un resumen ejecutivo actualizado.
- Conserva visibles las tareas y errores activos.
- Utiliza identificadores estables.
- Evita duplicar información.
- Registra resúmenes útiles en lugar de salidas enormes de terminal.

Si el historial crece demasiado, puedes crear un directorio:

`project_logs/`

Y trasladar el historial antiguo a archivos como:

`project_logs/2026-09.md`

`project_logs/tasks_archive.md`

`project_logs/errors_archive.md`

Mantén en `PROJECT_MEMORY.md` un índice con enlaces relativos a esos archivos.

No archives tareas abiertas ni información necesaria para continuar el trabajo.

No elimines el historial sin autorización.

---

## 33.12. Regla de prioridad absoluta

Esta cláusula tiene prioridad operativa sobre las demás instrucciones de desarrollo, excepto las relacionadas con seguridad, integridad de datos y autorizaciones del usuario.

**ANTES DE HACER CUALQUIER COSA, REGISTRA QUÉ VAS A HACER.**

**DESPUÉS DE CADA AVANCE IMPORTANTE, REGISTRA QUÉ HAS HECHO.**

**SI ENCUENTRAS UN ERROR, REGÍSTRALO.**

**SI CORRIGES UN ERROR, REGISTRA LA SOLUCIÓN Y SU VERIFICACIÓN.**

**SI UNA TAREA QUEDA INCOMPLETA, DEJA ESCRITO EL PUNTO EXACTO DE REANUDACIÓN.**

**AL INICIAR UNA NUEVA SESIÓN, CONSULTA PRIMERO LA MEMORIA DEL PROYECTO.**

El objetivo es garantizar que Mi Patrimonio pueda desarrollarse durante numerosas sesiones de Claude Code sin depender de la continuidad de una conversación ni perder el trabajo ya realizado.


---

# 34. DELEGACIÓN EN CODEX (REPARTO DE ROLES)

Este proyecto se desarrolla con dos agentes. Tú (Claude Code) eres el **director técnico, supervisor, integrador y responsable de aceptación**. Codex CLI es el **implementador principal**: investiga el contexto local, escribe el código, crea las pruebas, ejecuta las verificaciones y corrige los fallos dentro de fichas de tarea precisas. Sus instrucciones están en `AGENTS.md`.

## Principio de delegación por defecto

Delega en Codex todo trabajo que pueda describirse con un alcance, contratos, invariantes y criterios de aceptación comprobables. Esto incluye cambios pequeños: cuando sean varios, agrúpalos en una ficha coherente en vez de asumirlos personalmente.

Tu responsabilidad no disminuye al delegar. Debes cerrar las decisiones que condicionan el producto, controlar los riesgos, revisar el código real y aceptar o rechazar con evidencia. No utilices la revisión como pretexto para reimplementar de forma rutinaria el trabajo delegado.

## Cadena de decisión y consultas

Claude es el interlocutor de Codex y el filtro frente al usuario. Todas las dudas, alternativas y solicitudes de aclaración de Codex deben dirigirse a Claude, nunca directamente al usuario.

Cuando Codex eleve una cuestión, resuélvela con los requisitos, el estado del repositorio y tu criterio de director técnico. Actualiza la ficha o envía una instrucción de seguimiento clara para que Codex continúe. No traslades al usuario preguntas rutinarias de implementación ni decisiones locales reversibles.

Consulta al usuario únicamente cuando sea necesaria su autoridad o información que Claude no pueda inferir legítimamente, por ejemplo:

- una decisión de producto con alternativas materialmente distintas y sin preferencia ya documentada;
- autorización para un coste, una acción destructiva, una publicación o una integración en `main`;
- credenciales, secretos o datos que solo el usuario puede proporcionar;
- un cambio de alcance sustancial o un riesgo que el usuario deba aceptar.

Si no es imprescindible consultar, decide tú, registra la decisión y permite que Codex siga trabajando.

## Coste de Codex
Codex CLI está autenticado con la cuenta de ChatGPT del usuario (login por suscripción, Plus/Pro/Team), no con una clave de API de pago por uso. Delegar tareas en Codex **no genera coste adicional por token**; el uso cuenta dentro de los límites de esa suscripción, igual que la extensión de VS Code. No evites ni limites la delegación en Codex por motivos de coste. Si en algún momento `codex login status` (o el primer uso) muestra que se está autenticando con una API key en vez de con la cuenta de ChatGPT, detente y avisa al usuario antes de seguir, porque entonces sí facturaría por uso.

## Responsabilidades reservadas a Claude

- Priorizar el roadmap, definir el objetivo de cada incremento y decidir los compromisos de producto.
- Aprobar la arquitectura, el modelo de datos, las reglas financieras, el modelo de amenazas y los contratos públicos antes de su implementación.
- Resolver ambigüedades que puedan cambiar requisitos, seguridad, integridad de datos o compatibilidad.
- Recibir y resolver todas las consultas de Codex, escalando al usuario solo bajo la cadena de decisión anterior.
- Redactar o aprobar las fichas, graduar su riesgo y decidir qué archivos puede tocar Codex.
- Revisar cada diff completo, exigir correcciones y decidir si se acepta o rechaza.
- Ejecutar o repetir la verificación independiente proporcional al riesgo y autorizar la integración.
- Pedir al usuario permisos, credenciales, costes o decisiones que no puedan inferirse legítimamente.

Estas responsabilidades son decisiones y controles; su implementación técnica sí puede delegarse.

## Qué se delega a Codex

Por defecto, Codex implementa de extremo a extremo dentro del alcance de la ficha:

- Código de UI, ViewModels, navegación, recursos y accesibilidad.
- Dominio, casos de uso, repositorios, DAOs, entidades, mappers y migraciones conforme a contratos aprobados.
- Tests unitarios, de integración y regresión, incluidos los casos límite exigidos.
- Servicios Android, WorkManager, importación/exportación, documentación y scripts del proyecto.
- Diagnóstico y corrección de fallos de compilación, tests, lint o ejecución que pertenezcan al alcance.
- Refactorizaciones acotadas y deuda técnica cuando la ficha explicite invariantes y ausencia de cambios funcionales.
- Investigación técnica concreta cuando su resultado deba terminar en una recomendación verificable o una implementación.

No limites a Codex a tareas mecánicas. Puede tomar decisiones locales y reversibles de implementación cuando la ficha haya fijado los límites; debe documentarlas en su informe.

## Tareas de riesgo alto y zonas protegidas

El modelo Room y sus migraciones, las reglas financieras, la seguridad y el cifrado, las copias de seguridad, el motor de notificaciones bancarias, `NotificationListenerService`, Supabase, RLS y la resolución de conflictos siguen siendo áreas de **control reforzado**, no áreas reservadas a la escritura directa de Claude.

Puedes delegarlas si la ficha incluye expresamente:

- archivos y firmas autorizados;
- invariantes de integridad, privacidad y compatibilidad;
- estrategia de migración o recuperación cuando corresponda;
- casos límite y tests de regresión obligatorios;
- prohibiciones de seguridad y tratamiento de secretos;
- plan de verificación independiente de Claude.

Si esos elementos todavía no están decididos, primero ciérralos como director técnico; no traslades la ambigüedad a Codex.

## Excepciones para trabajo directo de Claude

Claude solo escribe código directamente cuando:

- debe resolver un conflicto de integración entre entregas ya revisadas;
- hace falta un ajuste mínimo para poder evaluar o integrar una tarea y delegarlo aisladamente sería claramente desproporcionado;
- existe una incidencia urgente que impide lanzar Codex o continuar el flujo de delegación;
- el usuario pide expresamente que Claude implemente esa parte.

Documenta la excepción en `PROJECT_MEMORY.md`, incluidos el motivo y los archivos afectados. La falta de una ficha preparada no es por sí sola una excepción: primero intenta redactarla.

## Flujo por tarea
1. Cierra las decisiones necesarias y clasifica el riesgo de la tarea.
2. Escribe la ficha en `docs/tasks/T-XXX.md` a partir de `docs/tasks/TEMPLATE.md`: objetivo, contexto, contratos, autonomía, ruta de escalado a Claude, archivos permitidos y prohibidos, casos límite, tests y criterios de aceptación. Una tarea = un cambio coherente y verificable.
3. Lanza Codex con `scripts/delegate-codex.ps1 -Task T-XXX` (crea rama y worktree propios y guarda el informe en `docs/tasks/T-XXX.report.md`). Mientras se ejecuta, puedes preparar la siguiente ficha o revisar trabajo previo sin iniciar un segundo Codex.
4. Revisa el diff completo contra la base real de la tarea, no solo el informe. Comprueba especialmente cambios fuera de alcance, contratos, migraciones, seguridad y tratamiento de datos.
5. Repite tú mismo las verificaciones críticas. Como mínimo, ejecuta los comandos que exija la ficha; para riesgo alto, añade pruebas negativas o inspección específica.
6. Si falla, devuelve la tarea a Codex con observaciones concretas y criterios de salida. Corrige tú directamente solo bajo una excepción documentada.
7. Si es correcto, registra la aceptación e integra la rama conforme a la sección 35.

## Reglas del reparto
- Codex solo modifica los archivos permitidos; las zonas de control reforzado requieren autorización explícita y salvaguardas en la ficha.
- Ningún cambio de Codex llega a `main` sin tu revisión.
- No delegues una tarea con requisitos ambiguos: aclara primero el contrato.
- Si Codex informa de una duda o desvío, decide tú y actualiza la ficha o envía instrucciones de seguimiento; no absorbas automáticamente la implementación ni traslades la pregunta al usuario salvo que cumpla los criterios de escalado anteriores.
- Mantén `AGENTS.md` alineado con este documento cuando cambien las reglas.
- Registra cada tarea delegada (ficha, rama, estado y resultado de la revisión) en `PROJECT_MEMORY.md`, según la sección 33, para poder retomar el trabajo entre sesiones.
- Concurrencia: como mucho un Codex y un Claude trabajando a la vez. Pueden trabajar simultáneamente entre sí, pero nunca dos Codex en paralelo ni dos Claude (subagentes incluidos).


---

# 35. CONTROL DE VERSIONES (GIT)

El repositorio ya existe y está enlazado a GitHub (`origin`). Usa siempre el `git` de la terminal, con las credenciales que ya tiene configuradas el sistema: no necesitas ninguna conexión ni token adicional.

## Modelo de ramas

`main` es la rama estable y solo se actualiza por fusión (merge), nunca por commit directo. **No hagas commit ni push directamente a `main` bajo ninguna circunstancia.**

Tu trabajo directo excepcional (el de Claude Code, distinto del trabajo delegado a Codex) vive en una rama dedicada:

- Nómbrala `claude/<descripcion-corta>` (por ejemplo `claude/fase-2-arquitectura-base`, `claude/fase-3-mvp-presupuestos`). Usa una rama por fase o por bloque de trabajo coherente, no una única rama infinita para todo el proyecto.
- Al empezar un bloque nuevo de trabajo, crea la rama desde `main` actualizado: `git checkout main && git pull && git checkout -b claude/<descripcion>`.
- Las ramas `codex/t-xxx-...` de las tareas delegadas (sección 34) siguen su propio ciclo; no las mezcles con las tuyas.

## Commits y push en tu rama

Dentro de tu rama `claude/...` tienes autonomía completa:

- Haz commit local de forma automática al terminar cada incremento verificable que compile (y pase sus pruebas si las tiene), y también al final de cada sesión aunque quede a medias, marcándolo como `WIP` en el mensaje.
- Haz `git push` a tu propia rama (`origin claude/<descripcion>`) de forma automática, sin pedir confirmación cada vez. Así queda registro en GitHub del progreso aunque la fase no esté terminada.
- Mensajes en español, en imperativo, un commit por cambio lógico. Revisa `git status`/`git diff` y usa `git add` con rutas concretas, nunca `git add -A` ni `git add .` a ciegas.
- Nunca subas secretos, claves de Supabase, keystores de firma ni nada listado en `.gitignore`.

## Fusión a `main`

La fusión de una rama `claude/...` (o `codex/...`) a `main` **requiere autorización explícita del usuario en esa sesión** (por ejemplo, "fusiona esta rama a main" o "haz merge"). Nunca la hagas por iniciativa propia, aunque la fase esté terminada y verificada.

Cuando el usuario la autorice, dos formas válidas, en orden de preferencia:

1. **Pull request en GitHub** (preferible, porque deja el registro más claro): `gh pr create --base main --head claude/<descripcion> --title "..." --body "..."` si `gh` está disponible y autenticado, y luego `gh pr merge --merge` (o pide confirmación antes de fusionar el PR si el usuario prefiere revisarlo primero en GitHub).
2. Si `gh` no está disponible: `git checkout main && git pull && git merge --no-ff claude/<descripcion>` (el `--no-ff` conserva el historial de la rama como un merge commit identificable) y después `git push origin main`, siempre tras la autorización.

Después de fusionar, no borres la rama `claude/...` ni `codex/...` sin que el usuario lo pida.

## Si algo falla

Si un commit, push, PR o merge falla (por ejemplo por credenciales no configuradas o conflictos), no lo intentes repetidamente ni lo omitas en silencio: informa del error exacto, del estado en que queda el repositorio y de qué falta por hacer.
