# Flujo de Git para las historias

El repositorio remoto es `https://github.com/gad-dev-java/microservicios-transferencia.git`. `main` contiene la base publicada. Cada historia se desarrolla y sube en una rama propia. Durante H2, las ramas HU-02 a HU-07 están apiladas: cada una nace de la historia anterior para mantener el código compilable.

## Nombres de ramas

| Tipo de trabajo | Formato | Ejemplo |
| --- | --- | --- |
| Historia de usuario | `feature/HU-XX-descripcion-corta` | `feature/HU-01-transferencia-interna` |
| Tarea técnica independiente | `chore/TEC-XX-descripcion-corta` | `chore/TEC-04-migraciones-contabilidad` |
| Corrección | `fix/descripcion-corta` | `fix/validacion-saldo` |

Los nombres usan letras minúsculas, números y guiones, sin espacios ni tildes. Las tareas técnicas que son parte de una historia pueden incluirse en la rama de esa historia; no necesitan una rama adicional.

## Ciclo de una historia

1. Crear la rama **antes** de empezar la historia, desde la última historia completada cuando haya dependencias directas.
2. Implementar en cambios pequeños y hacer commits con mensajes que indiquen el resultado.
3. Ejecutar las pruebas y comprobar los criterios de aceptación de la historia.
4. Subir la rama a GitHub. Se puede subir durante el trabajo para respaldo; debe estar subida al terminar.
5. Abrir pull requests en orden; una rama apilada apunta inicialmente a su rama base, y se actualiza la base al fusionar la anterior. Revisar el diff y el pipeline antes de fusionar.
6. Fusionar solo cuando la historia cumpla la [definición de terminado](05-pruebas-y-plan.md). Después, actualizar `main` y eliminar la rama terminada.

Ejemplo para HU-01:

```powershell
git switch main
git pull --ff-only origin main
git switch -c feature/HU-01-transferencia-interna
# implementar y verificar
git add -A
git commit -m "feat(HU-01): registrar transferencia contable"
git push -u origin feature/HU-01-transferencia-interna
```

## Reglas de integración

- No hacer `push --force` sobre `main` ni sobre ramas compartidas.
- No fusionar una historia con pruebas fallidas, contratos desactualizados o migraciones faltantes.
- El pull request debe mencionar los escenarios `P-XX` del [plan de pruebas](05-pruebas-y-plan.md) que cubre.
- No subir `.env`, contraseñas, tokens, bases de datos locales ni carpetas `target/`; [.gitignore](../.gitignore) excluye estos archivos.
- Si una historia resulta demasiado grande para revisar, dividirla en historias más pequeñas antes de programarla.

La configuración de protección de `main` y los controles automáticos del pull request se harán cuando el repositorio remoto tenga el pipeline conectado. El archivo [azure-pipelines.yml](../azure-pipelines.yml) ya define la compilación inicial.
