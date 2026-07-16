# Encuestas

Aplicación web de encuestas escrita en Java (JSPs y Servlets, Jakarta EE 10) con MySQL. Cada usuario registrado puede crear encuestas, ordenarlas en su tablero, compartir su perfil público y recibir respuestas de otros usuarios (calificación de 1 a 5 estrellas más comentario).

## Características

- Registro con **verificación de correo** e inicio de sesión con sesiones HTTP (con regeneración del id de sesión al autenticarse).
- **Restablecimiento de contraseña** por enlace con token firmado (HMAC-SHA256) y caducidad; las claves de firma se **persisten y rotan** automáticamente (o se usa `APP_TOKEN_SECRET` si está definido).
- Contraseñas con hash **PBKDF2** (HMAC-SHA256, sal aleatoria); los hashes antiguos se migran de forma transparente al iniciar sesión.
- Sello de seguridad (*security stamp*): al cambiar la contraseña se invalidan las demás sesiones abiertas.
- Gestión de cuenta: editar nombre, cambiar usuario, correo y contraseña, y eliminar la cuenta (con borrado en cascada de sus datos).
- CRUD de encuestas con posición configurable en el tablero.
- Perfil público por nombre de usuario (`/User?profile=usuario`) con las encuestas de esa persona.
- Respuestas a encuestas: calificación de 1 a 5 estrellas y comentario, **una por usuario y encuesta**; el dueño ve todas las respuestas recibidas, paginadas.
- Protección CSRF (token sincronizado en todos los POST), validación en servidor y **validación en cliente no intrusiva** (mensajes por campo, sin jQuery); los formularios rechazados conservan los valores no sensibles.
- Límite de peticiones por IP en login/registro más **bloqueo por cuenta** tras varios intentos fallidos; cabeceras de seguridad (incluida CSP sin scripts en línea y HSTS sobre HTTPS fuera de Development) y logging de eventos.
- Cookie de sesión `HttpOnly` + `SameSite=Lax`, y `Secure` fuera de Development; soporte para reverse proxy (`X-Forwarded-For`/`X-Forwarded-Proto` vía RemoteIpValve).
- Migraciones de esquema versionadas: se aplican automáticamente al arrancar y quedan registradas en la tabla `schemaversions`.
- Acceso a datos con pool de conexiones (HikariCP) y sentencias preparadas; credenciales por variables de entorno.
- UI con Bootstrap 5.3 (sin jQuery), vistas JSP bajo `WEB-INF` (no accesibles directamente) y assets con *cache busting* (`?v=` derivado del contenido).
- Endpoint de salud `/health`, compresión gzip y app contenerizada (Dockerfile multi-stage con usuario no root).
- Analizadores estáticos (Checkstyle y SpotBugs) aplicados en cada `verify`, Dependabot y CI en GitHub Actions.

## Requisitos

- [JDK 17](https://adoptium.net/) o superior (el proyecto compila con `--release 17`).
- [Docker](https://www.docker.com/) con Docker Compose, para la base de datos MySQL 8.4.
- Maven no es necesario: el repositorio incluye el Maven Wrapper (`mvnw`).

## Instalación y ejecución

### Opción A — Todo en Docker

Construye y levanta la aplicación (puerto 8080) junto con MySQL:

```bash
docker compose up --build
```

La app arranca en `http://localhost:8080` en modo Producción (sin datos de demostración). Regístrate para crear una cuenta; como el envío de correo está simulado, el enlace de confirmación aparece en los logs: `docker compose logs web`.

### Opción B — Base de datos en Docker + app local (desarrollo)

```bash
# 1. Levantar solo la base de datos
docker compose up -d db

# 2. Compilar el WAR (usa el wrapper; en Windows: mvnw.cmd)
./mvnw package
```

Despliega `target/encuestas.war` en un Tomcat 10.1 (por ejemplo desde Eclipse con un servidor configurado). Al arrancar se aplican las migraciones y, en el entorno Development (el valor por defecto de `APP_ENV`), se insertan los datos de demostración.

### Usuarios de demostración

En el entorno **Development** (Opción B), si la base está vacía se insertan dos cuentas de prueba **ya confirmadas**:

| Correo               | Contraseña | Nota                                            |
|----------------------|------------|-------------------------------------------------|
| `demo@encuestas.dev` | `demo1234` | Tiene una encuesta de ejemplo con una respuesta |
| `ana@encuestas.dev`  | `ana12345` | Responde la encuesta de la cuenta demo          |

Al registrar una cuenta nueva se requiere confirmar el correo. Con el envío simulado, el enlace de confirmación (y el de restablecimiento) se escriben en el log de la aplicación.

## Configuración

La aplicación se configura con variables de entorno (o propiedades del sistema con el mismo nombre):

| Variable           | Descripción                                                | Por defecto   |
|--------------------|------------------------------------------------------------|---------------|
| `DB_HOST`          | Host de MySQL                                              | `localhost`   |
| `DB_PORT`          | Puerto de MySQL                                            | `3306`        |
| `DB_NAME`          | Nombre de la base de datos                                 | `encuestas`   |
| `DB_USER`          | Usuario de la aplicación                                   | `encuestas`   |
| `DB_PASSWORD`      | Contraseña del usuario                                     | `encuestas`   |
| `APP_ENV`          | `Development` (datos demo) o `Production`                  | `Development` |
| `APP_TOKEN_SECRET` | Secreto fijo para firmar los tokens de correo (opcional)   | *(vacío)*     |
| `APP_KEYS_DIR`     | Directorio de las claves de firma generadas por la app     | `~/.encuestas` |

Si `APP_TOKEN_SECRET` no está definido, la aplicación genera sus claves de firma, las persiste en `APP_KEYS_DIR` (en Docker, el volumen `web-keys`) y las rota cada 90 días conservando las anteriores para validar tokens ya emitidos.

La base de datos en Docker se personaliza copiando `.env.example` a `.env` (`MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_PORT`, `WEB_PORT`). El contenedor (`encuestas-jsp-db`) y su volumen son propios de este proyecto, así que no interfiere con otras bases MySQL que tengas en Docker; si otro proyecto ya ocupa los puertos, cambia `MYSQL_PORT`/`WEB_PORT` en `.env`.

El endpoint `GET /health` comprueba la conectividad con la base de datos (útil como sonda de *readiness/liveness*).

## Estructura del proyecto

```
src/main/java/encuestas/
  model/            # Entidades: User, UserProfile, Poll, Answer
  data/             # Pool de conexiones y repositorios JDBC (RepositoryResult, PagedResult)
  service/          # PasswordService, TokenService, AuthService, AccountLockout,
                    # RateLimiter, SecurityStampCache, EmailSender, Validation, Messages
  infrastructure/   # AppConfig, App, AppInitializer, MigrationRunner, DevDataSeeder
  web/              # Servlets (User, Poll, Answer, Health) y filtros (CSRF, cabeceras,
                    # sello de seguridad, rate limit, codificación)
src/main/resources/db/migration/   # Scripts SQL versionados
src/main/webapp/
  WEB-INF/views/    # Vistas JSP (plantillas, públicas y de sesión)
  assets/           # Bootstrap 5.3, site.css, site.js (sin jQuery)
src/test/java/      # Pruebas unitarias y de integración (JUnit 5 + Testcontainers)
config/             # Reglas de Checkstyle y exclusiones de SpotBugs
Dockerfile          # Imagen multi-stage de la app (usuario no root, gzip)
docker-compose.yml  # Servicios web + MySQL 8.4 con volúmenes persistentes y healthcheck
```

## Pruebas y análisis estático

```bash
./mvnw verify
```

Hay pruebas unitarias de hashing de contraseñas (incluida la migración de hashes antiguos), tokens firmados (validez, caducidad, manipulación, rotación de claves), bloqueo de cuenta y validación, más pruebas de integración de los repositorios contra un MySQL 8.4 efímero (Testcontainers); estas últimas requieren Docker en ejecución y se omiten automáticamente si no está disponible. `verify` ejecuta además Checkstyle y SpotBugs y falla ante cualquier violación. En CI (GitHub Actions) todo esto corre en cada push y pull request.

## Base de datos

El esquema vive en [src/main/resources/db/migration](src/main/resources/db/migration) como scripts versionados que se aplican al arrancar (MySQL 8.4, `utf8mb4`, InnoDB, con columnas de auditoría `created_at`/`updated_at`, sello de seguridad, confirmación de correo e índice compuesto para el tablero). Para evolucionar el esquema, agrega un script nuevo (`V0002_...sql`) y regístralo en `MigrationRunner` — nunca edites uno ya aplicado:

| Tabla              | Contenido                                                             | Relaciones                                              |
|--------------------|-----------------------------------------------------------------------|---------------------------------------------------------|
| `a_users`          | Cuentas: correo, confirmación, hash y sello                           | —                                                       |
| `a_users_profiles` | Perfil público: usuario y nombre                                      | 1–1 con `a_users` (cascada)                             |
| `a_polls`          | Encuestas: título, descripción, posición                              | N–1 con `a_users_profiles` (cascada)                    |
| `a_answers`        | Respuestas: estrellas (1–5) y comentario, únicas por usuario/encuesta | N–1 con `a_polls` y `a_users_profiles` (cascada)        |

Para regenerar la base de datos desde cero (las migraciones y los datos demo se reaplican al arrancar la app):

```bash
docker compose down -v && docker compose up -d
```
