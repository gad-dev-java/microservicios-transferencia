# Backlog de implementación

Este backlog convierte el [alcance](01-alcance-y-reglas.md), los [contratos](../contratos/openapi-publica.yaml) y las [pruebas de aceptación](05-pruebas-y-plan.md) en trabajo ejecutable. Los identificadores `HU` son historias de usuario; los `TEC` son tareas técnicas necesarias para entregarlas. El orden dentro de cada hito respeta las dependencias, pero cada hito debe terminar con una demostración funcional.

## Mapa de entregas

| Hito | Resultado demostrable | Alcance |
| --- | --- | --- |
| H0. Base reproducible | Proyecto Maven compila y PostgreSQL arranca con Docker Compose | Preparación |
| H1. Núcleo contable | Movimiento atómico, rechazo por saldo e idempotencia probados | Base del MVP |
| H2. MVP seguro | Usuario autenticado crea y consulta transferencias mediante Gateway | MVP completo |
| H3. Eventos | Una transferencia confirmada genera evento y notificación sin perderse ante caída de Kafka | Portafolio |
| H4. Operación | Pipeline, métricas, trazas e imágenes permiten repetir la demostración | Portafolio completo |
| H5. Extensiones | Redis, historial MongoDB, Config Server y Kubernetes, cada uno con una prueba de valor | Arquitectura objetivo |

La **primera entrega utilizable es H2**. H0 y H1 son incrementos internos verificables. Una simulación de transferencia externa con saga se propone al final como ampliación adicional; las transferencias internas no necesitan una saga.

## H0. Base reproducible

| ID | Tarea técnica | Criterio de terminado |
| --- | --- | --- |
| TEC-01 | Inicializar Git, `.gitignore`, estructura de módulos y Maven Wrapper; fijar versiones compatibles de Java, Spring Boot y Spring Cloud | `mvnw.cmd verify` termina correctamente en una instalación limpia |
| TEC-02 | Crear `compose.yaml` con PostgreSQL y bases lógicas por servicio | Una orden levanta PostgreSQL; datos de prueba se pueden cargar de nuevo |
| TEC-03 | Crear pipeline inicial en Azure DevOps para compilar y ejecutar pruebas | Un cambio en el repositorio dispara una compilación con resultado visible |

**Salida:** repositorio reproducible, sin servicios vacíos presentados como terminados.

## H1. Núcleo contable

### HU-01 — Registrar una transferencia interna

**Como titular**, quiero transferir fondos entre cuentas activas para que el movimiento quede registrado de forma consistente.

- Contabilidad valida cuentas, moneda y saldo; una confirmación crea un movimiento y exactamente dos asientos opuestos en una transacción SQL.
- Si el saldo es insuficiente o una cuenta está inactiva, ningún saldo ni asiento cambia.
- Dos solicitudes simultáneas no producen sobregiro. Casos [P-01, P-02, P-04 y P-07](05-pruebas-y-plan.md).

| ID | Tarea técnica | Criterio de terminado |
| --- | --- | --- |
| TEC-04 | Crear migraciones Flyway de `cuentas`, `comandos`, `movimientos` y `asientos`, con índices y restricciones | Esquema se crea desde una base vacía |
| TEC-05 | Implementar transacción contable con bloqueo de cuentas en orden estable | Pruebas de integración con PostgreSQL real pasan |
| TEC-06 | Añadir pruebas de concurrencia e idempotencia contable con Testcontainers | P-01, P-02 y P-07 pasan; repetir `transferId` no crea otro movimiento |

**Salida:** un servicio Contabilidad utilizable por una API interna, con datos de prueba y evidencia de integridad. La ruta pública que completa HU-01 llega en H2.

## H2. MVP seguro

### HU-02 — Autenticarme y usar solo mis cuentas

**Como titular**, quiero identificarme para que nadie pueda consultar mis cuentas ni transferir desde ellas.

- Keycloak emite tokens de prueba; gateway y servicios validan firma, emisor, audiencia y expiración.
- Una cuenta o transferencia ajena responde `404` en la API pública. Casos [P-03 y P-10](05-pruebas-y-plan.md).
- Los endpoints internos requieren identidad de servicio y no se exponen en el gateway.

### HU-03 — Consultar mis cuentas

**Como titular**, quiero ver mis cuentas y saldos para decidir desde cuál transferir.

- `GET /cuentas` devuelve solo cuentas del usuario autenticado; `GET /cuentas/{id}` devuelve `404` si la cuenta es ajena.
- Los importes se muestran como cadenas con dos decimales y moneda `PEN`.
- Datos sintéticos incluyen dos usuarios y al menos tres cuentas.

### HU-04 — Solicitar y consultar una transferencia

**Como titular**, quiero enviar una transferencia y consultar su resultado para saber si fue confirmada o rechazada.

- `POST /transferencias` sigue [OpenAPI](../contratos/openapi-publica.yaml): `201` si hay resultado definitivo, `202` si requiere conciliación y `200` si la clave corresponde a una transferencia existente.
- `GET /transferencias/{id}` y el listado muestran solo transferencias propias.
- Transferencias persiste sus estados según la [máquina de estados](02-estados-y-fallos.md).

### HU-05 — Reintentar sin duplicar el movimiento

**Como cliente de la API**, quiero repetir una solicitud cuando pierdo la respuesta para no pagar dos veces.

- Contabilidad guarda un resultado único por `transferId`, confirmado o rechazado.
- La repetición del comando contable con los mismos datos devuelve el resultado guardado; datos distintos para el mismo ID dan conflicto.
- La API pública aplica `Idempotency-Key` por usuario. Casos [P-05 y P-06](05-pruebas-y-plan.md).

### HU-06 — Recibir una decisión de riesgo segura

**Como titular**, quiero que una operación rechazada por Riesgo no mueva fondos.

- La regla ficticia `demo-v1` aprueba hasta `3000.00` PEN y rechaza importes mayores.
- Un timeout o circuito abierto produce `RECHAZADA` con `RIESGO_NO_DISPONIBLE`; no se llama a Contabilidad. Caso [P-08](05-pruebas-y-plan.md).
- Una evaluación repetida de `transferId` conserva la decisión inicial.

### HU-07 — Recuperar un resultado incierto

**Como titular**, quiero consultar el estado después de una interrupción para no repetir una transferencia a ciegas.

- Si Transferencias pierde la respuesta de Contabilidad, muestra `PENDIENTE_CONCILIACION`.
- Un proceso consulta o reintenta en Contabilidad con el mismo `transferId`, incluso si Transferencias cayó en estado `CONTABILIZANDO`.
- La operación termina con el resultado contable real y sin un segundo débito. Caso [P-09](05-pruebas-y-plan.md).

| ID | Tarea técnica | Criterio de terminado |
| --- | --- | --- |
| TEC-07 | Configurar Keycloak y clientes de usuario/servicio en Compose | Autenticación reproducible con usuarios sintéticos |
| TEC-08 | Implementar Gateway y rutas públicas | El cliente solo usa el puerto del gateway |
| TEC-09 | Crear migraciones y API del servicio Transferencias | Estados, clave de idempotencia y consultas quedan persistidos |
| TEC-10 | Crear servicio Riesgo y persistencia de decisiones | Dos rutas deterministas y un fallo por indisponibilidad probados |
| TEC-11 | Configurar timeouts y circuit breaker de Riesgo | La caída no confirma una transferencia |
| TEC-12 | Ejecutar prueba integral a través del gateway | P-01 a P-10 pasan y el README muestra cómo reproducirlos |

**Salida:** MVP completo, seguro y demostrable en local.

## H3. Eventos y notificaciones

### HU-08 — Recibir una notificación simulada

**Como titular**, quiero un aviso cuando una transferencia se confirma para conocer el resultado sin bloquear la operación contable.

- Solo `CONFIRMADA` genera `TransferenciaConfirmada` versión 1 según el [contrato de eventos](../contratos/eventos.md).
- Una caída de Kafka conserva el evento en outbox; al recuperarse, se publica. Caso [P-11](05-pruebas-y-plan.md).
- Una entrega repetida de Kafka produce una sola notificación lógica. Caso [P-12](05-pruebas-y-plan.md).

| ID | Tarea técnica | Criterio de terminado |
| --- | --- | --- |
| TEC-13 | Migración `outbox` y escritura en la transacción contable | Asientos y evento pendiente se confirman o revierten juntos |
| TEC-14 | Relay de outbox, Kafka en Compose y métricas de atraso | Evento pendiente se publica tras recuperar Kafka |
| TEC-15 | Servicio Notificaciones con tabla `entregas` e idempotencia por `eventId` | Duplicados no crean dos entregas lógicas |

**Salida:** transferencia confirmada → evento → aviso simulado, incluso tras fallos del broker.

## H4. Operación y entrega

| ID | Tarea técnica | Criterio de terminado |
| --- | --- | --- |
| TEC-16 | Añadir Actuator, métricas, logs con `traceId` y trazas entre servicios | Una transferencia se puede seguir de extremo a extremo sin exponer datos sensibles |
| TEC-17 | Completar Azure DevOps: pruebas, análisis de dependencias e imágenes Docker | Pipeline verde publica imágenes versionadas |
| TEC-18 | Prueba de carga reproducible y documentación de resultados | Se publican entorno, datos, p50/p95, errores y recursos utilizados |
| TEC-19 | README de demostración y colección de solicitudes | Otra persona levanta el proyecto y ejecuta casos feliz y fallidos |

**Salida:** portafolio completo con evidencias técnicas, no solo servicios que arrancan.

## H5. Extensiones de la arquitectura objetivo

Se implementan en este orden; cada una conserva la integridad del MVP y actualiza contratos, pruebas y diagramas si agrega conexiones.

| ID | Historia o tarea | Criterio de terminado |
| --- | --- | --- |
| TEC-20 | Redis en el gateway para límite distribuido de solicitudes | Se puede demostrar el límite desde dos instancias; saldos e idempotencia siguen en PostgreSQL |
| HU-09 | **Como titular**, quiero buscar mi historial de transferencias para revisar operaciones anteriores | Historial consume Kafka, construye una proyección MongoDB, filtra por titular y deduplica `eventId`; su retraso no cambia saldos |
| TEC-21 | Config Server para configuración no sensible | Dos servicios consumen configuración versionada; secretos siguen fuera del repositorio |
| TEC-22 | Kubernetes en un clúster de prueba | Despliegue, servicios, configuración, salud y reinicio probados; datos persistentes se gestionan explícitamente |

**Salida:** todos los componentes del diagrama objetivo tienen un caso de uso y una prueba demostrable.

## Ampliación adicional: saga de transferencia externa simulada

Esta ampliación exige actualizar primero arquitectura, reglas, API y estados. Añadiría un procesador externo ficticio y una saga con **reserva de fondos → respuesta externa → confirmación o liberación**. La compensación se aplica a la reserva antes de la liquidación; una transferencia ya liquidada se corrige con un nuevo movimiento trazable. Se probarían timeout, respuesta tardía, duplicados y compensación. No forma parte del MVP ni de H5.

## Cómo ejecutar el backlog

- Trabajar una historia o tarea a la vez; cada cambio debe dejar el repositorio compilable.
- Referenciar en cada cambio el ID `HU` o `TEC` y los escenarios `P` afectados.
- Aplicar la [definición de terminado](05-pruebas-y-plan.md) a cada historia.
- No avanzar de hito hasta cumplir su salida demostrable. Las versiones de parche y umbrales operativos se fijan con pruebas reales al comenzar la implementación.
