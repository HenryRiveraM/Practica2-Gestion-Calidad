# Pruebas y cobertura del backend

## Herramientas

Las pruebas existentes usan JUnit Jupiter y Mockito, incluidos por
`spring-boot-starter-test`. JaCoCo 0.8.15 registra qué código Java ejecutan
estas pruebas. No mide el frontend ni valida por sí mismo consultas SQL reales.

## Ejecutar y generar cobertura

Requisitos: JDK 21 y Maven 3.9 o superior. La primera ejecución necesita
acceso a Internet para descargar las dependencias.

Desde la raíz del repositorio:

```bash
cd backend
mvn clean verify
```

En macOS, si `mvn -version` muestra otro JDK, seleccionar Java 21 antes:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn -version
mvn clean verify
```

`clean` elimina resultados anteriores. JaCoCo instrumenta las pruebas y genera
el reporte en la fase `verify`, siempre que las pruebas terminen correctamente.
Las pruebas actuales usan dobles de repositorios y no necesitan levantar la
aplicación, PostgreSQL o configurar una clave de Gemini.

## Consultar resultados

- `target/surefire-reports/`: resultados de JUnit por clase, con errores y fallos.
- `target/site/jacoco/index.html`: reporte navegable por paquete, clase y método.
- `target/site/jacoco/jacoco.xml`: cobertura para procesamiento automático.
- `target/site/jacoco/jacoco.csv`: cobertura para hojas de cálculo.

En el HTML, las líneas verdes se ejecutaron, las rojas no y las amarillas tienen
ramas parcialmente cubiertas. Una rama corresponde, por ejemplo, a cada resultado
posible de una condición. Algunas líneas no contienen instrucciones ejecutables.

No se excluyen paquetes del reporte ni se exige todavía un porcentaje mínimo:
el objetivo inicial es medir honestamente el estado de todo el backend.
La cobertura alta no sustituye las aserciones ni las pruebas de integración.

## Si hay pruebas fallidas

El comando debe conservar su estado de error. Revisar primero los archivos
de Surefire. Para obtener cobertura diagnóstica de las pruebas que alcanzaron
a ejecutarse, sin volver a ejecutarlas:

```bash
mvn jacoco:report
```

Este reporte no equivale a una ejecución exitosa. Si no hubo datos de ejecución
(por ejemplo, por errores de compilación), no se puede generar cobertura válida.

## Conservar la línea base

Antes de ampliar las pruebas, conservar una copia de `target/site/jacoco/`
y `target/surefire-reports/` fuera de `target/`, que se borra con `clean`.
Registrar fecha, commit, comando, pruebas ejecutadas, fallos y cobertura de
líneas y ramas. Comparar la medición final usando los mismos criterios.

Documentación oficial: https://www.jacoco.org/jacoco/trunk/doc/maven.html
