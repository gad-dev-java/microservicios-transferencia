# TEC-21. Configuración centralizada no sensible

Config Server sirve archivos del directorio [infra/config-repo](../infra/config-repo), versionado en este repositorio Git. En Compose usa el backend `native` montado en solo lectura y escucha únicamente en la red interna. La revisión declarada `h5-v1` permite identificar la versión que cargó cada cliente.

Riesgo consume el importe máximo de la regla demo y su versión. Transferencias consume el tiempo de espera del circuit breaker. Ambos usan `spring.config.import=optional:configserver:` y conservan valores locales equivalentes para pruebas aisladas. Compose espera a que Config Server esté saludable antes de iniciar esos clientes. Los parámetros se leen al arrancar; para aplicar una revisión nueva se reinician los consumidores. No se implementó actualización dinámica.

```powershell
docker compose up -d --build
docker compose exec -T config-server curl -fsS http://localhost:8888/riesgo/default
docker compose exec -T config-server curl -fsS http://localhost:8888/transferencias/default
docker compose logs riesgo transferencias | Select-String 'revision=h5-v1'
```

En la verificación local, ambos endpoints devolvieron los archivos esperados y los logs de Riesgo y Transferencias indicaron `revision=h5-v1`. La colección Postman completó 13 solicitudes y 13 aserciones con esta configuración.

Este repositorio de configuración **no contiene** contraseñas, tokens, URIs con credenciales ni claves. Las credenciales de PostgreSQL y del cliente de servicio siguen entrando por variables de entorno en Compose; en un clúster irían en `Secret` o un gestor de secretos. El backend `native` está destinado a la demo local. En un despliegue real se configuraría un repositorio Git de configuración propio, control de acceso y una estrategia explícita de versiones y promoción entre ambientes.
