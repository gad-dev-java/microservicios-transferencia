# Alcance y reglas de negocio

## Objetivo

Demostrar el diseño y la implementación de un backend bancario con microservicios mediante transferencias **internas y simuladas**. Los usuarios y las cuentas son de prueba. El proyecto no procesa pagos reales ni integra un banco.

## MVP

- Consultar las cuentas propias y el saldo disponible.
- Solicitar una transferencia entre dos cuentas activas en **PEN**.
- Consultar una transferencia propia por identificador.
- Evaluar la operación con un servicio de Riesgo de reglas deterministas y configurables.
- Registrar débito y crédito de forma atómica en Contabilidad.
- Repetir una solicitud con la misma clave de idempotencia sin producir otro movimiento.
- Autenticar con OIDC/JWT y autorizar el uso de la cuenta de origen.
- Ejecutar todo localmente con Docker Compose y verificarlo en CI.

No forman parte del MVP: transferencias interbancarias, cambio de moneda, aprobación manual, apertura de cuentas, depósitos, retiros, interfaz web, Kafka, Redis, MongoDB y Kubernetes. Las cuentas y saldos iniciales se cargan como datos sintéticos de prueba.

## Reglas

| ID | Regla |
| --- | --- |
| RN-01 | El importe debe ser mayor que cero y tener como máximo dos decimales. La representación pública es una cadena decimal; en Java se usa `BigDecimal` y en PostgreSQL `numeric(19,2)`. |
| RN-02 | La moneda del MVP es `PEN`. Las dos cuentas deben tener esa moneda y estar activas. |
| RN-03 | La cuenta de origen y la de destino deben ser distintas. |
| RN-04 | La cuenta de origen debe pertenecer al usuario autenticado. La cuenta de destino puede pertenecer a otro usuario de prueba. |
| RN-05 | No se permite que el saldo de origen quede negativo. Las cuentas se bloquean en orden estable durante la transacción para resolver concurrencia. |
| RN-06 | La regla de demostración `demo-v1` aprueba importes de hasta `3000.00` PEN y rechaza importes mayores con `RISK_REJECTED`. La decisión se persiste por `transferId`. Es una regla ficticia y configurable, no una política bancaria real. |
| RN-07 | Si Riesgo no responde dentro del timeout, la operación se rechaza con motivo `RIESGO_NO_DISPONIBLE` y no se llama a Contabilidad. |
| RN-08 | Cada transferencia confirmada produce exactamente dos asientos, uno negativo y uno positivo, cuya suma es cero. No se editan ni eliminan asientos confirmados. |
| RN-09 | `Idempotency-Key` se aplica por usuario. Misma clave y mismo cuerpo devuelven el mismo identificador y estado actual; misma clave con cuerpo diferente devuelve `409`. |
| RN-10 | Si se desconoce el resultado de Contabilidad por un timeout, Transferencias consulta o reintenta con el mismo `transferId`. Mientras tanto muestra `PENDIENTE_CONCILIACION`. |
| RN-11 | Una transferencia confirmada no se revierte modificando asientos. Una eventual devolución sería una nueva operación contable, fuera del MVP. |

El umbral de Riesgo es configuración de datos de prueba, no una política financiera real. Los clientes reciben códigos y motivos definidos en el contrato, nunca detalles internos de excepciones.

La respuesta HTTP `201` significa que se creó el recurso de transferencia, incluso cuando su estado final es `RECHAZADA`; no significa que el dinero se movió. `202` significa que el resultado aún se está conciliando. Una solicitud repetida con la misma clave y cuerpo recibe `200` con el estado actual del mismo recurso.

## Historias de usuario y aceptación

1. **Como titular**, veo mis cuentas y saldos. Una cuenta ajena no aparece y su consulta directa devuelve `404` para no revelar su existencia.
2. **Como titular**, transfiero fondos a otra cuenta activa en PEN. El saldo de origen baja, el de destino sube y la transferencia queda `CONFIRMADA`.
3. **Como titular**, recibo `RECHAZADA` si no tengo fondos, la cuenta está inactiva o Riesgo la rechaza. Los saldos permanecen iguales.
4. **Como cliente API**, puedo repetir una solicitud tras perder la respuesta. La misma clave identifica la misma transferencia y no causa un segundo débito.
5. **Como titular**, consulto el estado de mi transferencia. Un usuario distinto no puede consultarla.

## Supuestos de la demostración

- Una instancia local puede ejecutarse en un equipo de desarrollo; no se pretende certificación bancaria ni disponibilidad de producción.
- Las cuentas y usuarios son ficticios. El README de implementación deberá incluir dos usuarios y al menos tres cuentas de prueba.
- El proveedor de identidad para desarrollo será Keycloak. Los servicios validarán JWT emitidos por ese proveedor.
