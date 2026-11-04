# ADR 0003: incorporación gradual de infraestructura

- Estado: aceptada.
- Contexto: la arquitectura objetivo incluye Gateway, Riesgo, Kafka, Redis, MongoDB, Config Server y Kubernetes; la oferta laboral valora varios de estos patrones.
- Decisión: el MVP incluye Gateway, Transferencias, Riesgo, Contabilidad, PostgreSQL, Keycloak, Compose y CI. Kafka y Notificaciones forman la siguiente entrega. Redis, MongoDB, Config Server y Kubernetes se implementan después de demostrar un caso de uso concreto para cada uno.
- Alternativas consideradas: crear todos los componentes y despliegues desde el primer día; mantener un monolito como producto final.
- Motivo: una primera transferencia completa permite validar reglas e integridad antes de sumar complejidad operativa. La solución final sigue demostrando microservicios y patrones de la oferta.
- Consecuencias: el diagrama objetivo muestra componentes todavía no implementados; cada hito debe indicar qué está realmente disponible.
- Verificación: condiciones de salida de [05-pruebas-y-plan.md](../05-pruebas-y-plan.md).
