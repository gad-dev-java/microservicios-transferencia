# Logs y observabilidad

- Registrar el resultado contable después del commit: `transferId`, estado y código de motivo son suficientes. No registrar saldos, identificadores personales, tokens, contraseñas ni cuerpos completos.
- Usar mensajes parametrizados de SLF4J (`{}`), sin concatenación de entradas. Un error se registra una vez en el límite que lo maneja; evitar duplicar logs al lanzar y capturar la misma excepción.
- Mantener etiquetas de métricas de cardinalidad acotada (`outcome`, `reason`). `transferId` pertenece al log o traza, nunca a una etiqueta de métrica.
- Al incorporar trazas en H4, seguir W3C Trace Context y propagar `traceparent` entre servicios. El `traceId` debe provenir de instrumentación confiable; no aceptar un `userId` de una cabecera arbitraria.
- Instrumentar clientes HTTP y tareas asíncronas cuando se agreguen; comprobar que el contexto de traza llegue al trabajo posterior. No afirmar trazabilidad de extremo a extremo antes de probarla.

Objetivos y fase de incorporación: [seguridad y calidad](../04-seguridad-y-calidad.md) y [backlog H4](../07-backlog-de-implementacion.md).
