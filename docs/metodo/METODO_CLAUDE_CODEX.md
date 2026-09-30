# Método Claude + Codex: dirección técnica y delegación supervisada

Kit reutilizable (Windows, PowerShell) para desarrollar cualquier proyecto (app móvil, Python, MATLAB, web, scripts, análisis de datos…) con dos agentes de IA de roles separados:

- **Claude Code**: director técnico, supervisor, integrador y responsable de aceptación.
- **Codex CLI**: implementador principal.

Nació y se depuró en el proyecto «Mi Patrimonio» (más de 60 tareas delegadas). Este documento lo generaliza. Cómo usarlo:
1. Lee las secciones 1 a 4 para entender la mecánica.
2. Sigue la **puesta en marcha** (sección 5).
3. Copia las **plantillas** (sección 6) al nuevo proyecto y rellena los `{{MARCADORES}}`.

---

## 1. Idea central

| | Claude Code | Codex CLI |
|---|---|---|
| Rol | Cerebro: decide, especifica, revisa, verifica, integra | Músculo: investiga el código, implementa, prueba, corrige |
| Escribe código | Solo por excepción (sección 3.4) | Siempre, dentro de una ficha |
| Habla con el usuario | Sí, es el único interlocutor | Nunca. Sus dudas van a Claude |
| Trabaja en | Rama `claude/<bloque>` | Rama `codex/t-xxx` en un *worktree* aislado |
| Responde de | Calidad final, seguridad, integridad, decisiones | Que la ficha quede cumplida y verificada |

Principios:
1. **Delegar por defecto.** Todo trabajo que se pueda describir con alcance, contratos y criterios de aceptación comprobables se escribe como **ficha** y lo ejecuta Codex, aunque sea pequeño; varios cambios pequeños se agrupan en una ficha.
2. **Delegar no reduce la responsabilidad.** Claude revisa el diff real, no solo el informe, y repite él mismo las verificaciones.
3. **No se traslada ambigüedad.** Si falta una decisión, Claude la cierra *antes* de escribir la ficha.
4. **Memoria en disco, no en la conversación.** Todo el estado vive en `PROJECT_MEMORY.md`; cualquier sesión nueva puede continuar.
5. **Honestidad radical.** Nadie afirma que algo compila o pasa las pruebas sin haberlo ejecutado. Los fallos se registran tal cual.
6. **Concurrencia 1 + 1.** Como máximo un Codex y un Claude a la vez. Pueden trabajar en paralelo entre sí (Claude revisa o prepara la siguiente ficha mientras Codex trabaja), pero nunca dos Codex ni dos Claude (subagentes incluidos).

---

## 2. Ciclo de vida de una tarea

```
Petición del usuario
   │
   ▼
[Claude] 1. Analiza: lee memoria, código real y datos; cierra decisiones; gradúa el riesgo
[Claude] 2. Registra la tarea en PROJECT_MEMORY.md (EN CURSO)
[Claude] 3. Escribe docs/tasks/T-XXX.md y hace commit en su rama   ← la ficha debe estar commiteada
[Claude] 4. Lanza: .\scripts\delegate-codex.ps1 -Task T-XXX -Base claude/<bloque>
   │            (crea rama codex/t-xxx + worktree en .local/worktrees/t-xxx)
   ▼
[Codex]  5. Implementa, prueba, corrige; escribe docs/tasks/T-XXX.report.md
   │
   ▼
[Claude] 6. Revisa el diff COMPLETO (fuera de alcance, contratos, datos, seguridad)
[Claude] 7. Verifica en el worktree: build + tests + prueba con datos reales si aplica
[Claude] 8a. ¿Falla por algo de fondo?  → devuelve a Codex con observaciones y criterios de salida
         8b. ¿Error trivial (import, tipo)? → lo corrige él (excepción documentada)
[Claude] 9. Integra: patch del worktree → git apply --3way en su rama
[Claude] 10. Verificación final en su rama: tests ×3 (detecta tests inestables) + build/artefacto
[Claude] 11. Commit + push de su rama; ficha con «Revisión de Claude»; memoria actualizada
[Claude] 12. Resume al usuario: qué cambia para él, qué se verificó, qué falta
```

Integración recomendada (paso 9), en PowerShell:
```powershell
# En el worktree de la tarea
git add -A
git diff --cached --binary --output=..\..\patches	0XX.patch   # no uses «>» ni «|»: PowerShell 5.1 estropea las tildes
# En el repositorio principal
git apply --3way .local\patches	0XX.patch
```
Es más robusto que fusionar la rama de Codex cuando la rama de Claude ha avanzado entre medias.

---

## 3. Reglas del reparto

### 3.1 Reservado a Claude (decisiones y controles)
- Priorizar el roadmap y definir el objetivo de cada incremento.
- Aprobar la arquitectura, el modelo de datos, las reglas de negocio y de cálculo, el modelo de amenazas y los contratos públicos.
- Resolver ambigüedades que afecten a requisitos, seguridad, integridad o compatibilidad.
- Redactar las fichas, graduar su riesgo y decidir qué archivos puede tocar Codex.
- Revisar cada diff, exigir correcciones, aceptar o rechazar.
- Verificar de forma independiente e integrar.
- Pedir al usuario permisos, credenciales, costes o decisiones de producto.

### 3.2 Delegado a Codex
Código de cualquier capa, pruebas (incluidos los casos límite), migraciones y esquemas conforme a contratos aprobados, scripts, documentación, diagnóstico y corrección de fallos dentro del alcance, refactorizaciones con invariantes explícitos e investigación técnica que acabe en una recomendación verificable. Codex puede tomar **decisiones locales y reversibles** y debe documentarlas en su informe.

### 3.3 Zonas de control reforzado
Son las zonas donde un error cuesta caro: modelo de datos y migraciones, cálculos críticos (financieros, científicos, de ingeniería), seguridad, cifrado y credenciales, copias de seguridad, sincronización, integraciones externas y ficheros de build o dependencias.

**Se pueden delegar**, pero la ficha debe incluir: archivos y firmas autorizados, invariantes, estrategia de migración o recuperación, casos límite, tests de regresión obligatorios, prohibiciones de seguridad y el plan de verificación de Claude.

### 3.4 Excepciones en las que Claude escribe código
Solo en estos casos, y siempre documentados en la memoria con el motivo y los archivos:
- conflicto de integración entre entregas ya revisadas;
- ajuste mínimo para poder evaluar o integrar (un import que falta, un test mal planteado) cuando delegarlo sería desproporcionado;
- incidencia urgente que impide usar Codex;
- el usuario lo pide expresamente.

«No tengo la ficha preparada» **no** es una excepción.

### 3.5 Cadena de consultas
- Codex → Claude, siempre. Codex no pregunta nunca al usuario; si necesita una decisión, la plantea en su informe («Preguntas para Claude») y sigue con lo independiente.
- Claude → usuario, **solo** cuando hace falta su autoridad o información que Claude no puede inferir: decisiones de producto con alternativas materialmente distintas, costes, acciones destructivas, publicación o fusión a `main`, credenciales, o cambios de alcance.
- En todo lo demás Claude decide, registra la decisión y deja que Codex continúe.

---

## 4. Control de versiones

- `main` es estable y solo recibe **fusiones autorizadas por el usuario en esa sesión**. Nunca commit ni push directo a `main`.
- Claude trabaja en `claude/<bloque>` (una rama por fase o bloque coherente) y hace commit y push de forma autónoma tras cada incremento verificado.
- Codex trabaja en `codex/t-xxx` dentro de un worktree en `.local/worktrees/`. No hace merge ni push.
- Commits: un cambio lógico por commit, en imperativo, con `git add <rutas>` explícitas (nunca `git add -A` a ciegas en el repo principal).
- Fusión a `main` (solo con autorización): primero un PR (`gh pr create` / `gh pr merge --merge`); si no hay `gh`, `git merge --no-ff`.
- **Nada fuera de la carpeta del proyecto.** Worktrees, parches, artefactos, logs y copias de prueba van en `.local/`, que está en `.gitignore`.
- Los datos privados del usuario van en `datos-privados/` (ignorada por git). Se pueden usar en **pruebas locales temporales** que se borran y nunca se commitean; los tests versionados usan datos sintéticos.

---

## 5. Puesta en marcha en un proyecto nuevo

### 5.1 Requisitos
- Windows 10/11 con PowerShell 5.1 o superior.
- Git, con el repositorio creado y un primer commit (y opcionalmente un remoto en GitHub).
- Claude Code (CLI o extensión de VS Code).
- Codex CLI: `npm i -g @openai/codex`, y después `codex login` con la cuenta de ChatGPT.
- Comprueba con `codex login status` que usa **la suscripción de ChatGPT y no una API key**: con API key cada token se factura aparte. Si aparece una API key, detente y decide.
- La toolchain del proyecto (Python + pytest, MATLAB, Node, JDK + Gradle…).

### 5.2 Estructura de archivos
```
<proyecto>/
├── CLAUDE.md                  # requisitos del producto + bloque del método (6.1)
├── AGENTS.md                  # instrucciones para Codex (6.2)
├── PROJECT_MEMORY.md          # memoria persistente (6.5)
├── docs/tasks/
│   ├── TEMPLATE.md            # plantilla de ficha (6.3)
│   ├── COMMON.md              # contrato común + lecciones aprendidas (6.4)
│   ├── T-001.md …             # fichas (versionadas)
│   └── T-001.report.md …      # informes de Codex (ignorados por git)
├── scripts/
│   └── delegate-codex.ps1     # lanzador de Codex (6.6)
├── datos-privados/            # ignorada
└── .local/                    # ignorada: worktrees/, patches/, artefactos, logs
```

`.gitignore` mínimo:
```
.local/
datos-privados/
docs/tasks/*.report.md
.env
*.key
```

### 5.3 Arranque
1. Crea los archivos de 5.2 a partir de las plantillas y adapta la sección «Toolchain» de `AGENTS.md` y `COMMON.md` (tabla 5.4).
2. En la primera sesión, pide a Claude: «Lee CLAUDE.md, crea PROJECT_MEMORY.md y empieza por la FASE 0».
3. FASE 0: Claude inspecciona el entorno (SO, herramientas y versiones, estructura) y lo registra.
4. A partir de ahí: plan por fases, y cada incremento se convierte en una ficha delegada.
5. En cada sesión nueva basta con decir «continúa con el proyecto (recuerda mandar a trabajar a Codex)».

### 5.4 Adaptación por tecnología

| Proyecto | Verificación en la ficha | Artefacto | Notas |
|---|---|---|---|
| Android (Kotlin) | `.\gradlew.bat testDebugUnitTest assembleDebug` | APK en `.local/apk/` con tamaño y SHA-256 | Robolectric para tests con Android |
| Python | `.\.venv\Scripts\python -m pytest -q`, `ruff check .`, `mypy` si hay tipos | wheel, CLI o notebook ejecutado | Fija las dependencias en `requirements.txt` o `pyproject` |
| MATLAB | `matlab -batch "results = runtests('tests'); assertSuccess(results)"` | `.mlapp`, toolbox o figuras | Tests con `matlab.unittest`; tolerancias numéricas explícitas |
| Web / Node | `npm test`, `npm run lint`, `npm run build` | `dist/` | Pruebas e2e opcionales con Playwright |
| Análisis de datos | tests de las funciones puras + ejecución del pipeline con una muestra | informe o figuras reproducibles | Los datos reales solo en `datos-privados/` |
| C/C++ | `cmake --build build; ctest --test-dir build` | `.exe` | Activa los *warnings* como errores |

Si el sandbox de Codex no puede ejecutar la verificación (red, daemons, licencias como la de MATLAB), la ficha lo dice («No puedes ejecutar X; Claude lo ejecuta») y Codex debe releer el código con especial cuidado. Claude compila y prueba siempre en el paso 7.

---

## 6. Plantillas

### 6.1 Bloque para `CLAUDE.md`
Pégalo al final del `CLAUDE.md` del proyecto, después de los requisitos del producto.

```markdown
# MÉTODO DE TRABAJO (Claude dirige, Codex implementa)

> Antes de cualquier trabajo, lee `PROJECT_MEMORY.md` y sigue su protocolo. Si no existe, créalo.

## Roles
Eres el **director técnico, supervisor, integrador y responsable de aceptación** de {{PROYECTO}}.
Codex CLI es el **implementador principal** (instrucciones en `AGENTS.md`).
Las órdenes «implementa», «crea» o «corrige» describen resultados de los que respondes,
no que debas escribir tú el código: si el trabajo cabe en una ficha verificable, **delégalo**.

## Flujo por tarea
1. Cierra las decisiones necesarias y gradúa el riesgo (bajo, medio o alto).
2. Registra la tarea en `PROJECT_MEMORY.md` antes de empezar.
3. Escribe `docs/tasks/T-XXX.md` desde `TEMPLATE.md` y haz commit en tu rama.
4. Lanza `.\scripts\delegate-codex.ps1 -Task T-XXX -Base <tu rama>`.
5. Revisa el diff completo contra la base: alcance, contratos, datos, seguridad.
6. Repite tú las verificaciones ({{COMANDO_VERIFICACION}}); en riesgo alto, añade pruebas negativas.
7. Si falla, devuélvela a Codex con observaciones concretas. Si es correcta, integra, verifica,
   haz commit y push, y completa la «Revisión de Claude» en la ficha.

## Cuándo escribes código tú
Solo en conflictos de integración, ajustes mínimos para integrar, incidencias urgentes que
impidan usar Codex, o si el usuario lo pide. Documenta cada excepción en la memoria.

## Consultas
Codex solo te pregunta a ti. Tú solo consultas al usuario para decisiones de producto
materialmente distintas, costes, acciones destructivas, fusión a `main`, credenciales o cambios de alcance.
En todo lo demás, decide y registra.

## Concurrencia y coste
Máximo un Codex y un Claude a la vez. Codex usa la suscripción de ChatGPT (sin coste por token):
no limites la delegación por coste, pero si `codex login status` muestra una API key, avisa antes de seguir.

## Git
`main` solo por fusión autorizada por el usuario en la sesión. Tu trabajo va en `claude/<bloque>`
(commit y push autónomos tras cada incremento verificado); el de Codex, en `codex/t-xxx` (worktree en `.local/`).
Nada fuera de la carpeta del proyecto. Nunca subas secretos.

## Honestidad
No afirmes haber ejecutado comandos, pruebas o compilaciones que no hayas ejecutado.
Una tarea solo está COMPLETADA cuando se ha verificado.
```

### 6.2 `AGENTS.md` (instrucciones para Codex)
```markdown
# AGENTS.md — {{PROYECTO}} (instrucciones para Codex)

Eres el **implementador principal**. Claude Code es el director técnico: fija las decisiones y los
límites de cada tarea y revisa tu trabajo. Entregas cada ficha de extremo a extremo: entender el
contexto, implementar, crear o actualizar pruebas, verificar y corregir lo que esté en el alcance.

Puedes tomar decisiones locales, reversibles y coherentes con la ficha; documéntalas en el informe.
Si una decisión afecta a requisitos, interfaces públicas, seguridad, integridad de datos o archivos
no autorizados, detén esa parte y elévala a Claude.

**No hagas preguntas al usuario.** Claude es tu único interlocutor: formula las dudas en la sección
«Preguntas para Claude» del informe y continúa con lo que no dependa de ellas.

Contexto del producto: `CLAUDE.md` (consúltalo, no lo modifiques).

## Proyecto
{{DESCRIPCIÓN BREVE, STACK, ARQUITECTURA, IDIOMA DE LA INTERFAZ}}

## Cómo trabajas
1. Trabaja solo en la ficha asignada (`docs/tasks/T-XXX.md`) y en `docs/tasks/COMMON.md`.
2. Inspecciona lo que necesites, pero modifica solo los archivos autorizados.
3. Respeta las interfaces y contratos existentes.
4. Trabajas en tu propia rama y worktree: sin merge, sin push, sin tocar `main`.
5. Implementa también las pruebas, recursos y documentación exigidos.
6. Ejecuta la verificación de la ficha antes de terminar (o indica que no puedes y por qué).
7. Termina con el informe: qué hiciste, decisiones locales, archivos tocados, comandos **con su
   resultado real**, riesgos, desvíos y «Preguntas para Claude».

## Zonas de control reforzado (solo con autorización explícita en la ficha)
{{p. ej. modelo de datos y migraciones, cálculos críticos, seguridad y credenciales,
integraciones externas, archivos de build y dependencias}}

## Reglas de código
{{REGLAS DEL DOMINIO. Ejemplos: dinero sin coma flotante; unidades físicas explícitas;
tolerancias numéricas documentadas; funciones puras para la lógica; sin datos ficticios en el
flujo normal; sin secretos en el código ni en los logs; tests para todo comportamiento nuevo y
sus casos límite}}

## Honestidad
No afirmes que algo compila o que los tests pasan sin haberlo ejecutado. Si un comando falla,
di cuál y con qué error. Si la ficha es ambigua en algo material, no inventes: pregunta a Claude.

## Toolchain
{{comandos de test, lint y build}}
```

### 6.3 `docs/tasks/TEMPLATE.md` (ficha)
```markdown
# T-XXX — Título corto

- **Estado:** pendiente | en curso | en revisión | aceptada | rechazada
- **Rama:** `codex/t-xxx`
- **Rama base:** `claude/<bloque>`
- **Depende de:** T-YYY | nada
- **Riesgo:** bajo | medio | alto
- **Tipo:** implementación | corrección | refactorización | investigación

## Objetivo
Qué debe existir al terminar y por qué. Si viene del usuario, cita su petición literal.
Si Claude ya ha localizado el código afectado, indica las rutas y líneas aproximadas.

## Contexto y contratos
Interfaces, clases, tablas o funciones existentes que hay que respetar (rutas y firmas).
Formato real de los datos de entrada si los hay (analizado por Claude con los datos reales).

## Decisiones ya cerradas
Numeradas. Codex no las reabre. Incluye invariantes de integridad, seguridad y compatibilidad.

## Autonomía y escalado
Qué puede decidir Codex solo y qué debe elevar a Claude.

## Alcance
### Puedes crear o modificar
### No debes tocar
### Autorizaciones de control reforzado

## Casos límite a cubrir

## Tests obligatorios
Incluye **invariantes** comprobables (p. ej. «saldo final = Σ entradas − Σ salidas»).

## Verificación obligatoria
Comandos exactos. Si Codex no puede ejecutarlos, dilo: «Claude ejecuta …».
Si aplica, el resultado esperado con los datos reales («debe dar 0,08 € y 0.00834125 BTC»).

## Criterios de aceptación
- [ ] …
- [ ] Sin cambios fuera del alcance; tests en verde

## Informe esperado de Codex
`docs/tasks/T-XXX.report.md`, sin preguntas al usuario.

## Revisión de Claude
(pendiente: hallazgos, correcciones, excepciones aplicadas, comandos y resultados, aceptación o rechazo)
```

### 6.4 `docs/tasks/COMMON.md` (contrato común vivo)
Es el archivo que más valor acumula: cada error que Codex repite se convierte aquí en una regla, y todas las fichas lo exigen («`docs/tasks/COMMON.md` obligatorio»).
```markdown
# Contrato común de todas las fichas (léelo entero antes de empezar)

## Entorno y verificación
- SO: {{…}}. Comandos: {{…}} desde la raíz de tu worktree.
- No instales nada ni cambies dependencias salvo que la ficha lo diga.
- Si un comando no puede ejecutarse, copia el error en el informe; no afirmes que funciona.

## Qué ya existe (úsalo, no lo dupliques)
{{utilidades comunes, componentes, helpers de tests}}

## Reglas generales
{{convenciones de estilo, estructura de carpetas, textos, accesibilidad, errores}}

## Lecciones aprendidas (se amplía tras cada revisión)
- {{Lección de T-0XX: síntoma → regla obligatoria}}
```

### 6.5 Estructura de `PROJECT_MEMORY.md`
```markdown
# PROJECT MEMORY — {{PROYECTO}}

## 1. ESTADO ACTUAL
Fase actual · Última tarea completada · Tarea en curso · Próxima tarea · Estado de compilación
· Última prueba · Último artefacto (ruta, tamaño, hash) · Bloqueos

## 2. RESUMEN EJECUTIVO   (entender el estado en menos de un minuto)
## 3. DECISIONES TÉCNICAS  (ID, fecha, decisión, justificación, consecuencias)
## 4. TAREAS PENDIENTES    (ID, objetivo, prioridad, dependencias, estado)
## 5. TAREA ACTUAL         (objetivo, archivos, plan, progreso, última operación, próxima acción exacta)
## 6. HISTORIAL DE TAREAS  (ID, fecha, ficha, rama, resultado de la revisión, verificación, estado final)
## 7. REGISTRO DE ERRORES  (ID, tarea, mensaje, causa, intentos, solución, verificación, estado)
## 8. COMPILACIONES Y PRUEBAS
## 9. DEPENDENCIAS Y CONFIGURACIÓN   (solo nombres de variables de entorno, nunca secretos)
## 10. PROBLEMAS Y BLOQUEOS
## 11. PRÓXIMOS PASOS     (la primera debe poder ejecutarse directamente en la siguiente sesión)
## 12. REGISTRO DE SESIONES
```
Reglas:
- Registra cada tarea **antes** de ejecutarla, con los estados PENDIENTE → EN CURSO → COMPLETADA | BLOQUEADA | INTERRUMPIDA | CANCELADA, y actualízala tras cada avance.
- Los errores usan los estados ABIERTO → EN INVESTIGACIÓN → CORREGIDO | BLOQUEADO.
- Al iniciar una sesión, compara la memoria con el estado real del repositorio (`git status`, `git log`, worktrees) antes de continuar.
- Si crece demasiado, archiva el historial en `project_logs/` y deja un índice.

### 6.6 `scripts/delegate-codex.ps1`
```powershell
param(
  [Parameter(Mandatory = $true)][string]$Task,
  [string]$Slug = "",
  [string]$Base = "main"
)
$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root

$taskFile = "docs/tasks/$Task.md"
if (-not (Test-Path $taskFile)) { throw "No existe la ficha $taskFile" }
if (-not (Get-Command codex -ErrorAction SilentlyContinue)) { throw "Codex CLI no está instalado (npm i -g @openai/codex)" }
git rev-parse --verify $Base *> $null
if ($LASTEXITCODE -ne 0) { throw "La rama base '$Base' no existe." }

$name = $Task.ToLower(); if ($Slug) { $name = "$name-$Slug" }
$branch = "codex/$name"
$worktree = Join-Path $root ".local\worktrees\$name"
if (-not (Test-Path $worktree)) {
  git worktree add -b $branch $worktree $Base
  if ($LASTEXITCODE -ne 0) { throw "No se pudo crear el worktree." }
}

$report = Join-Path $root "docs/tasks/$Task.report.md"
$prompt = @"
Lee AGENTS.md, docs/tasks/COMMON.md y después docs/tasks/$Task.md. Eres el implementador principal de esta tarea: llévala de extremo a extremo dentro del alcance, toma las decisiones locales autorizadas, añade o actualiza las pruebas exigidas, diagnostica y corrige los fallos que pertenezcan a la ficha y ejecuta todas sus verificaciones. Respeta estrictamente los archivos permitidos y las zonas de control reforzado. Claude Code es tu único interlocutor: no hagas preguntas al usuario; eleva cualquier decisión necesaria a Claude en una sección «Preguntas para Claude» y continúa el trabajo independiente. Termina con el informe descrito en la ficha, incluyendo decisiones, riesgos y resultados reales.
"@

Write-Host "Rama: $branch`nWorktree: $worktree`nInforme: $report"
codex exec --cd $worktree --sandbox workspace-write --output-last-message $report $prompt
Write-Host "`nCodex ha terminado. Revisa el diff antes de integrar:`n  git diff $Base...$branch"
```

---

## 7. Lecciones aprendidas (de más de 60 fichas)

**Sobre las fichas**
- **La ficha es el producto de Claude.** Cuanto más concreta (rutas y líneas localizadas, formato real de los datos, decisiones numeradas), mejor sale la entrega a la primera.
- **Analiza los datos reales antes de especificar.** En el importador de Neverless, mirar el CSV reveló que los IDs no eran únicos (depósito y conversión compartían ID). Ese hallazgo fue a la ficha como regla de deduplicación.
- **Cita al usuario literalmente** en el objetivo. Evita malinterpretaciones y, si las hubo, deja claro qué se corrige.
- **Invariantes en vez de ejemplos.** «Saldo final = Σ depósitos + Σ intereses − Σ compras» detecta más errores que un caso concreto.
- **Resultado esperado con datos reales** en la verificación. Claude lo comprueba con una prueba local temporal que se borra y no se commitea.
- **Commitea la ficha antes de lanzar a Codex**: el worktree se crea desde la rama base y Codex solo ve lo commiteado.

**Sobre la revisión**
- Revisa especialmente los archivos **fuera del alcance** del diff. A veces son necesarios (una fachada aditiva, un parámetro opcional), pero hay que justificarlos.
- Errores típicos de Codex cuando no puede compilar: imports que faltan, `when`/`switch` no exhaustivos en archivos fuera de su alcance, una lambda final que se engancha a un parámetro nuevo, llamadas a APIs inexistentes y aserciones mal calculadas. Todos salen al compilar. Claude los corrige como ajuste mínimo y los añade a `COMMON.md`.
- **Ejecuta la suite tres veces** (`--rerun` o equivalente) antes de aceptar. Así aparecen los tests inestables y los que dependen de la fecha: un test con `LocalDate.now().plusDays(1)` falló el último día del mes. Regla: los tests inyectan el reloj y usan una fecha fija.
- **Tests con estado global compartido** (preferencias, singletons, ficheros) contaminan otros tests y bloquean la suite. Deben usar almacenes en memoria propios de cada test.

**Sobre la relación con el usuario**
- Traduce el resultado técnico a lo que cambia para él («en Gestión de categorías verás una sola lista»), no a nombres de clases.
- Cuando no estés de acuerdo o haya un matiz (p. ej. si las devoluciones reducen el gasto de un presupuesto), decide por defecto lo conservador y ofrece la alternativa en una línea.
- Guarda sus preferencias de trabajo como reglas persistentes (memoria de Claude o `CLAUDE.md`): «nunca escribir fuera de la carpeta del proyecto», «la UI la decide…», «máximo un agente de cada tipo»…
- Si el usuario rechaza una propuesta, regístralo para no volver a proponerla.

**Sobre la continuidad**
- La memoria se actualiza **antes** de cada tarea y **después** de cada avance. Si la sesión se corta, la siguiente sabe exactamente dónde retomar.
- Al retomar, compara la memoria con la realidad: una tarea puede haber avanzado en disco sin quedar registrada.

---

## 8. Frases útiles para el usuario

| Quieres… | Dile a Claude |
|---|---|
| Continuar | «continúa con el proyecto (recuerda mandar a trabajar a Codex)» |
| Una funcionalidad nueva | Descríbela como la verías tú; Claude la convierte en ficha |
| Que Claude lo haga él | «esta parte hazla tú directamente» (queda como excepción documentada) |
| Ver cómo va | «¿cómo va?» |
| Publicar en `main` | «fusiona la rama a main» (solo se hace con esta autorización) |
| Parar con seguridad | «tengo que apagar: cierra todo y avísame» (Claude actualiza la memoria antes) |
