# API, validación y errores

- Revisar primero `contratos/openapi-publica.yaml` para rutas públicas y `contratos/eventos.md` para las rutas internas. Actualizar el contrato cuando cambie una interfaz.
- El controlador interpreta entrada HTTP, invoca el servicio y devuelve el resultado. Las reglas de cuentas, saldo, moneda e idempotencia pertenecen al servicio.
- Validar solicitudes en el borde y reforzar en el dominio los invariantes que deben cumplirse aun si el servicio se invoca sin HTTP. Una entrada inválida responde `400`; un ID reutilizado con datos distintos responde `409`.
- Centralizar el mapeo de excepciones HTTP en un `@RestControllerAdvice` por servicio. Usar `ProblemDetail` para respuestas de error nuevas y un `code` estable; no devolver detalles de SQL, trazas de pila ni excepciones internas.
- Un recurso ajeno en la API pública responde igual que uno inexistente (`404`). En H2, obtener el titular del JWT verificado, nunca de una cabecera o cuerpo suministrado por el cliente.
- La respuesta `201` de la API pública significa recurso creado; el estado `RECHAZADA` indica que no hubo movimiento. Contabilidad interna devuelve el resultado del comando, no una promesa de éxito.
- No exponer `/internal/**` por el gateway. Antes de habilitar red entre servicios, añadir autenticación de servicio conforme a `docs/04-seguridad-y-calidad.md`.

La [máquina de estados](../02-estados-y-fallos.md) define los resultados y reintentos; no introducir un nuevo estado sin actualizarla.
