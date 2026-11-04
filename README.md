# Simulador de transferencias bancarias

Proyecto de portafolio para demostrar backend Java con microservicios en un dominio bancario simulado. El repositorio contiene por ahora el diseño previo al desarrollo; todavía no hay servicios implementados.

- [Arquitectura y conexiones](ARQUITECTURA.md)
- [Diagrama editable en draw.io](ARQUITECTURA.drawio)
- [Entregables y orden de implementación](docs/README.md)
- [Historias de usuario y backlog](docs/07-backlog-de-implementacion.md)
- [Ramas y pull requests por historia](docs/08-flujo-git.md)
- [Contrato OpenAPI público](contratos/openapi-publica.yaml)

El MVP es una transferencia interna en PEN entre cuentas de prueba, con autenticación, evaluación de riesgo, asiento contable atómico e idempotencia. Kafka, Notificaciones y observabilidad completan la siguiente entrega; Redis, MongoDB, Config Server y Kubernetes son ampliaciones posteriores.

## Estado actual: H0

El repositorio ya incluye el POM padre, el módulo inicial de Contabilidad, PostgreSQL en Compose, datos sintéticos y un pipeline básico. El servicio aún no tiene tablas ni lógica de transferencias: eso corresponde a H1.

### Requisitos locales

- JDK 21 en `JAVA_HOME`.
- Docker Desktop activo para iniciar PostgreSQL.
- Maven Wrapper incluido en el repositorio; no hace falta instalar Maven por separado.

### Comandos

```powershell
.\mvnw.cmd verify
docker compose up -d postgres
docker compose ps
```

PostgreSQL queda disponible en `localhost:5432`, con base inicial `contabilidad`, usuario `banco_app` y contraseña local de demostración `local-dev-only`. Las bases `transferencias`, `riesgo` y `notificaciones` se crean al inicializar un volumen nuevo. Puedes cambiar puerto y contraseña con las variables de [.env.example](.env.example). No uses esos valores de demostración en un entorno compartido.

Los CSV de [infra/fixtures](infra/fixtures/README.md) todavía no se cargan en PostgreSQL; H1 añadirá las tablas y la carga de prueba.
