# Stack y estructura propuesta

## Decisiones de tecnología

| Área | Elección | Motivo |
| --- | --- | --- |
| Lenguaje y build | Java 21, Maven con Wrapper | Base reproducible para el proyecto |
| Aplicaciones | Spring Boot 4.1.2, Spring Cloud 2025.1.4 | Gateway, configuración y resiliencia dentro del ecosistema Spring |
| Datos transaccionales | PostgreSQL, Spring JDBC en Contabilidad y Flyway | SQL explícito para el bloqueo y los asientos; migraciones versionadas |
| Seguridad | Spring Security Resource Server y Keycloak local | OIDC/JWT para usuarios y servicios |
| Resiliencia | Resilience4j, timeouts y reintentos acotados | Fallos explícitos de llamadas síncronas |
| Contrato HTTP | OpenAPI 3.0.3 | Contrato independiente de la implementación |
| Pruebas | JUnit 5 y Testcontainers | PostgreSQL y Kafka reales durante integración |
| Desarrollo local | Docker Compose | Servicios e infraestructura reproducibles |
| CI/CD | Azure DevOps Pipelines | Compilación, pruebas, análisis y publicación de imágenes |
| Eventos, segunda entrega | Kafka y outbox | Distribución de hechos confirmados |
| Observabilidad, segunda entrega | Actuator, Micrometer y trazas OpenTelemetry | Métricas y seguimiento entre servicios |
| Ampliaciones | Redis, MongoDB, Config Server y Kubernetes | Casos definidos en el plan de implementación |

La versión de parche de Spring Boot, Spring Cloud y cada dependencia se fijará en el primer `pom.xml` y se verificará contra la [matriz oficial de compatibilidad](https://spring.io/projects/spring-cloud/) antes de agregar starters. No se mezclan versiones por intuición.

## Estructura al comenzar la implementación

```text
proyecto-microservicios/
├─ pom.xml                    # POM padre y agregador; packaging=pom
├─ mvnw / mvnw.cmd            # Maven Wrapper
├─ .mvn/wrapper/
├─ compose.yaml               # Infraestructura local
├─ README.md
├─ ARQUITECTURA.md
├─ ARQUITECTURA.drawio
├─ docs/
│  └─ adr/
├─ contratos/
│  ├─ openapi-publica.yaml
│  └─ eventos.md
├─ services/
│  ├─ gateway/
│  │  ├─ pom.xml
│  │  ├─ Dockerfile
│  │  └─ src/main/ y src/test/
│  ├─ transferencias/
│  │  ├─ pom.xml
│  │  ├─ Dockerfile
│  │  └─ src/main/ y src/test/
│  ├─ riesgo/
│  │  ├─ pom.xml
│  │  ├─ Dockerfile
│  │  └─ src/main/ y src/test/
│  ├─ contabilidad/
│  │  ├─ pom.xml
│  │  ├─ Dockerfile
│  │  ├─ src/main/resources/db/migration/
│  │  └─ src/main/java/ y src/test/java/
│  └─ notificaciones/       # se agrega en la segunda entrega
├─ infra/
│  ├─ postgres/
│  ├─ fixtures/
│  ├─ keycloak/              # se agrega en H2
│  └─ observability/       # segunda entrega
└─ azure-pipelines.yml
```

Se mantendrá un monorepositorio para que contratos, migraciones, configuración local y servicios evolucionen juntos. Cada servicio conserva su propio módulo, base lógica y migraciones. El pipeline puede ejecutar pruebas por módulo y una prueba de integración del flujo completo. En H0 solo existe el módulo `contabilidad`; las otras carpetas de servicios se incorporan en sus hitos.

## Organización de Maven

Habrá **un `pom.xml` raíz y uno por servicio**. El raíz tendrá `packaging=pom`, declarará solo los módulos que ya existan y centralizará versión de Java, BOM de Spring Boot/Spring Cloud y configuración común de plugins. No genera una aplicación ejecutable. Cada POM de servicio declarará sus dependencias específicas, tests y `spring-boot-maven-plugin`, y producirá su propio JAR e imagen Docker. Maven permite combinar herencia y agregación en este esquema; su *reactor* construye los módulos seleccionados y sus dependencias. [Guía oficial de Maven](https://maven.apache.org/guides/mini/guide-multiple-modules.html)

Los servicios **no dependerán entre sí como módulos Java** ni compartirán entidades JPA o repositorios. Se comunicarán mediante los contratos HTTP y de eventos de `contratos/`. Esto preserva su despliegue independiente aunque vivan en un único repositorio.

Comandos previstos desde la raíz, una vez creado el código:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -pl :contabilidad -am test
```

`-pl` selecciona el servicio y `-am` incluye cualquier módulo Maven del que dependa. El `Dockerfile` de cada servicio usará el repositorio como contexto de construcción para poder acceder al POM padre. [Spring Boot Maven Plugin](https://docs.spring.io/spring-boot/maven-plugin/)
