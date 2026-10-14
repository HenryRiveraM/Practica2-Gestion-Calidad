# Coca-Cola Event Intelligence

Proyecto de demo para una API REST en Spring Boot con PostgreSQL, orientada a campañas, eventos, participantes y KPIs.

## Estructura

```text
CocaCola_Hackathon/
├── docker-compose.yml
├── database/
│   ├── init/       # Esquema, migraciones, datos semilla y vistas
│   └── kpi_queries.sql
├── backend/
└── frontend/
```

## Preparación de la base de datos

Para una instalación nueva, puedes levantar una base aislada con Docker Compose.
Requiere Docker Desktop (macOS/Windows) y crea PostgreSQL con el esquema, las
vistas y los datos de demostración incluidos en `database/init/`. Si ya tienes
PostgreSQL local y el proyecto te funciona, continúa usando esa instalación.

```bash
docker compose up -d postgres
```

La base queda disponible en `localhost:5432`, con base `cocacola` y usuario y
contraseña `postgres`. Para detenerla sin borrar los datos: `docker compose stop`.
Para eliminar también el volumen de datos, usar `docker compose down -v`.

También puedes usar una instalación local de PostgreSQL y ejecutar los scripts
en orden numérico: esquema, migraciones, datos semilla, vistas y relleno de
inscripciones. Ejecuta estos scripts solo en una base vacía para la demo:
`00-schema.sql` elimina tablas previas con `CASCADE`. No lo ejecutes sobre la
base que ya usa tu compañera.

1. Crear la base (solo si usas PostgreSQL local):

```bash
createdb cocacola
```

2. Ejecutar en orden:

```bash
psql -U postgres -d cocacola -f database/init/00-schema.sql
psql -U postgres -d cocacola -f database/init/01-registration-schema.sql
psql -U postgres -d cocacola -f database/init/02-check-in-schema.sql
psql -U postgres -d cocacola -f database/init/03-activities-surveys-schema.sql
psql -U postgres -d cocacola -f database/init/04-backend-compat.sql
psql -U postgres -d cocacola -f database/init/05-seed.sql
psql -U postgres -d cocacola -f database/init/06-views.sql
psql -U postgres -d cocacola -f database/init/07-registration-backfill.sql
```

Docker Compose aplica automáticamente las migraciones de inscripciones, check-in, actividades y encuestas.

## Backend

Consultar [backend/README.md](backend/README.md) para las instrucciones completas de ejecución y endpoints.

## Ejecución en macOS

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21) # macOS
DB_PASSWORD=postgres mvn spring-boot:run
```

En otra terminal:

```bash
cd frontend
nvm use 22.14.0 # macOS, si usas nvm
pnpm install --frozen-lockfile
pnpm dev
```

Abre `http://localhost:5173`. Durante el desarrollo, Vite envía `/api` al backend
en `http://localhost:8080`; deja ambos procesos abiertos. Para usar otra API,
configura `VITE_API_URL` antes de iniciar el frontend.

## Ejecución en Windows (PowerShell)

Si todavía no tienes PostgreSQL para este proyecto, inicia la base de
demostración desde la raíz del repositorio:

```powershell
docker compose up -d postgres
```

En una terminal PowerShell:

```powershell
cd backend
$env:DB_PASSWORD = "postgres"
mvn spring-boot:run
```

En otra terminal PowerShell:

```powershell
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

Abre `http://localhost:5173/login` y usa una de las cuentas de demostración
indicadas abajo. Si ya tienes PostgreSQL local y esta aplicación funciona,
continúa usando esa base y omite el paso de Docker.


## Cuentas y permisos

Después de actualizar, reinicia el backend y abre `/login`. En la base de
demostración incluida en `database/init/`, Flyway crea automáticamente las
cuentas `admin@demo.local`, `organizador@demo.local` y `marketing@demo.local`
con las contraseñas compartidas por el equipo. No se sobreescriben cuentas ya
creadas. El organizador recibe los eventos activos de la base al crear su
cuenta. En una base sin los datos de demostración, la primera cuenta se crea
con el código temporal que aparece en la terminal del backend. Luego usa
**Equipo** para asignar funciones y eventos; ya no se puede elegir libremente un
rol en la barra. Las cuentas y asignaciones se guardan en PostgreSQL.

Consulta [Acceso de usuarios y eventos](ACCESS-CONTROL.md) para la activación,
inscripción pública, permisos y configuración de despliegue. El proceso es el
mismo en macOS y Windows; se conservan las variables de conexión existentes.

La página `/` muestra las experiencias públicas. El panel interno está en `/dashboard` y el acceso del equipo en `/login`. Publica los eventos desde **Eventos → Inscripción pública**.
