# Acceso de usuarios y eventos

## Primer inicio

1. Inicia PostgreSQL y reinicia el backend con tus variables habituales (`DB_PASSWORD`, Java 21). Flyway aplica `V1__access_control.sql` una sola vez: añade usuarios, roles, asignaciones, invitaciones, acceso público y auditoría. `V2__demo_team_accounts.sql` crea las tres cuentas de demostración únicamente si está presente el evento semilla. No borra eventos ni participantes ni cambia cuentas existentes. Se requiere el esquema existente del proyecto y permisos DDL para estas migraciones.
2. Abre `http://localhost:5173/login`. En una base con los datos de demostración, entra con una de las tres cuentas de la sección siguiente. Comparte sus contraseñas por un canal privado.
3. Si usas una base sin los datos de demostración y aún no existen usuarios, aparece **Configura tu equipo**. Copia de la terminal del backend el **código temporal de administrador**. Introduce tu nombre, correo y una contraseña de al menos 12 caracteres. El código cambia al reiniciar y deja de servir cuando se crea la primera cuenta.
4. Entra en **Equipo**, invita personas, asigna funciones y selecciona los eventos de cada organizador.
5. Comparte de forma privada el enlace de activación generado. La persona establece su propia contraseña. El enlace vence en 24 horas, se almacena únicamente como hash y solo se puede utilizar una vez. **Nuevo enlace** invalida el anterior. No se envían correos automáticamente.

El antiguo valor de rol en localStorage ya no tiene efecto.

## Permisos

| Función activa | Acceso |
|---|---|
| Administrador | Usuarios, asignaciones, creación/edición de eventos y catálogos, participantes, operación, reportes y Power BI. |
| Organizador | Dashboard, eventos, participantes y operaciones de sus eventos asignados. No administra usuarios, cambia catálogos ni accede a reportes globales. |
| Marketing | Eventos y métricas generales, reportes y Power BI. Solo lee/exporta contactos que tengan consentimiento para comunicaciones. Sin operaciones de escritura. |
| Participante público | Información e inscripción en un evento con enlace habilitado. Recibe un QR, sin sesión ni acceso al panel interno. |

La función activa se guarda en la sesión del servidor. El menú de perfil muestra nombre, función y **Cerrar sesión**. **Cambiar función** aparece únicamente cuando la cuenta tiene varias funciones asignadas. Cambiarla vuelve al inicio y desmonta la vista anterior.

El backend aplica una lista explícita de rutas permitidas, controles de lectura/escritura sobre el evento y filtros de alcance en los indicadores. Comprobar solo el menú no concede acceso. Un QR de otro evento tampoco permite hacer check-in a un organizador no asignado.

Las modificaciones de roles, asignaciones o habilitación invalidan las sesiones existentes de la cuenta. La persona inicia sesión nuevamente con sus permisos actualizados. Se impide retirar el último administrador activo y la autodeshabilitación administrativa. Los cambios de cuentas y activaciones se registran en `app_access_audit`.

## Portada y cuentas locales

`/` es la landing pública de experiencias. `/dashboard` contiene el panel protegido y `/login` es el acceso del equipo. La landing permite buscar por nombre/lugar y filtrar por estado; solo consulta los campos públicos de los eventos publicados. No muestra datos de participantes, presupuestos ni métricas internas.

En esta instalación se crearon, a petición del usuario, `admin@demo.local`, `organizador@demo.local` y `marketing@demo.local`. Sus contraseñas distintas están en `LOCAL-ACCOUNTS.md` (excluido de Git, permisos de archivo limitados). La migración `V2__demo_team_accounts.sql` crea estas mismas cuentas en otras bases que incluyan los datos semilla del proyecto. Guarda solo hashes BCrypt, omite correos ya existentes y asigna al organizador los eventos activos presentes al crear la cuenta. Las contraseñas compartidas son solo para esta demostración; antes de publicar una instalación accesible al público, sustituye o deshabilita estas cuentas.

## Inscripción pública

En **Eventos → Publicación e inscripciones**, un administrador publica y copia el enlace. Crear un evento Activo o cambiar su estado a Activo abre la inscripción automáticamente. En eventos completados o cancelados, **Reactivar y publicar** cambia el estado a Activo y abre las inscripciones en una sola operación. Editar otros datos de un evento ya activo conserva la decisión de cerrar sus inscripciones. Se pueden cerrar las inscripciones con el mismo control. El evento debe estar planificado o activo. Al habilitar la inscripción también aparece en la landing; al cerrarla o completar/cancelar el evento, deja de publicarse. La landing se actualiza al recuperar el foco y cada 15 segundos mientras está visible.

La landing y el enlace permiten consultar nombre, fecha, ubicación, tipo, descripción y disponibilidad; no expone listas de asistentes ni métricas. El servidor determina el evento desde el enlace, sin confiar en un ID enviado por el formulario. La respuesta de inscripción contiene únicamente el nombre introducido, el evento y el QR recién generado.

El consentimiento es opcional y no está preseleccionado. Para participantes ya existentes, el registro no reemplaza sus datos ni su consentimiento. El QR confirma una inscripción; no verifica la identidad ni la propiedad del correo. No se implementaron verificación por correo ni recuperación automática de contraseñas.

## Sesiones y despliegue

- Contraseñas: BCrypt con coste 12; mínimo 12 caracteres y máximo 72 bytes.
- Sesiones: cookie `JSESSIONID`, HttpOnly, SameSite=Lax, caducidad tras 30 minutos de inactividad. Reiniciar el backend cierra las sesiones.
- Todas las escrituras, incluido login y registro público, requieren CSRF. El cliente obtiene `/api/auth/csrf` y envía el encabezado devuelto. Login usa formulario `email`/`password`; el resto usa JSON.
- El frontend usa `/api` por defecto. En desarrollo, Vite lo redirige a `localhost:8080`. En producción configura un proxy del mismo origen para `/api` y fallback del frontend para `/login`, `/activate` y `/register/*`.
- En HTTPS configura `SESSION_COOKIE_SECURE=true`. No expongas PostgreSQL públicamente. `CORS_ALLOWED_ORIGIN` acepta únicamente orígenes concretos; se permiten cookies y `X-CSRF-TOKEN`.
- El límite básico por IP está en memoria (login, setup, activación e inscripción). Para varias instancias se requiere un almacén compartido de sesiones y límites, o infraestructura equivalente. No se confía en `X-Forwarded-For` recibido directamente del cliente.
- `VITE_POWER_BI_URL` admite un enlace HTTPS de `app.powerbi.com` que requiera acceso organizacional. Se retiró el enlace público `view?r=...`; Power BI debe tener sus propios permisos. El rol local no concede acceso a Power BI.

Esta implementación cubre autenticación y permisos en la aplicación. SSO/MFA, recuperación de contraseña, verificación de correo y despliegue de infraestructura no están incluidos.

## Comprobaciones de esta entrega

Backend compilado con Java 21 (`mvn -Dmaven.test.skip=true compile`) y frontend compilado (`npm run build`). También se arrancó el backend contra PostgreSQL y se aplicó la migración de acceso. Se crearon las tres cuentas locales solicitadas mediante los flujos de configuración y activación. No se ejecutó una batería de pruebas de autenticación. Antes de publicar, valida con cuentas de cada función las lecturas, escrituras, revocación, CSRF y acceso a eventos ajenos.
