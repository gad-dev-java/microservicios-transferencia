# TEC-20. Límite distribuido del Gateway

El Gateway aplica `RequestRateLimiter` de Spring Cloud Gateway a sus tres rutas públicas. Usa el token bucket de Redis, con clave derivada del principal JWT y del identificador de ruta. El valor local predeterminado permite 100 solicitudes por segundo, sin acumulación adicional (`RATE_LIMIT_REPLENISH=100`, `RATE_LIMIT_BURST=100`). Las solicitudes excedentes reciben HTTP `429`; las aceptadas incluyen encabezados `X-RateLimit-*`.

Redis guarda únicamente cuotas efímeras. Cuentas, saldos, asientos e idempotencia permanecen en las bases PostgreSQL de sus servicios. Si Redis no responde, el Gateway no puede otorgar cuota y la solicitud falla; no se permite continuar sin control de límite.

## Verificación con dos instancias

Con Compose levantado y la imagen Gateway construida, ejecuta:

```powershell
docker compose up -d --build
pwsh -NoProfile -File infra/scripts/verify-rate-limit.ps1
pwsh -NoProfile -File infra/scripts/verify-h2.ps1
```

`verify-rate-limit.ps1` inicia dos Gateways temporales en puertos `18085` y `18086`, con la misma instancia Redis y un prefijo de ruta único para la prueba. La cuota de prueba permite dos solicitudes por minuto. Envía solicitudes A/B/A/B con el mismo titular y la misma ruta; el resultado esperado es `200, 200, 429, 429`. El script detiene sus dos contenedores al finalizar. La verificación H2 vuelve a comprobar saldos, concurrencia e idempotencia por la API pública.

Resultado local observado: `TEC-20 OK: Gateway A/B/A/B = 200, 200, 429, 429`; después, P-01 a P-08 y P-10 pasaron en `verify-h2.ps1`.
