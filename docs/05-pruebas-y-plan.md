# Pruebas y plan de implementación

El detalle de historias de usuario y tareas por hito está en [07-backlog-de-implementacion.md](07-backlog-de-implementacion.md).

## Escenarios de aceptación

| ID | Escenario | Resultado esperado |
| --- | --- | --- |
| P-01 | Transferencia válida de 100.00 PEN | `CONFIRMADA`; origen −100.00, destino +100.00; dos asientos cuya suma es cero |
| P-02 | Saldo insuficiente | `RECHAZADA`; saldos y asientos sin cambios |
| P-03 | Cuenta de origen ajena | `404`; sin evaluación de riesgo ni movimiento |
| P-04 | Cuenta de destino inactiva | `RECHAZADA`; sin movimiento |
| P-05 | Misma clave y mismo cuerpo repetidos 10 veces en paralelo | Un `transferId`, un movimiento |
| P-06 | Misma clave y cuerpo distinto | `409`; sin segundo movimiento |
| P-07 | Dos transferencias concurrentes que exceden juntas el saldo | Una como máximo confirmada; saldo nunca negativo |
| P-08 | Riesgo rechaza o queda inaccesible | `RECHAZADA`; Contabilidad no recibe comando |
| P-09 | Timeout después de que Contabilidad confirma | `PENDIENTE_CONCILIACION` temporal; termina `CONFIRMADA` sin otro débito |
| P-10 | Otro usuario consulta una transferencia | `404` |
| P-11 | Kafka caído tras confirmar, versión de portafolio | Evento pendiente en outbox y publicado luego |
| P-12 | Evento duplicado | Una entrega lógica y una sola proyección de Historial por `eventId` |
| P-13 | Historial detenido durante una confirmación | El saldo se actualiza una vez; la proyección aparece al recuperar el consumidor sin nuevo movimiento |

Se prueban reglas puras con pruebas unitarias y persistencia/concurrencia con pruebas de integración usando PostgreSQL real mediante Testcontainers. En la etapa de eventos se incorpora Kafka de prueba. Referencia: [Spring Boot y Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html).

## Etapas

| Etapa | Trabajo | Condición de salida |
| --- | --- | --- |
| 0. Preparación | Estructura Maven, convenciones, Compose, CI básico y datos sintéticos | El proyecto compila y levanta infraestructura local |
| 1. Núcleo contable | Cuentas, movimientos, dos asientos, bloqueo ordenado, saldo, idempotencia del comando | P-01, P-02 y P-07 pasan con PostgreSQL real |
| 2. MVP seguro | Gateway, Transferencias, Riesgo, Keycloak, contratos públicos, conciliación y circuit breaker | P-01 a P-10 pasan; una demostración completa funciona |
| 3. Eventos | Outbox, relay, Kafka y Notificaciones | P-11 y duplicados de notificación pasan |
| 4. Operación | Logs, métricas, trazas, análisis de dependencias, imágenes y despliegue de prueba en Azure DevOps | Pipeline verde y recorrido demostrable de una transferencia |
| 5. Extensiones | Redis, Historial/MongoDB, Config Server y Kubernetes, en ese orden solo si cada necesidad se demuestra | Cada componente tiene prueba, métrica y explicación de valor; Historial supera P-12 |

## Definición de terminado para cada historia

- Contrato o evento actualizado si cambió la interfaz.
- Migración de base de datos versionada si cambió el esquema.
- Prueba del camino exitoso y del fallo relevante, con foco en reglas y persistencia.
- Logs y métricas útiles, sin información sensible.
- Ejecución local documentada y pipeline verde.

## Primera tarea de programación

Implementar el servicio Contabilidad y su transacción de dos asientos sobre PostgreSQL. El primer resultado observable será una prueba de integración que confirme un movimiento, rechace saldo insuficiente y demuestre que dos solicitudes concurrentes no generan sobregiro.
