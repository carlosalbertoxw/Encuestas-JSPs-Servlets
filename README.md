# Encuestas

Aplicación web de encuestas escrita en Java 25 (JSPs y Servlets, Jakarta EE 11 sobre Tomcat 11) con MySQL. Cada usuario registrado puede crear encuestas, ordenarlas en su tablero, compartir su perfil público y recibir respuestas de otros usuarios (calificación de 1 a 5 estrellas más comentario).

## Características

- Registro con **verificación de correo** e inicio de sesión con sesiones HTTP (con regeneración del id de sesión al autenticarse; el cierre de sesión la invalida en el servidor).
- **Restablecimiento de contraseña** por enlace con token firmado (HMAC-SHA256) y caducidad; las claves de firma se **persisten y rotan** automáticamente (o se usa `APP_TOKEN_SECRET` si está definido).
- **Cambio de correo con confirmación**: el correo nuevo queda pendiente y solo se aplica al abrir el enlace enviado a esa dirección; se avisa al correo anterior y se cierran todas las sesiones.
- Enlaces de los correos construidos con la URL pública configurada (`APP_BASE_URL`), nunca con la cabecera `Host` de la petición.
- Envío de correos por **SMTP** (Jakarta Mail); en local, **Mailpit** captura los correos y los muestra en una interfaz web.
- Contraseñas con hash **PBKDF2** (HMAC-SHA256, 210 000 iteraciones, sal aleatoria); los hashes antiguos se migran de forma transparente al iniciar sesión.
- Sello de seguridad (*security stamp*): al cambiar la contraseña o el correo se invalidan las demás sesiones.
- Gestión de cuenta: editar nombre, cambiar usuario, correo y contraseña, y eliminar la cuenta (con borrado en cascada de sus datos).
- CRUD de encuestas con posición configurable; tablero, perfil público y respuestas **paginados**.
- Perfil público por nombre de usuario (`/User?profile=usuario`).
- Respuestas: calificación de 1 a 5 estrellas y comentario, **una por usuario y encuesta**.
- Protección CSRF (token sincronizado en todos los POST), validación en servidor y en cliente (sin jQuery); los formularios rechazados conservan los valores no sensibles.
- Límite de peticiones por IP en login/registro más **bloqueo por cuenta**; cabeceras de seguridad (CSP sin scripts en línea y HSTS sobre HTTPS fuera de Development) y logs sin correos en claro.
- Logs en **JSON** fuera de Development, con un identificador por petición (cabecera `X-Request-Id`).
- Cookie de sesión `HttpOnly` + `SameSite=Lax`, y `Secure` fuera de Development; soporte para reverse proxy (`X-Forwarded-For`/`X-Forwarded-Proto` vía RemoteIpValve).
- Migraciones de esquema versionadas que se **descubren solas** y se aplican al arrancar (tabla `schemaversions`).
- Acceso a datos con pool de conexiones (HikariCP) y sentencias preparadas; credenciales por variables de entorno.
- Sondas `/health/live` (proceso) y `/health/ready` (incluye la base de datos), con `HEALTHCHECK` en la imagen.
- Checkstyle y SpotBugs aplicados en cada `verify`; CodeQL, Trivy y Dependabot en GitHub.

## Requisitos

- [JDK 25](https://adoptium.net/) (el proyecto compila con `--release 25`).
- [Docker](https://www.docker.com/) con Docker Compose, para MySQL 8.4 y Mailpit.
- Maven no es necesario: el repositorio incluye el Maven Wrapper (`mvnw`, Maven 3.9.16).

## Instalación y ejecución

### Opción A — Todo en Docker

Construye y levanta la aplicación (puerto 8080), MySQL y Mailpit:

```bash
docker compose up --build
```

La app arranca en `http://localhost:8080` en modo Producción (sin datos de demostración). Regístrate para crear una cuenta y abre los correos (confirmación, restablecimiento, cambio de correo) en Mailpit: `http://localhost:8025`.

### Opción B — Base de datos y Mailpit en Docker + app local (desarrollo)

```bash
# 1. Levantar MySQL y Mailpit
docker compose up -d db mailpit

# 2. Compilar el WAR (usa el wrapper; en Windows: mvnw.cmd)
./mvnw package
```

Despliega `target/encuestas.war` en un Tomcat 11 (por ejemplo desde Eclipse o IntelliJ con un servidor configurado). Al arrancar se aplican las migraciones y, en el entorno Development (el valor por defecto de `APP_ENV`), se insertan los datos de demostración. Para recibir los correos en Mailpit define `SMTP_HOST=localhost`, `SMTP_PORT=1025` y `SMTP_STARTTLS=false`; sin SMTP, en Development los correos se escriben en el log.

### Usuarios de demostración

En el entorno **Development** (Opción B), si la base está vacía se insertan dos cuentas de prueba **ya confirmadas**:

| Correo               | Contraseña | Nota                                            |
|----------------------|------------|-------------------------------------------------|
| `demo@encuestas.dev` | `demo1234` | Tiene una encuesta de ejemplo con una respuesta |
| `ana@encuestas.dev`  | `ana12345` | Responde la encuesta de la cuenta demo          |

## Configuración

La aplicación se configura con variables de entorno (o propiedades del sistema con el mismo nombre):

| Variable           | Descripción                                                     | Por defecto   |
|--------------------|-----------------------------------------------------------------|---------------|
| `DB_HOST`          | Host de MySQL                                                   | `localhost`   |
| `DB_PORT`          | Puerto de MySQL                                                 | `3306`        |
| `DB_NAME`          | Nombre de la base de datos                                      | `encuestas`   |
| `DB_USER`          | Usuario de la aplicación                                        | `encuestas`   |
| `DB_PASSWORD`      | Contraseña del usuario                                          | `encuestas`   |
| `APP_ENV`          | `Development` (datos demo) o `Production`                       | `Development` |
| `APP_BASE_URL`     | URL pública; base de los enlaces de los correos. **Obligatoria** fuera de Development | `http://localhost:8080` en Development |
| `SMTP_HOST`        | Servidor SMTP. **Obligatorio** fuera de Development             | *(vacío)*     |
| `SMTP_PORT`, `SMTP_STARTTLS` | Puerto y uso de STARTTLS                              | `587`, `true` |
| `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM` | Credenciales y remitente                 | *(vacío)*, *(vacío)*, `Encuestas <no-reply@encuestas.local>` |
| `APP_TOKEN_SECRET` | Secreto fijo para firmar los tokens de correo (opcional)        | *(vacío)*     |
| `APP_KEYS_DIR`     | Directorio de las claves de firma generadas por la app          | `~/.encuestas` |

Si `APP_TOKEN_SECRET` no está definido, la aplicación genera sus claves de firma, las persiste en `APP_KEYS_DIR` (en Docker, el volumen `web-keys`) y las rota cada 90 días conservando las anteriores para validar tokens ya emitidos.

Docker Compose se personaliza copiando `.env.example` a `.env` (`MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_PORT`, `WEB_PORT`, `APP_BASE_URL`, `MAILPIT_SMTP_PORT`, `MAILPIT_UI_PORT`, `APP_TOKEN_SECRET`). Los contenedores (`encuestas-jsp-*`) y volúmenes son propios de este proyecto; si otro proyecto ya ocupa los puertos, cámbialos en `.env` (y `APP_BASE_URL` junto con `WEB_PORT`).

## Estructura del proyecto

```
src/main/java/encuestas/
  model/            # Entidades: User, UserProfile, Poll, Answer
  data/             # Pool de conexiones y repositorios JDBC (RepositoryResult, PagedResult)
  service/          # PasswordService, TokenService, AuthService, AccountLockout, RateLimiter,
                    # SecurityStampCache, correo (SMTP / log), Validation, Messages, LogRedaction
  infrastructure/   # AppConfig, App, AppInitializer, MigrationRunner, DevDataSeeder, JsonLogFormatter
  web/              # Servlets (User: inicio y acceso; Account: gestión de la cuenta; Poll, Answer,
                    # Health) y filtros (request id, CSRF, cabeceras, sello de seguridad, rate limit)
src/main/resources/db/migration/   # Scripts SQL versionados (V0001__..., V0002__...)
src/main/webapp/
  WEB-INF/views/    # Vistas JSP (plantillas, públicas y de sesión)
  assets/           # Bootstrap 5.3, site.css, site.js (sin jQuery)
src/test/java/      # Pruebas unitarias (*Test) y de integración (*IT, Testcontainers)
config/             # Reglas de Checkstyle y exclusiones de SpotBugs
docker/             # Sonda del HEALTHCHECK de la imagen
Dockerfile          # Imagen multi-stage (Tomcat 11 sobre JRE 25, usuario no root, gzip)
docker-compose.yml  # web + MySQL 8.4 + Mailpit, con volúmenes persistentes
```

## Pruebas y análisis estático

```bash
./mvnw verify
```

Las pruebas están separadas por costo: Surefire ejecuta las **unitarias** (`*Test`) en la fase `test` y Failsafe las de **integración** (`*IT`) en `integration-test`. Para el ciclo rápido basta `./mvnw test`, que no toca Docker; `./mvnw verify -DskipITs` añade el análisis estático sin levantar contenedores, y `./mvnw verify -DskipUTs` ejecuta solo las de integración.

Las unitarias cubren hashing de contraseñas (incluida la migración de hashes antiguos), tokens firmados (confirmación, cambio de correo y restablecimiento: validez, caducidad, manipulación, rotación de claves), bloqueo de cuenta, validación, configuración de la URL pública, descubrimiento de migraciones, logs JSON y enmascarado de correos. Las de integración ejercitan los repositorios contra un MySQL 8.4 efímero (Testcontainers), incluidos la paginación y el flujo del correo pendiente; requieren Docker en ejecución y se omiten automáticamente si no está disponible. `verify` ejecuta además Checkstyle y SpotBugs y falla ante cualquier violación.

## Integración continua

En cada push a `master` y en cada pull request, GitHub Actions ejecuta:

| Workflow / job                     | Qué hace                                                                                 |
|------------------------------------|------------------------------------------------------------------------------------------|
| `ci.yml` · `pruebas-unitarias`     | Unitarias, Checkstyle y SpotBugs                                                         |
| `ci.yml` · `pruebas-integracion`   | Pruebas de integración con Testcontainers                                                |
| `ci.yml` · `imagen`                | Construye la imagen Docker y la escanea con Trivy (falla con vulnerabilidades altas o críticas corregibles) |
| `ci.yml` · `revision-dependencias` | En los PR, rechaza dependencias nuevas o actualizadas con vulnerabilidades conocidas     |
| `codeql.yml`                       | Análisis estático de seguridad (CodeQL) en cada cambio y semanalmente                    |

Dependabot ([dependabot.yml](.github/dependabot.yml)) propone cada semana actualizaciones de Maven, de las imágenes del Dockerfile y de Docker Compose, y de GitHub Actions.

## Base de datos

El esquema vive en [src/main/resources/db/migration](src/main/resources/db/migration) como scripts versionados que se aplican al arrancar (MySQL 8.4, `utf8mb4`, InnoDB). Para evolucionar el esquema, agrega un script nuevo (`V0003__descripcion.sql`); nunca edites uno ya aplicado:

| Tabla              | Contenido                                                             | Relaciones                                              |
|--------------------|-----------------------------------------------------------------------|---------------------------------------------------------|
| `a_users`          | Cuentas: correo, correo pendiente, confirmación, hash y sello         | —                                                       |
| `a_users_profiles` | Perfil público: usuario y nombre                                      | 1–1 con `a_users` (cascada)                             |
| `a_polls`          | Encuestas: título, descripción, posición                              | N–1 con `a_users_profiles` (cascada)                    |
| `a_answers`        | Respuestas: estrellas (1–5) y comentario, únicas por usuario/encuesta | N–1 con `a_polls` y `a_users_profiles` (cascada)        |

Para regenerar la base de datos desde cero (las migraciones y los datos demo se reaplican al arrancar la app):

```bash
docker compose down -v && docker compose up -d
```

## Licencia

[MIT](LICENSE).
