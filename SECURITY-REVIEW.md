# Control de acceso — estado de implementación

Actualizado con autenticación y autorización de la aplicación.

## Hallazgos anteriores

- Participantes accesibles sin sesión: se añadió autenticación en Spring Security, alcance por evento para organizadores y consentimiento obligatorio para lectura de marketing.
- Reportes accesibles sin sesión: se restringieron a administrador y marketing.
- CRUD público: se reemplazó por permisos explícitos por ruta y comprobaciones del recurso antes de escribir. Las demás rutas se deniegan.

## Controles incorporados

Sesiones HttpOnly/SameSite, CSRF, contraseñas BCrypt, cambio de función limitado a
roles asignados, revocación por versión de cuenta, invitaciones de uso único,
protección del último administrador, límites básicos de intentos y auditoría de
cuentas. El acceso público se limita a eventos cuyo registro está habilitado.

CORS utiliza orígenes exactos y ahora permite credenciales para las sesiones.
Las credenciales de PostgreSQL y Gemini siguen viniendo del entorno.

## Alcance de la revisión

Se compilaron backend y frontend; el backend arrancó y aplicó la migración en PostgreSQL. No se ejecutaron pruebas funcionales de esta
nueva capa de seguridad; los resultados de pruebas de versiones anteriores no
validan estos cambios. No equivale a una auditoría de seguridad para producción.

Consulta [ACCESS-CONTROL.md](ACCESS-CONTROL.md) para configuración y límites:
HTTPS, sesiones/límites compartidos si hay varias instancias, permisos propios
de Power BI y flujos de identidad aún no implementados.
