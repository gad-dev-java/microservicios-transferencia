# Java y arquitectura

## Código

- Java 21, UTF-8 y cuatro espacios de indentación. Spotless con Palantir Java Format fija el formato de Java; ejecutar `mvnw.cmd spotless:apply` antes de verificar.
- Usar nombres que expresen la regla de negocio; favorecer métodos breves y retornos tempranos. Evitar capas, interfaces o mappers que solo delegan una línea.
- Usar `record` para mensajes y valores inmutables cuando resulte apropiado. La inyección en producción es por constructor; `@Autowired` en campos se reserva para pruebas.
- Mantener la coordinación del caso de uso y la transacción en `application`. El controlador adapta HTTP y delega; no consulta la base ni decide sobre saldos. Las reglas puras van en `domain`.
- No mezclar `double` o `float` con dinero. Validar precisión y rango antes de persistir `BigDecimal` en `numeric(19,2)`.

## Módulos y persistencia

- El POM raíz agrega módulos; cada servicio declara solo sus dependencias y tiene su propia configuración, migraciones y pruebas.
- Las relaciones entre servicios se expresan por contratos HTTP o eventos, nunca por dependencias a clases de otro servicio.
- Dentro de Contabilidad, usar estas cuatro capas:

| Paquete | Responsabilidad | Dependencias permitidas |
| --- | --- | --- |
| `api` | Rutas, DTO de entrada/salida, traducción de errores HTTP | `application`, `domain` para convertir DTO |
| `application` | Caso de uso, límite transaccional, idempotencia y puerto `AccountingStore` | `domain`, Spring para transacción y métricas |
| `domain` | Cuenta, instrucción validada y reglas de rechazo | Biblioteca estándar de Java |
| `infrastructure/persistence` | Implementación JDBC del puerto, SQL y mapeo de filas | `application`, `domain`, Spring JDBC |

El flujo es `HTTP → api → application → AccountingStore ← infrastructure/persistence`; `application → domain` aplica las reglas. No importar `JdbcAccountingStore` en `application`, ni `JdbcTemplate` en `domain` o `api`. Las respuestas HTTP se construyen en `api`, no se devuelve una fila SQL al cliente.
- En Contabilidad, Spring JDBC permite ver claramente `INSERT ... ON CONFLICT`, `SELECT ... FOR UPDATE` y el orden de las escrituras. Mantener SQL parametrizado; no concatenar valores de entrada en consultas.
- Las migraciones Flyway son acumulativas. Añadir una nueva migración para cambiar un esquema ya aplicado; no editar o borrar una migración publicada.
- Conservar las restricciones de base de datos que protegen integridad aunque la aplicación también valide. La idempotencia y el bloqueo de filas deben seguir probados sobre PostgreSQL real.
- Al añadir otro servicio, conservar las responsabilidades de las capas y adaptar los paquetes a su tamaño. No imponer JPA, entidades, repositorios Spring Data, Lombok ni un par interfaz/implementación por cada servicio. `AccountingStore` sí existe porque separa el caso de uso del SQL.

Decisiones principales: [modelo de datos](../03-modelo-de-datos.md), [ADR de contabilidad](../adr/0001-contabilidad-atomica.md) y [stack](../06-stack-y-repositorio.md).
