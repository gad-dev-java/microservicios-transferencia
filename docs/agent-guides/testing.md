# Pruebas y quality gate

- Usar JUnit 5 y AssertJ. Probar comportamiento y resultados observables; evitar pruebas que copien línea por línea la implementación.
- Para las reglas de `domain`, usar pruebas unitarias sin contexto Spring. Para SQL, migraciones, bloqueos e idempotencia, usar Testcontainers con PostgreSQL real. No sustituir esas garantías por H2 en memoria.
- Cubrir el camino exitoso y el fallo relevante de cada historia. Para Contabilidad conservar P-01, P-02, P-04 y P-07, además de reintentos paralelos y reversión ante un fallo SQL.
- Las pruebas concurrentes deben iniciar solicitudes a la vez y verificar saldos, movimiento único y número de asientos después de que todas terminen.
- Cada prueba prepara sus datos sintéticos y puede ejecutarse desde una base vacía; el perfil `local` no es una dependencia de los tests.
- Añadir pruebas de controlador cuando se cambien validación, estados HTTP o forma del error. No crear tests triviales que solo comprueban que Spring arranca.

Antes de un commit que altere código o configuración:

```powershell
.\mvnw.cmd clean verify
git diff --check
```

`verify` ejecuta Spotless y la suite. En CI se usa el mismo ciclo Maven. Si Docker no está disponible, informar explícitamente que las pruebas de integración no se ejecutaron.
