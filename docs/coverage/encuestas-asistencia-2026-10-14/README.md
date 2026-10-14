# Evidencia de P2-06 y P2-15

Medición: 14/10/2026, 13:24 (America/La_Paz). Rama: `codex/pruebas-encuestas-asistencia`. Base: `9bc992c`; cambios de esta entrega sobre esa base.

JDK 21.0.2, Maven 3.10.0 y JaCoCo 0.8.15. Comando desde backend: `mvn -B -ntp clean verify`, con JAVA_HOME seleccionado para Java 21. Resultado: BUILD SUCCESS.

75 casos aprobados, 0 fallos, 0 errores, 0 omitidos. Se agregaron 12 métodos y 13 ejecuciones: 9 de encuestas (8 métodos, uno con dos parámetros) y 4 de asistencia.

| Alcance | Métrica | Inicial | Esta entrega | Diferencia |
|---|---|---|---|---|
| Backend | Líneas | 48.75 % | 51.88 % | +3.13 pp |
| Backend | Ramas | 29.79 % | 32.71 % | +2.92 pp |
| Servicios | Líneas | 73.21 % | 78.79 % | +5.58 pp |
| Servicios | Ramas | 54.66 % | 60.32 % | +5.67 pp |

AttendeeGuard: 100 % de líneas (7/7) y ramas (6/6). SurveyService: 94,59 % de líneas (35/37) y 100 % de ramas (12/12). El override validate de SurveyService sigue sin cubrirse.

- [Reporte HTML](jacoco/index.html)
- [Datos XML](jacoco/jacoco.xml)
- [Datos CSV](jacoco/jacoco.csv)
- Resultados de pruebas en `surefire/`.
- Matriz y metodología en `docs/testing/P2-06-P2-15.md` desde la raíz del repositorio.

No se excluyeron paquetes ni se alteró producción. Esta medición es intermedia; la meta del sprint y la cobertura final siguen pendientes. Las 62 pruebas heredadas conservan su alcance original; no se reclasifican automáticamente como unitarias estrictas.
