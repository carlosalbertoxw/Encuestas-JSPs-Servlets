# Decisiones de diseño

Registro breve de las decisiones que dan forma al proyecto: el contexto, lo que se eligió, lo que se descartó y por qué. Si una decisión cambia, se agrega una entrada nueva en lugar de editar la anterior.

## 1. Servlets y JSP sin framework

- **Contexto:** el objetivo del proyecto es practicar la API de Servlets/JSP de Jakarta EE tal cual.
- **Decisión:** sin Spring ni otro framework. `App` construye los servicios una vez y `AppInitializer` registra los filtros y servlets en código, pasándoles las dependencias por constructor.
- **Descartado:** un *service locator* estático (dificulta probar los servlets) y `web.xml` con cada servlet y filtro declarado a mano (mucho texto y fácil de desincronizar).
- **Consecuencia:** el orden de registro de los filtros es su orden de ejecución, y `web.xml` solo conserva las páginas de error y la configuración de la sesión.

## 2. URLs por ruta con una tabla de acciones

- **Contexto:** las acciones se elegían con parámetros (`?page=edit&id=3`, `form=sign-in`) y un `switch` en cada servlet.
- **Decisión:** rutas `/{Controlador}/{Acción}/{id?}`, iguales a las de las versiones ASP.NET y PHP. `BaseServlet` mantiene una tabla de acciones por método HTTP y aplica el límite de peticiones declarado en cada una; un `AuthFilter` exige la sesión en las áreas privadas.
- **Descartado:** un servlet por acción (decenas de clases) y mantener los parámetros `page`/`form`.

## 3. Tokens HMAC propios con rotación de claves

- **Contexto:** confirmación de cuenta, restablecimiento de contraseña y cambio de correo necesitan enlaces firmados y con caducidad, y Jakarta EE no trae un equivalente a Data Protection.
- **Decisión:** HMAC-SHA256 sobre un payload con propósito y caducidad. `TokenKeyStore` genera las claves, las persiste y las rota cada 90 días; el token de restablecimiento incluye el sello de seguridad, así que deja de servir en cuanto cambia la contraseña.
- **Descartado:** JWT con una librería externa (más superficie para un uso tan acotado) y tokens guardados en una tabla.

## 4. Bandeja de salida para los correos

- **Contexto:** enviar por SMTP dentro de la petición hacía que una caída del servidor de correo rompiera el registro con la cuenta ya creada, y que el tiempo de respuesta delatara si un correo estaba registrado.
- **Decisión:** las peticiones insertan en `a_outbox` y un hilo virtual envía con reintentos y espera exponencial, bloqueando filas con `FOR UPDATE SKIP LOCKED` para poder correr varias réplicas.
- **Descartado:** `try/catch` alrededor del envío síncrono (más simple, pero el correo se pierde) y un broker de mensajes (infraestructura de más para este volumen).

## 5. Contadores de intentos en MySQL

- **Contexto:** el límite por IP y el bloqueo por cuenta vivían en un `ConcurrentHashMap`, así que cada réplica llevaba su propia cuenta y un reinicio la ponía a cero.
- **Decisión:** tabla `a_attempt_counters` con ventanas fijas e incremento atómico (`INSERT … ON DUPLICATE KEY UPDATE`), la misma que usa la versión PHP.
- **Descartado:** Redis (un servicio más que operar) y seguir en memoria.

## 6. Confianza explícita en los proxies

- **Contexto:** `RemoteIpValve` con su configuración por defecto confía en cualquier IP privada; detrás del NAT de Docker eso permitía a cualquier cliente falsear `X-Forwarded-For` y saltarse el límite por IP.
- **Decisión:** un filtro propio que solo acepta `X-Forwarded-For`/`X-Forwarded-Proto` de las IPs o rangos de `TRUSTED_PROXIES` (vacío por defecto) y toma como IP del cliente la primera, de derecha a izquierda, que no es un proxy de confianza.
- **Descartado:** configurar `internalProxies` de la válvula (expresiones regulares en `context.xml`, fuera del código y de las pruebas).
