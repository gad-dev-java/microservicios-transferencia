# Guía para agentes del repositorio

Este repositorio implementa un **simulador** de transferencias internas en PEN. Antes de modificar código, lee el contrato y la historia afectada. Las instrucciones del usuario y las decisiones vigentes del proyecto prevalecen sobre las sugerencias generales de cualquier plantilla.

Esta guía adapta el enfoque de [Agents.md para Java y Spring Boot](https://josealopez.dev/blog/agents-md-java-spring-boot) al stack y al estado de este repositorio.

## Estado y stack reales

- Java 21, Maven Wrapper, Spring Boot 4.1.2, PostgreSQL 17, Flyway, JUnit 5 y Testcontainers.
- Monorepositorio Maven: `pom.xml` raíz agrega un `pom.xml` por servicio. Hoy existe `services/contabilidad`; los demás servicios se incorporan según `docs/07-backlog-de-implementacion.md`.
- Contabilidad usa Spring JDBC y SQL explícito. No introducir JPA, Lombok, MapStruct, Kafka, Redis o Kubernetes por inercia; cada incorporación debe responder al backlog y a una decisión concreta.
- La API pública aún no está implementada. Los endpoints actuales de Contabilidad son internos y se limitan a `127.0.0.1` hasta añadir identidad de servicio en H2.

## Qué leer según el cambio

| Trabajo | Referencias |
| --- | --- |
| Reglas de negocio, estados o contratos | `docs/01-alcance-y-reglas.md`, `docs/02-estados-y-fallos.md`, `contratos/`, `docs/agent-guides/api-and-errors.md` |
| Servicio, persistencia o migración | `docs/03-modelo-de-datos.md`, `docs/adr/`, `docs/agent-guides/java-and-architecture.md` |
| Pruebas o concurrencia | `docs/05-pruebas-y-plan.md`, `docs/agent-guides/testing.md` |
| Logs, métricas, trazas o seguridad | `docs/04-seguridad-y-calidad.md`, `docs/agent-guides/logging-observability.md` |
| Ramas, commits o entrega | `docs/07-backlog-de-implementacion.md`, `docs/08-flujo-git.md`, `docs/agent-guides/workflow.md` |

## Reglas que no deben perderse

- Contabilidad es dueña de cuentas, saldos y asientos. Una transferencia confirmada crea exactamente un movimiento y dos asientos opuestos dentro de una transacción local.
- Bloquear ambas cuentas en orden estable, impedir saldos negativos y persistir un único resultado por `transferId`. Repetir el mismo comando conserva el resultado; cambiar su contenido con el mismo ID produce conflicto.
- Cada servicio es dueño de su base lógica y sus migraciones Flyway. No compartir entidades ni repositorios entre servicios ni crear claves foráneas entre bases.
- Mantener `BigDecimal` y `numeric(19,2)` para importes. No usar `double` ni `float` para dinero.
- No registrar credenciales, tokens, cuerpos completos ni datos personales. La autenticación y autorización previstas para H2 no deben describirse como ya implementadas.
- Las reglas de este archivo orientan cambios futuros: no requieren reestructurar código funcional sin un motivo verificable.

## Comandos desde la raíz

```powershell
.\mvnw.cmd spotless:apply
.\mvnw.cmd clean verify
docker compose up -d postgres
```

`clean verify` ejecuta formato y pruebas. Las pruebas de integración necesitan Docker Desktop. En Linux o CI usa `./mvnw`.
