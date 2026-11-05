# TEC-22: despliegue de prueba en Minikube

El entorno `banca-demo` reproduce los siete módulos Java y sus dependencias en un clúster local de un nodo. Los manifiestos están en [`infra/k8s`](../infra/k8s). Este despliegue es una demostración, no una configuración de producción de alta disponibilidad.

## Preparación y arranque

Requisitos: Docker Desktop, Minikube en ejecución, `kubectl`, PowerShell 7 e imágenes locales construidas con `docker compose build`. Se recomiendan 6 CPU y 8 GiB para arrancar todos los servicios; la prueba inicial con 2 CPU hizo que el arranque simultáneo fuera muy lento. El script usa explícitamente el contexto `minikube` y no cambia el contexto global de `kubectl`.

Para un perfil nuevo:

```powershell
& 'C:\Program Files\Kubernetes\Minikube\minikube.exe' start --driver=docker --cpus=6 --memory=8192
docker compose build
docker compose stop
pwsh -NoProfile -File infra/k8s/deploy.ps1
kubectl --context minikube -n banca-demo get pods,pvc
```

Minikube no permite cambiar `--cpus` en un perfil ya creado. Si el perfil Docker existente tiene 2 CPU, `docker update --cpus 6 minikube` amplía temporalmente el contenedor en ejecución sin borrar sus PVC; repite el ajuste si el contenedor se recrea. Esta fue la vía usada en la prueba local.

Si Minikube está instalado fuera de `C:\Program Files\Kubernetes\Minikube\minikube.exe`, pasa `-Minikube <ruta>`. Para volver a aplicar los manifiestos con las imágenes ya cargadas, pasa `-SkipImageLoad`. El script toma las credenciales ficticias de `.env.example`, crea un Secret y cuatro ConfigMaps, carga las imágenes desde Docker y espera el estado Ready de los Deployments. No imprime las credenciales. Los archivos de Config Server solo contienen configuración no sensible.

## Acceso a la demo

En dos terminales separadas:

```powershell
kubectl --context minikube -n banca-demo port-forward svc/keycloak 18180:8080
kubectl --context minikube -n banca-demo port-forward svc/gateway 18080:8080
```

La API queda en `http://localhost:18080` y Keycloak en `http://localhost:18180`. Importa la [colección Postman](../contratos/coleccion-demo.postman_collection.json) cambiando las variables `gatewayUrl` e `identityUrl`, o ejecuta el script H2 con `-Api http://localhost:18080 -Identity http://localhost:18180`. El script H2 detiene Riesgo mediante Compose para P-08, por lo que esa parte no representa una caída de Riesgo en Kubernetes. Para ver trazas y métricas puedes abrir port-forwards adicionales a `svc/zipkin` y `svc/prometheus`.

## Datos y salud

PostgreSQL, Keycloak, Kafka y MongoDB usan PVCs `ReadWriteOnce` con la StorageClass predeterminada de Minikube. Sobreviven al reinicio de un Pod en el mismo clúster; la eliminación del clúster o de sus PVCs elimina los datos. Redis guarda cuotas efímeras, Zipkin mantiene trazas en memoria y Prometheus usa `emptyDir`: sus datos no sobreviven a un reemplazo de Pod. Cada servicio Java expone `readiness` y `liveness`; las dependencias tienen sondas apropiadas para el entorno local.

Para diagnosticar:

```powershell
kubectl --context minikube -n banca-demo get pods,pvc
kubectl --context minikube -n banca-demo describe pod <pod>
kubectl --context minikube -n banca-demo logs deployment/transferencias --tail=100
kubectl --context minikube -n banca-demo rollout status deployment/transferencias
```

El Secret usa valores de `.env.example` que son exclusivamente de demo. Un entorno real necesita un gestor de secretos, almacenamiento externo con copias de seguridad, recursos dimensionados, políticas de red, TLS, alta disponibilidad, observabilidad durable y un registro de imágenes. El pipeline Azure queda preparado, pero la publicación de imágenes requiere la conexión de servicio que todavía no existe.

## Evidencia de ejecución

Prueba local del 4 de noviembre de 2026 (zona America/Lima), Minikube con Kubernetes v1.37.0 y driver Docker:

- Los 14 Pods terminaron `1/1 Running`, y los cuatro PVC quedaron `Bound`. Reaplicar `deploy.ps1 -SkipImageLoad` terminó con código 0 sin cambiar recursos.
- Newman contra los dos port-forwards ejecutó 13 solicitudes y 13 aserciones, sin fallos. Se creó una transferencia confirmada de 0,01 PEN; apareció una fila en Historial y un aviso.
- Se reiniciaron los Deployments de PostgreSQL, Keycloak, Kafka y MongoDB. Tras el reemplazo de los Pods, el token volvió a emitirse, la transferencia `6d30359e-be9d-4925-929b-c4dd32493360` conservó estado `CONFIRMADA`, su registro siguió en Historial y el saldo de origen siguió en `3999.99` PEN. El port-forward de Keycloak se abrió de nuevo después del reinicio.
- Se ejecutó Newman otra vez: 13/13 aserciones. Una segunda transferencia confirmada llegó a Historial y Notificaciones, y el saldo final fue `3999.98` PEN. Esto también comprobó el flujo nuevo a través de Kafka después de su reinicio.

Estas cifras pertenecen a datos ficticios y a este clúster local. La prueba de reinicio comprueba persistencia ante reemplazo de Pods, sin demostrar recuperación ante pérdida del nodo o del clúster.
