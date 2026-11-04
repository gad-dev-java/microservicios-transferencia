# Seguridad y atributos de calidad

## Seguridad

- **Identidad:** Keycloak local como proveedor OIDC de usuarios de prueba y cuentas de servicio. El usuario presenta JWT al gateway y a los servicios públicos. Se validan firma, emisor, audiencia y expiración.
- **Autorización:** `transferencias:crear` y `transferencias:leer` para clientes; `contabilidad:registrar` y `riesgo:evaluar` para llamadas entre servicios. La cuenta de origen debe pertenecer al `sub` autenticado. Contabilidad comprueba esa relación de nuevo al recibir el comando interno.
- **Aislamiento:** los endpoints `/internal/**` se exponen solo en la red interna de Compose o del clúster y exigen token de servicio. El gateway no los enruta.
- **Consultas privadas:** un identificador de cuenta o transferencia ajena devuelve `404`, igual que uno inexistente. La lista siempre filtra por `sub`.
- **Secretos:** fuera de Git; variables de entorno o gestor de secretos. Config Server, cuando se incorpore, mantiene únicamente configuración no sensible.
- **Transporte y datos:** HTTPS fuera del entorno local; no registrar JWT, contraseñas ni cuerpos completos de solicitudes. Se usan datos sintéticos, sin información personal real.
- **Abuso:** límite de solicitudes en gateway en la etapa Redis; antes, límites simples por instancia y pruebas de entrada. Se valida tamaño de cuerpo y formato de todos los campos.

La autorización por identificador de objeto es un riesgo conocido en APIs; las pruebas de cuenta y transferencia ajenas son obligatorias. Referencia: [OWASP API Security Top 10](https://api-security.owasp.org/editions/2023/en/0x11-t10/).

## Objetivos de calidad del proyecto

| Atributo | Objetivo verificable |
| --- | --- |
| Integridad | Ninguna transferencia confirmada deja solo un asiento o un saldo negativo. |
| Idempotencia | Diez repeticiones concurrentes de una misma clave producen un único `transferId` y un único movimiento contable. |
| Concurrencia | Dos o más transferencias simultáneas desde una cuenta no superan el saldo disponible. |
| Recuperación | Si Kafka está caído después de confirmar, outbox conserva el evento y lo publica al recuperarse. |
| Seguridad | Los casos de usuario sin token, cuenta ajena y transferencia ajena fallan según contrato. |
| Observabilidad | Cada solicitud tiene `traceId`; se pueden seguir Transferencias, Riesgo y Contabilidad en logs y trazas. |
| Rendimiento | En un entorno local de referencia documentado, medir 20 solicitudes por segundo durante 5 minutos y registrar p50/p95, errores y uso de recursos. La línea base se publica antes de fijar un límite de latencia. |

No se declara un SLA de producción para este simulador. Se documentan el equipo de prueba, la cantidad de cuentas, el volumen de datos y el resultado de la medición para que pueda repetirse.

## Operación

- Endpoints de salud separados para *liveness* y *readiness*; Actuator y Micrometer para métricas.
- Logs estructurados con `traceId`, `transferId` y nombre de servicio, sin datos sensibles.
- Métricas mínimas: latencia y tasa de errores HTTP, transferencias por estado, conciliaciones pendientes, tamaño de outbox, antigüedad del evento más antiguo y errores de consumidores.
- Alertas de demostración: conciliación pendiente demasiado tiempo y outbox sin avance. Los umbrales se fijan con mediciones reales de la etapa de operación.
- Dependencias con versiones compatibles y fijadas en el proyecto; el pipeline ejecuta pruebas y revisión de dependencias antes de construir imágenes.

## Fuentes de referencia

- [Spring Security: JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Boot: Observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html)
