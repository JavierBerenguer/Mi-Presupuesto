# T-XXX — Título corto

- **Estado:** pendiente | en curso | en revisión | aceptada | rechazada
- **Rama:** `codex/t-xxx-slug`
- **Rama base:** `main` | `claude/...` | otra
- **Depende de:** T-YYY (o "nada")
- **Riesgo:** bajo | medio | alto
- **Tipo:** implementación | corrección | refactorización | investigación técnica

## Objetivo
Una o dos frases: qué debe existir al terminar y por qué.

## Contexto y contratos
Interfaces, clases o tablas que ya existen y que hay que respetar (rutas y firmas exactas).
Reglas de negocio relevantes (con fórmulas si aplica).

## Decisiones ya cerradas e invariantes
- Decisiones de arquitectura o producto que Codex no debe reabrir.
- Propiedades de integridad, seguridad, privacidad y compatibilidad que deben conservarse.

## Autonomía de Codex
- Decisiones locales que puede tomar sin consultar.
- Decisiones que debe elevar a Claude antes de continuar con la parte afectada.

## Escalado de decisiones
- Codex dirige todas sus preguntas a Claude, nunca al usuario.
- Formato de cada consulta: decisión necesaria, opciones consideradas, recomendación, impacto y trabajo que puede continuar mientras se resuelve.
- Claude responde con una decisión o actualiza la ficha; solo Claude decide si es imprescindible consultar al usuario.

## Alcance
### Archivos que puedes crear o modificar
- `app/src/main/java/.../Ejemplo.kt`

### Archivos que NO debes tocar
- Todo lo demás, en especial `domain/`, migraciones, seguridad y archivos Gradle.

### Autorizaciones de control reforzado
- Ninguna | lista explícita de zonas, archivos y firmas protegidos autorizados.

## Requisitos detallados
1. ...
2. ...

## Casos límite a cubrir
- ...

## Tests obligatorios
- ...

## Verificación obligatoria
- Comandos exactos que Codex debe ejecutar.
- Comprobaciones adicionales para tareas de riesgo alto.

## Criterios de aceptación
- [ ] `./gradlew test` pasa
- [ ] `./gradlew assembleDebug` compila
- [ ] Todo el texto de UI está en `strings.xml` en español
- [ ] Sin cambios fuera del alcance

## Informe esperado de Codex
Resumen, decisiones locales, archivos tocados, comandos ejecutados con resultado real, riesgos, desvíos, cualquier trabajo que no pudo completarse dentro del alcance y una sección «Preguntas para Claude» si hace falta. No incluir preguntas dirigidas al usuario.

## Revisión de Claude
(Se rellena tras revisar el diff completo y repetir las verificaciones críticas: hallazgos, correcciones solicitadas, comandos y resultados independientes, motivo de aceptación o rechazo.)
