# Cobertura inicial del backend

Medición: 14 de octubre de 2026, 12:56 (America/La_Paz).

Commit base: `e67098433fc26362a8a168fb85a057fd8db0c1d8`, con la configuración de JaCoCo agregada en el árbol de trabajo. No se agregaron ni modificaron pruebas ni lógica de negocio.

Entorno: JDK 21.0.2, Maven 3.10.0, JaCoCo 0.8.15.

Comando desde backend: `mvn -B -ntp clean verify`, con JAVA_HOME apuntando al JDK 21.

Resultado: BUILD SUCCESS. 62 pruebas, 0 fallos, 0 errores y 0 omitidas, en 15 clases de pruebas.

| Alcance | Líneas | Ramas |
|---|---|---|
| Backend completo | 546/1120 (48.75%) | 143/480 (29.79%) |
| eventintelligence | 0/3 (0.00%) | No aplica |
| service | 459/627 (73.21%) | 135/247 (54.66%) |
| exception | 50/63 (79.37%) | 8/14 (57.14%) |
| repository | No aplica | No aplica |
| security | 0/220 (0.00%) | 0/183 (0.00%) |
| controller | 27/180 (15.00%) | 0/34 (0.00%) |
| dto | 8/10 (80.00%) | No aplica |
| entity | 2/2 (100.00%) | No aplica |
| config | 0/15 (0.00%) | 0/2 (0.00%) |

## Evidencia

- [Reporte HTML navegable](jacoco/index.html)
- [Datos XML](jacoco/jacoco.xml)
- [Datos CSV](jacoco/jacoco.csv)
- Resultados individuales de pruebas en `surefire/`.

Los archivos de esta carpeta son una copia histórica; no se regeneran al ejecutar Maven. El reporte actual se genera en `backend/target/site/jacoco/`.

## Interpretación y prioridades

No se excluyeron paquetes. JaCoCo puede filtrar código generado automáticamente (por ejemplo, por Lombok); no mide todas las líneas físicas del repositorio. La cobertura corresponde solo al backend y no demuestra la ejecución real de consultas SQL ni el funcionamiento del sistema completo.

La seguridad registra 0 %: los mocks de permisos de las pruebas existentes no ejecutan su implementación. Priorizar pruebas de AccessPolicy, SurveyService, condiciones de AttendeeGuard y caminos alternativos de servicios. Mantener esta línea base antes de ampliar la suite.

## Incidencia de entorno

La primera compilación utilizó Java 27 desde Maven y falló por métodos generados por Lombok no disponibles. Al seleccionar el JDK 21 requerido, compilación y pruebas pasaron sin cambios al código de aplicación.
