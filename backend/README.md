# Coca-Cola Event Intelligence API

API REST para gestionar campañas, eventos, participantes, productos, actividades, asistencias y analítica de KPIs para una demo de hackathon.

## Requisitos

- Java 21+
- PostgreSQL 13+
- Maven 3.9+

## Inicio rápido con Docker

Desde la raíz del repositorio, con Docker Desktop activo:

```bash
docker compose up -d postgres
cd backend
DB_PASSWORD=postgres mvn spring-boot:run
```

En Windows PowerShell, usa `$env:DB_PASSWORD = "postgres"` y después
`mvn spring-boot:run` desde `backend`.

En otra terminal, inicia el frontend con `cd frontend && pnpm dev` y abre
`http://localhost:5173`. Compose crea una base `cocacola` aislada en un volumen
propio e inicializa el esquema, vistas, migraciones y datos de demostración.
Al arrancar el backend, Flyway también crea las tres cuentas de demostración
(`admin@demo.local`, `organizador@demo.local`, `marketing@demo.local`) si el
evento semilla está presente y esos correos todavía no existen. Usa las
contraseñas compartidas por el equipo; no están guardadas en texto plano en el
repositorio.
No hace falta instalar PostgreSQL en Windows o macOS. Para detener PostgreSQL
sin borrar los datos: `docker compose stop`.

## Base de datos

1. Alternativamente, crear una base de datos PostgreSQL local:

```bash
createdb cocacola
```

2. Si usas PostgreSQL local, aplica los SQL incluidos en `../database/init/` en
orden numérico (`00-schema.sql` hasta `07-registration-backfill.sql`).

> El orden es obligatorio porque las vistas dependen de las tablas y los datos semilla usan los IDs generados por la base. Configura `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` para conectar el backend a esa instalación.
> `00-schema.sql` elimina las tablas existentes con `CASCADE`; ejecútalo solo sobre una base vacía de demo, nunca sobre una base que ya contiene datos del proyecto.

## Configuración

Para desarrollo local y despliegues, usa variables de entorno; no guardes contraseñas en archivos versionados:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/cocacola"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "<contraseña-local>"
$env:CORS_ALLOWED_ORIGIN = "http://localhost:5173"
$env:GEMINI_API_KEY = "<clave-de-Gemini>"
```

En producción, `DB_PASSWORD` es obligatorio y debe proceder de un gestor de secretos o variable protegida del entorno. Los archivos `application.properties` y `application.yml` están versionados y solo contienen referencias a variables de entorno; nunca escribas secretos en ellos.

Configura la conexión mediante las variables de entorno documentadas arriba. `GEMINI_API_KEY` es necesaria para generar insights; solicítala de forma segura y no la escribas en archivos versionados.

## Ejecutar

```bash
cd backend
DB_PASSWORD=postgres mvn spring-boot:run
```

La API queda disponible en:

```text
http://localhost:8080
```

## Endpoints principales

### Campañas

```http
GET    /api/campaigns
GET    /api/campaigns/{id}
POST   /api/campaigns
PUT    /api/campaigns/{id}
DELETE /api/campaigns/{id}
```

### Eventos

```http
GET    /api/events
GET    /api/events/{id}
POST   /api/events
PUT    /api/events/{id}
DELETE /api/events/{id}
```

### Participantes

```http
GET    /api/participants
GET    /api/participants/{id}
POST   /api/participants
PUT    /api/participants/{id}
DELETE /api/participants/{id}
```

### Registro de participantes

```http
POST /api/registrations
Content-Type: application/json
```

El cuerpo requiere `eventId`, `firstName`, `lastName` y un `email` válido. `phone` es opcional. El backend reutiliza participantes con el mismo email y devuelve `409 Conflict` si ya están registrados en ese evento. El token de registro lo genera el backend y se devuelve en la respuesta; no debe generarlo el cliente.

Antes de desplegar esta versión, aplicar `backend/src/main/resources/db/registration-schema.sql` a la base existente para crear la tabla y sus restricciones únicas.

### Check-in con token de registro

```http
POST /api/attendances/check-in
Content-Type: application/json
```

Enviar `{"qrToken":"<token de una inscripción>"}`. El backend valida el UUID y la inscripción, crea o actualiza la asistencia y registra `CHECKED_IN` con la hora UTC del servidor. Un token vacío responde `400`, uno inválido o sin inscripción `404` y un check-in ya realizado `409`. Aplicar también `backend/src/main/resources/db/check-in-schema.sql` a la base existente.

### Actividades y encuestas

`POST /api/activities` registra una actividad o sampling con `eventId`, `type`, `quantity`, `result` y `occurredAt` (ISO 8601 con zona horaria); `participantId` es opcional. La cantidad se admite desde cero.

`POST /api/surveys` recibe `eventId`, `participantId`, `rating` (1â€“5), `nps` (0â€“10) y `comment` opcional. El participante debe haber realizado check-in en ese evento y solo puede enviar una encuesta por evento. El segundo envío responde `409 Conflict`.

Aplicar `backend/src/main/resources/db/activities-surveys-schema.sql` a la misma base de datos indicada por `DB_URL` (por defecto `cocacola`) para guardar el participante/cantidad/resultado de actividades y asegurar la unicidad de encuestas. Desde la carpeta `backend`, ejecutar:

```bash
psql -U postgres -d cocacola -f src/main/resources/db/activities-surveys-schema.sql
```

El proyecto usa `spring.jpa.hibernate.ddl-auto=none`: Hibernate no crea, valida ni modifica el esquema. Los scripts históricos del esquema deben existir antes de iniciar; las nuevas migraciones de acceso en `db/migration/` se aplican automáticamente mediante Flyway.

### Analytics overview

`GET /api/analytics/overview` acepta los filtros opcionales `eventId`, `campaignId`, `startDate` y `endDate` (fechas inclusivas ISO `YYYY-MM-DD`). Los filtros se resuelven en SQL sobre los eventos antes de agregar los indicadores.

- `registered`: inscripciones de `registrations` en los eventos filtrados.
- `attendees`: filas `attendances` con estado `CHECKED_IN`; `attendance_rate` es asistentes / registrados Ã— 100.
- `conversions`: filas de `conversions`; `conversion_rate` es conversiones / asistentes Ã— 100.
- `new_participants` y `recurrent_participants`: personas únicas según `participant_id` de registrations filtradas. Una persona es recurrente si una inscripción o asistencia no cancelada suya pertenece a un evento con fecha anterior a su primera inscripción en el período.
- `nps`: porcentaje de promotores (9â€“10) menos detractores (0â€“6) sobre encuestas filtradas. `average_rating` promedia `surveys.overall_satisfaction`.
- `peakAttendanceHours`: conteo de check-ins agrupados por hora local de La Paz (`attendances.check_in_at`).

Las tasas se devuelven como `null` si el denominador es cero; NPS y rating son `null` cuando no existen encuestas.

### Event Impact Score

`GET /api/analytics/events/{id}/impact-score` devuelve el puntaje y el detalle de un evento existente. Cada métrica se expresa en escala 0â€“100:

- asistencia = asistentes con `CHECKED_IN` / inscripciones Ã— 100;
- engagement = participantes únicos con interacción o interacción de producto / asistentes Ã— 100;
- satisfacción = promedio de `overall_satisfaction` / 5 Ã— 100;
- conversión = participantes únicos con conversión / asistentes Ã— 100;
- recurrencia = participantes inscritos con una participación en un evento de fecha anterior / inscripciones Ã— 100.

Los pesos base viven juntos en `EventImpactScoreWeights`: asistencia 30%, engagement 20%, satisfacción 20%, conversión 20% y recurrencia 10%. El score es el promedio ponderado de las métricas disponibles; los pesos disponibles se renormalizan para sumar 100%. Si no existe ninguna métrica calculable, `score` es `null` y `status` es `INSUFFICIENT_DATA`. Si solo algunas métricas están disponibles, `status` es `PARTIAL_DATA`; si están todas, es `CALCULATED`. La respuesta lista métricas faltantes, pesos base, pesos aplicados y definición de cada métrica. No se imputan valores.

### Insights del evento

`GET /api/events/{id}/insights` usa métricas reales del evento y, solo cuando el usuario solicita generarlas, las envía en un contexto JSON compacto a Gemini 2.5 Flash-Lite. No se envían datos personales ni se calculan métricas en el modelo. La respuesta contiene `eventId`, `generatedByAI`, `provider`, `model`, `summary`, `insights`, `anomalies` y `recommendations`. `generatedByAI` solo es `true` cuando la llamada al proveedor y la validación de la respuesta terminan correctamente. Las cifras devueltas se validan contra el contexto enviado; una cifra no presente se rechaza como error del proveedor.

Configura `GEMINI_API_KEY` como variable de entorno del proceso backend antes de iniciarlo. Sin ella, el endpoint devuelve `status: NOT_CONFIGURED`, `generatedByAI: false` y listas vacías; no genera una respuesta simulada. Si el evento no tiene datos analizables, responde `INSUFFICIENT_DATA` sin invocar Gemini. Los errores del proveedor responden `502 Bad Gateway`.

### Reporte de evento

`GET /api/reports/events/{id}` descarga un CSV UTF-8 con datos reales obtenidos de `AnalyticsOverviewService` y `EventImpactScoreService`: datos del evento, registros, asistencia, conversiones, rating, NPS, participantes nuevos/recurrentes y Event Impact Score. Un evento inexistente responde `404`; las métricas no disponibles se muestran como `Sin datos`.

### Productos y actividades

```http
GET    /api/products
GET    /api/activities
GET    /api/event-products
```

### Asistencias y acciones del evento

```http
GET    /api/attendances
POST   /api/attendances
GET    /api/interactions
POST   /api/interactions
GET    /api/product-interactions
POST   /api/product-interactions
GET    /api/surveys
POST   /api/surveys
GET    /api/conversions
POST   /api/conversions
```

> La lógica de negocio exige `CHECKED_IN` antes de permitir interacciones, encuestas, product interactions y conversiones.

### Analytics

```http
GET /api/analytics/overview
GET /api/analytics/events
GET /api/analytics/events/{eventId}
GET /api/analytics/campaigns
GET /api/analytics/activities
GET /api/analytics/products
GET /api/analytics/participants
GET /api/analytics/loyalty
```

Estas rutas leen directamente de las vistas SQL de KPIs: `vw_event_performance`, `vw_campaign_performance`, `vw_activity_analysis`, `vw_product_analysis` y `vw_participant_analysis`.

## Observaciones de diseño

- No se crea ni se modifica el esquema desde Hibernate (`ddl-auto: none`); la base se administra con los SQL de `database/init/`.
- Las entidades reflejan exactamente los nombres y tipos de columnas del SQL real.
- Se mantienen las restricciones de negocio en `AttendeeGuard` y en las validaciones del servicio para no romper la integridad de la demo.

## Acceso, CORS y seguridad

La API usa Spring Security con sesiones, CSRF y roles almacenados en PostgreSQL.
Las rutas internas requieren sesión. Los organizadores solo operan eventos
asignados; marketing consulta métricas y contactos con consentimiento. Las
rutas no declaradas se deniegan. Las únicas rutas de negocio públicas son las
inscripciones a eventos mediante enlaces habilitados por un administrador.

Lee [Acceso de usuarios y eventos](../ACCESS-CONTROL.md) para crear el primer
administrador, invitar cuentas, configurar cookies HTTPS y consultar los límites
de esta implementación. Reinicia el backend tras actualizar: Flyway añade las
tablas de acceso sin modificar los datos de eventos existentes.

CORS permite credenciales y el encabezado CSRF únicamente para los orígenes
exactos de `CORS_ALLOWED_ORIGIN`. El frontend usa `/api` por el mismo origen.
Las respuestas de error no incluyen stack traces ni detalles SQL.

## Detecciones clave de la base SQL

- `attendances` reemplaza la relación `participant_events`.
- `interactions`, `product_interactions`, `surveys` y `conversions` requieren que el participante esté `CHECKED_IN` en ese evento.
- Las KPIs se calculan desde vistas SQL, no desde tablas sin agregación. 
