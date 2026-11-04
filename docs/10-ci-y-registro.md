# CI e imágenes versionadas

`azure-pipelines.yml` ejecuta Java 21, `mvn verify`, inventario Maven y validación de Compose. Publica informes JUnit. Si se configura el secreto `NVD_API_KEY`, ejecuta OWASP Dependency-Check y falla ante CVSS 9 o superior. Dependabot revisa versiones Maven e imagen base cada semana.

La etapa `Images` construye y publica Gateway, Transferencias, Riesgo, Contabilidad, Notificaciones e Historial con dos etiquetas: SHA del commit y número de ejecución. Solo corre en `main` y cuando `dockerRegistryServiceConnection` tiene el nombre de una conexión de Docker Registry autorizada. Cambia también `imageNamespace` si el registro requiere otro prefijo.

Los parámetros `APP_MODULE` del Dockerfile se suministran en el paso `Docker@2 build`; el paso `push` publica las etiquetas ya construidas. El repositorio no contiene credenciales del registro ni la clave NVD.

Para terminar la activación en Azure DevOps hacen falta un proyecto conectado a este repositorio, una conexión de servicio de Docker Registry y la variable secreta `NVD_API_KEY`. Hasta configurarlos, la etapa de imágenes y el análisis CVE se omiten; no se debe declarar una publicación remota como exitosa.
