# TEC-18. Prueba de carga local

## Reproducción

Levanta `docker compose up -d --build` y comprueba que Gateway y Keycloak responden. Ejecuta desde la raíz:

```powershell
docker run --rm --network proyecto-microservicios_default -v "${PWD}/infra/load:/scripts" grafana/k6:2.3.0 run --summary-export /scripts/tec18-summary.json -e RUN_ID=tec18 /scripts/transferencias.js
```

El escenario usa `constant-arrival-rate`: 20 iteraciones por segundo durante cinco minutos, 60 VU preasignados y hasta 120 VU. Cada iteración realiza cuatro lecturas de cuentas por cada transferencia de 0,01 PEN. Las transferencias usan claves de idempotencia nuevas. Todas las solicitudes incluyen un JWT de `cliente1`; el token se renueva antes de vencer. La cuenta origen demo debe tener al menos 12 PEN disponibles para unas 1.200 transferencias. El test **modifica los datos locales** y añade eventos, avisos y trazas. Usa un volumen de datos desechable o revisa los saldos antes de repetirlo.

Se considera falla una respuesta distinta de `200` para cuentas o una transferencia distinta de `201` y `CONFIRMADA`. Los umbrales son menos de 1 % de solicitudes HTTP fallidas, p95 bajo 2 segundos y cero iteraciones descartadas. El resumen JSON registra p50, p95, errores, número de iteraciones y solicitudes. `sample-resources.ps1` toma muestras de CPU y memoria de todos los contenedores cada 30 segundos.

## Entorno de la medición

- Fecha: 2026-11-04, hora de Lima.
- Equipo local: 16 procesadores lógicos y 32 GB de RAM física. Docker Desktop informó 15,18 GiB disponibles para los contenedores.
- Docker Engine 29.1.3; `grafana/k6:2.3.0`; PostgreSQL 17, Kafka 4.4.0, Keycloak 26.8.1 y servicios Java 21 / Spring Boot 4.1.2 en Compose.
- Volúmenes locales ya iniciados y servicios calientes. Prometheus activo y trazas muestreadas al 10 %. No se incluyeron tiempos de arranque ni construcción.
- Tráfico por la red interna de Compose hacia `gateway:8080`, con autenticación en Keycloak. No representa latencia de Internet ni un clúster de producción.

## Resultado medido

La ejecución repetida con muestreo de trazas del 10 % terminó con todos los servicios, incluido Zipkin, arriba y saludable.

| Medida | Resultado |
| --- | ---: |
| Iteraciones / tráfico de negocio | 6.000 / 20,00 por segundo; 4.800 lecturas y 1.200 transferencias |
| Solicitudes HTTP totales | 6.120, incluidas 120 obtenciones o renovaciones de token |
| Respuestas inesperadas / errores HTTP | 0 / 0 |
| Iteraciones descartadas | 0 |
| Duración HTTP p50 / p95 / máxima | 7,12 / 34,67 / 1.214,14 ms |
| VU máximos ocupados / preasignados | 6 / 60 |

CPU y memoria son máximos de diez muestras tomadas cada 30 segundos con `docker stats --no-stream`. Un 100 % de CPU equivale aproximadamente a un núcleo lógico; los valores pueden superar 100 %.

| Contenedor | CPU máximo | Memoria máxima |
| --- | ---: | ---: |
| Gateway | 18,70 % | 547,1 MiB |
| Transferencias | 24,63 % | 530,9 MiB |
| Riesgo | 5,42 % | 371,8 MiB |
| Contabilidad | 31,11 % | 461,3 MiB |
| Notificaciones | 4,29 % | 390,7 MiB |
| Kafka | 194,19 % | 815,5 MiB |
| PostgreSQL | 4,80 % | 123,3 MiB |
| Keycloak | 46,82 % | 1.011,0 MiB |
| Zipkin | 3,88 % | 317,1 MiB |
| Prometheus | 0,50 % | 29,5 MiB |

Los archivos `infra/load/tec18-summary.json` y `infra/load/tec18-resources.csv` contienen la salida de k6 y las muestras originales. Estas cifras corresponden a una estación local y no garantizan capacidad de producción. La prueba no cubre picos superiores a 20 iteraciones por segundo, fallos de infraestructura ni una red externa.

La primera ejecución, con trazas al 100 %, completó 6.001 iteraciones sin errores HTTP (p50 7,36 ms; p95 32,7 ms; cero descartadas), pero Zipkin terminó con `OutOfMemoryError: Java heap space` durante la carga. Se conserva `tec18-first-run-summary.json` y `tec18-first-run-resources.csv` como evidencia. El muestreo local se redujo al 10 % y se repite el escenario para comprobar la estabilidad de la observabilidad.
