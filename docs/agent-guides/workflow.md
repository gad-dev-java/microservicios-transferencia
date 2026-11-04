# Flujo de trabajo para cambios asistidos por agentes

1. Leer la historia, sus criterios de aceptación, los contratos y las reglas relevantes de `AGENTS.md`.
2. Inspeccionar código y migraciones actuales. Para un cambio con varias decisiones, anotar alcance, motivos, riesgos y pruebas en la historia, ADR o descripción del pull request según corresponda.
3. Cambiar el contrato primero si cambia la interfaz; después implementar, probar y actualizar ejemplos de ejecución.
4. Revisar el diff, ejecutar `clean verify` y `git diff --check`, y explicar cualquier prueba pendiente.
5. Trabajar cada historia en su rama según [08-flujo-git.md](../08-flujo-git.md). El usuario ya autorizó subir las historias completadas a GitHub. El pull request debe resumir historia, pruebas y riesgos; fusionar solo después de revisar el resultado y el pipeline.

No añadir subagentes, archivos de agentes especializados ni un flujo de aprobación adicional por defecto. Si una tarea concreta requiere colaboración paralela, seguir la petición explícita del usuario y mantener una sola fuente de verdad para contratos y decisiones.

Mantener estas instrucciones cortas y acordes al código existente. Revisarlas cuando cambie el stack, una decisión arquitectónica o el hito del backlog; no copiar nuevas plantillas sin evaluar si aplican.
