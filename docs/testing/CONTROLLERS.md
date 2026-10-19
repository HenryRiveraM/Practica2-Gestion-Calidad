# Pruebas del paquete controller

Medición del 19/10/2026. Meta: al menos 80 % de líneas y ramas del paquete
`com.cocacola.eventintelligence.controller`, sin excluir clases.

## Resultado

| Métrica | Antes (snapshot P2-16) | Después | Diferencia |
|---|---|---|---|
| Líneas del paquete | 27/180 (15 %) | 159/180 (88,33 %) | +73,33 puntos |
| Ramas del paquete | 0/34 (0 %) | 34/34 (100 %) | +100 puntos |
| Ejecuciones de toda la suite | 103 | 160 | +57 |

Resultado Maven: BUILD SUCCESS; 160 pruebas, 0 fallos, 0 errores, 0 omitidas.
Las pruebas nuevas son unitarias con llamadas directas al controlador. Las
pruebas previas con MockMvc mantienen su propio alcance y no se reclasifican.

## Distribución de nuevas ejecuciones

| Archivo | Ejecuciones |
|---|---|
| AnalyticsControllerTest | 18 |
| EventAccessControllerTest | 10 |
| AuthControllerTest | 7 |
| PublicEventControllerTest | 6 |
| CrudControllerTest | 7 |
| OperationalCrudControllerTest | 9 |

Los parámetros representan escenarios distintos: alcance sin restricción,
vacío/asignado; roles; estados de eventos; endpoints de analítica. Se distingue
el número de métodos Java del número de ejecuciones Surefire.

## Casos y trazabilidad

| Grupo | Decisiones y contratos verificados |
|---|---|
| Analytics | Fechas invertidas, iguales y ausentes; filtro de evento y permiso denegado; alcance null/vacío/asignado; detalle existente/inexistente; consentimiento de marketing; delegación de puntaje; resultados de campañas, actividades, productos y recurrencia |
| EventAccess | Configuración ausente; estado habilitable o cerrado; bandera enabled; rechazo sin reactivación explícita; reactivación con auditoría; publicación y despublicación |
| Auth | Token CSRF y cabecera; estado de setup; perfil; límite de solicitudes antes de setup/activación; cambio de rol permitido y rechazado sin crear sesión |
| PublicEvent | Lista pública; enlace válido/inválido; mapeo de inscripción y datos de participante; duplicado a 409; enlace inválido sin persistir |
| CRUD | Filtrado de lectura; lectura individual; permiso antes de crear, actualizar y borrar; comprobación de entidad anterior y nueva; denegación sin persistir; permisos de insights |
| Actividades/encuestas | Filtrado de listas, lectura, actualización y borrado; autorización de evento/participante antes de registrar actividad |

## FIRST y límites

Mockito crea dependencias nuevas por prueba y los fixtures no se comparten.
Las sesiones usan MockHttpServletRequest por prueba. No se modifica
SecurityContext ni se utilizan PostgreSQL, red, Gemini, reloj o esperas. Las
aserciones comprueban datos, errores, orden de autorizaciones y ausencia de
persistencia al denegar acceso. Se corrigieron expectativas de pruebas sobre
el valor por defecto de colecciones en Mockito antes de validar la suite final.

No se prueba SQL contra una base real ni filtros de Spring Security, rutas HTTP,
CSRF real, serialización JSON o Bean Validation: invocar métodos directamente
no ejecuta esas capas. El mock de AccessPolicy verifica que el controlador
solicita autorización, no la implementación de esa autorización.

El 100 % de ramas corresponde al paquete medido por JaCoCo, no implica todos
los caminos de excepción ni todas las combinaciones posibles. Quedan 21 líneas
sin cubrir: UserAdminController, sus records y constructores de controladores
CRUD específicos. Aunque la base CRUD está probada, estos controladores
concretos conservan 0 % en sus propias clases; no se afirma 80 % por clase.

## Reproducción y evidencia

Desde backend, con JAVA_HOME apuntando a JDK 21:

```bash
mvn -B -ntp clean verify
```

Reporte actual: `backend/target/site/jacoco/index.html`.
Evidencia conservada: `docs/coverage/controllers-2026-10-19/` (HTML, XML, CSV y
Surefire). Comparación inicial: `docs/coverage/p2-16-2026-10-19/`.
Las pruebas están listas para revisión de un compañero; no se cambiaron fuentes
de producción ni se impusieron umbrales globales a otros paquetes.
