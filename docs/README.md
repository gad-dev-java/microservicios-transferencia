# Entregables previos al desarrollo

Esta carpeta fija las decisiones necesarias para comenzar el simulador de transferencias. [ARQUITECTURA.md](../ARQUITECTURA.md) y [ARQUITECTURA.drawio](../ARQUITECTURA.drawio) muestran la arquitectura objetivo; los documentos de aquí distinguen el MVP de las ampliaciones.

| Orden | Entregable | Archivo | Decisión principal |
| --- | --- | --- | --- |
| 1 | Alcance y reglas | [01-alcance-y-reglas.md](01-alcance-y-reglas.md) | Transferencias internas simuladas en PEN |
| 2 | Estados y fallos | [02-estados-y-fallos.md](02-estados-y-fallos.md) | Resultado seguro ante reintentos y respuestas inciertas |
| 3 | Modelo de datos | [03-modelo-de-datos.md](03-modelo-de-datos.md) | Contabilidad atómica en PostgreSQL |
| 4 | Contratos | [openapi-publica.yaml](../contratos/openapi-publica.yaml), [eventos.md](../contratos/eventos.md) y [JSON Schema](../contratos/transferencia-confirmada-v1.schema.json) | API pública y evento inicial |
| 5 | Seguridad y calidad | [04-seguridad-y-calidad.md](04-seguridad-y-calidad.md) | Identidad, autorización y objetivos medibles |
| 6 | Pruebas y ejecución | [05-pruebas-y-plan.md](05-pruebas-y-plan.md) | Criterios de aceptación por etapa |
| 7 | Decisiones de arquitectura | [adr](adr) | Motivos y consecuencias de decisiones difíciles de revertir |
| 8 | Stack y repositorio | [06-stack-y-repositorio.md](06-stack-y-repositorio.md) | Herramientas y estructura inicial |
| 9 | Backlog de implementación | [07-backlog-de-implementacion.md](07-backlog-de-implementacion.md) | Historias, tareas, dependencias e hitos |
| 10 | Flujo de Git | [08-flujo-git.md](08-flujo-git.md) | Ramas por historia y pull requests hacia `main` |
| 11 | Guía para agentes | [AGENTS.md](../AGENTS.md), [guías por tema](agent-guides) | Contexto, reglas de código y verificación acordes al stack real |

## Límites del compromiso

- **MVP:** API Gateway, Transferencias, Riesgo, Contabilidad, PostgreSQL, proveedor OIDC, Docker Compose, pruebas y pipeline de CI. No mueve dinero real.
- **Versión de portafolio:** añade outbox, Kafka, Notificaciones, observabilidad y despliegue de prueba.
- **Ampliaciones:** Redis, Historial con MongoDB, Config Server y Kubernetes. Aparecen en el diagrama objetivo, pero no bloquean el MVP.
- La rama de trabajo empieza por una transferencia vertical completa; no se crean todos los servicios vacíos a la vez.

## Listo para programar

Se puede iniciar la implementación cuando el flujo de [estados y fallos](02-estados-y-fallos.md), el [modelo de datos](03-modelo-de-datos.md), el [contrato público](../contratos/openapi-publica.yaml) y los [escenarios de aceptación](05-pruebas-y-plan.md) tengan una versión coherente. Estos documentos se actualizan con nuevas decisiones durante el desarrollo.
