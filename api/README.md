# ERP API

API REST del ERP sobre **PhaseDB**, con autenticacion JWT, roles de negocio y
persistencia real en los globales de Phase. El frontend `ERP-DEMO` consume esta
API mediante `fetch` y se sirve como sitio estatico compilado desde TypeScript.

## Requisitos

- JDK 17 o superior (probado con Temurin 25, compilando con `--release 17`).
- PhaseDB escuchando en `http://127.0.0.1:8484`.
- El usuario de Phase configurado debe poder ejecutar comandos y escribir los
  globales de la aplicacion (por defecto se usa la cuenta admin).

## Arranque

```powershell
cd api
$env:JAVA_HOME = 'C:\ruta\al\jdk'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd spring-boot:run
```

La API queda en `http://localhost:8080`. Documentacion interactiva:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

El perfil por defecto es `dev`: activa Swagger, el seed del administrador y la
migracion de clientes. Para produccion usa `--spring.profiles.active=prod`
(o `SPRING_PROFILES_ACTIVE=prod`); en ese perfil Swagger queda deshabilitado y
las credenciales y los origenes CORS son obligatorios.

Al primer arranque en `dev` se crea el usuario administrador y se migran los
clientes del formato anterior. Ambos procesos son idempotentes.

Credenciales demo por defecto (solo perfil `dev`):

```
admin@empresa.com / admin123
```

## Frontend

El frontend vive en `../ERP-DEMO`. Se compila con TypeScript y se sirve por
HTTP para que el navegador aplique CORS y las rutas relativas funcionen:

```powershell
cd ..\ERP-DEMO
npm install
npm run build
npm run serve
```

Queda disponible en `http://localhost:5500`. El codigo fuente esta en
`ERP-DEMO/src` y el resultado compilado en `ERP-DEMO/dist` (ignorado por git);
la unica entrada es `dist/main.js`. La URL de la API se puede cambiar sin
recompilar:

```js
localStorage.setItem('erp.apiBase', 'http://mi-servidor:8080/api/v1');
```

## Configuracion

El perfil `dev` trae valores por defecto para la demo. El perfil `prod` exige
`ERP_JWT_SECRET`, `ERP_PHASE_USER`, `ERP_PHASE_PASSWORD` y `ERP_CORS_ORIGINS`;
si falta alguna, la aplicacion no arranca. Ademas valida que el secreto JWT
tenga al menos 32 bytes.

| Variable | Default en `dev` | Descripcion |
| --- | --- | --- |
| `PORT` | `8080` | Puerto HTTP |
| `SPRING_PROFILES_ACTIVE` | `dev` | Perfil activo |
| `ERP_PHASE_URL` | `http://127.0.0.1:8484` | URL de PhaseDB |
| `ERP_PHASE_USER` / `ERP_PHASE_PASSWORD` | `admin` / `admin123` | Cuenta de servicio (obligatorias en `prod`) |
| `ERP_JWT_SECRET` | secreto demo | Firma HS256, minimo 32 bytes (obligatorio en `prod`) |
| `ERP_JWT_EXPIRATION` | `8h` | Vigencia del token |
| `ERP_CURRENCY` | `USD` | Moneda de la demo |
| `ERP_IVA_RATE` | `0.00` | IVA como fraccion (`0.19` = 19%) |
| `ERP_RUT_DV` | `false` | Validar digito verificador del RUT |
| `ERP_CORS_ORIGINS` | localhost:5500/8080 | Origenes permitidos (obligatorio en `prod`) |
| `ERP_SEED_ENABLED` | `true` | Crear admin inicial |
| `ERP_MIGRATION_CLIENTES` | `true` | Migrar clientes legacy |
| `ERP_USER_CACHE_TTL` | `30s` | Vigencia del estado ACTIVO/INACTIVO en cache |
| `ERP_USER_CACHE_STALE` | `5m` | Estado de respaldo si PhaseDB no responde |
| `ERP_LOGIN_MAX_ATTEMPTS` | `5` | Intentos fallidos antes del bloqueo |
| `ERP_LOGIN_WINDOW` | `15m` | Ventana para contar intentos |
| `ERP_LOGIN_LOCKOUT` | `15m` | Duracion del bloqueo de login |
| `ERP_UPLOADS_DIR` | `uploads` | Carpeta donde se guardan los documentos adjuntos |
| `ERP_UPLOAD_MAX_FILE_SIZE` | `10MB` | Limite de multipart por archivo |
| `ERP_UPLOAD_MAX_BYTES` | `10485760` | Limite validado por la aplicacion |

El perfil `prod` tambien desactiva `springdoc` (Swagger UI y OpenAPI) y deja
`erp.security.swagger-public=false`, por lo que esas rutas responden `401/404`.

## Seguridad adicional

- **Bloqueo de login**: 5 fallos por correo + IP dentro de 15 minutos bloquean
  el login durante 15 minutos. La respuesta es `429` con codigo
  `LOGIN_BLOQUEADO` y header `Retry-After` en segundos.
- **Revalidacion de usuarios**: cada token se contrasta contra `^USUARIOS` con
  una cache de 30 segundos. Si el usuario pasa a `INACTIVO`, el siguiente
  request devuelve `401` con mensaje `La cuenta esta inactiva`.
- **Correlacion**: `X-Request-Id` se acepta o se genera, se devuelve en la
  respuesta, se registra en los logs y se propaga a PhaseDB.
- **Errores de PhaseDB**: el cliente solo ve mensajes y codigos sanitizados
  (`SERVICIO_DATOS_NO_DISPONIBLE`, `ERROR_DATOS`, etc.). El detalle completo
  queda en el log con el `requestId`.
- **Swagger**: publico solo en `dev`; en `prod` responde `401` sin token y
  `404` con token, porque springdoc esta deshabilitado.

## Autenticacion y roles

```http
POST /api/v1/auth/login
Content-Type: application/json

{ "email": "admin@empresa.com", "password": "admin123" }
```

La respuesta incluye `token`; se envia como `Authorization: Bearer <token>`.

Roles: `ADMIN`, `COMERCIAL`, `OPERACIONES`, `CONTABILIDAD`.

- `ADMIN`: acceso completo y gestion de usuarios.
- `COMERCIAL`: clientes, presupuestos y lectura general.
- `OPERACIONES`: operaciones y lectura general.
- `CONTABILIDAD`: facturas, pagos, notas de credito, cartera y conciliacion.

## Endpoints

### Autenticacion y usuarios

| Metodo | Ruta | Roles |
| --- | --- | --- |
| POST | `/api/v1/auth/login` | publico |
| GET | `/api/v1/auth/me` | autenticado |
| POST | `/api/v1/auth/change-password` | autenticado |
| GET/POST | `/api/v1/usuarios` | ADMIN |
| GET/PUT/DELETE | `/api/v1/usuarios/{email}` | ADMIN |

### Clientes

| Metodo | Ruta | Roles |
| --- | --- | --- |
| GET | `/api/v1/clientes?q=&activo=&page=&size=` | autenticado |
| GET | `/api/v1/clientes/buscar?q=` | autenticado |
| GET | `/api/v1/clientes/{numDoc}` | autenticado |
| POST/PUT | `/api/v1/clientes`, `/api/v1/clientes/{numDoc}` | ADMIN, COMERCIAL |
| PATCH | `/api/v1/clientes/{numDoc}/estado` | ADMIN, COMERCIAL |
| DELETE | `/api/v1/clientes/{numDoc}` | ADMIN, COMERCIAL (baja logica) |

### Presupuestos, operaciones y facturas

| Metodo | Ruta | Roles |
| --- | --- | --- |
| GET | `/api/v1/presupuestos`, `/api/v1/presupuestos/{id}` | autenticado |
| POST/PUT/PATCH | `/api/v1/presupuestos...` | ADMIN, COMERCIAL |
| GET | `/api/v1/operaciones`, `/api/v1/operaciones/{id}` | autenticado |
| POST/PUT/PATCH | `/api/v1/operaciones...` | ADMIN, OPERACIONES |
| POST | `/api/v1/operaciones/{id}/documentos/archivo` | ADMIN, OPERACIONES |
| GET | `/api/v1/operaciones/{id}/documentos/{token}/archivo` | autenticado |
| GET | `/api/v1/facturas`, `/api/v1/facturas/{id}` | autenticado |
| POST/PUT/PATCH | `/api/v1/facturas...` | ADMIN, CONTABILIDAD |
| GET | `/api/v1/facturas/{id}/pdf` | autenticado |
| POST | `/api/v1/facturas/{id}/enviar` | ADMIN, CONTABILIDAD |
| GET | `/api/v1/facturas/{id}/envios` | autenticado |

Los documentos de operación aceptan una referencia de texto (`archivo`) por compatibilidad, o un archivo real mediante `multipart/form-data` con los campos `tipo`, `numero`, `fecha` y `archivo` (máximo 10 MB). El archivo se guarda bajo `erp.uploads.dir` (por defecto `uploads/`) y la respuesta incluye `archivoToken` para descargarlo con `GET /api/v1/operaciones/{id}/documentos/{token}/archivo`.

### Cobranza y finanzas

| Metodo | Ruta | Roles |
| --- | --- | --- |
| GET/POST | `/api/v1/pagos`, `/api/v1/pagos/{id}` | lectura autenticada; escritura ADMIN, CONTABILIDAD |
| POST | `/api/v1/pagos/{id}/anular` | ADMIN, CONTABILIDAD |
| GET/POST | `/api/v1/notas-credito` | lectura autenticada; escritura ADMIN, CONTABILIDAD |
| POST | `/api/v1/notas-credito/{id}/anular` | ADMIN, CONTABILIDAD |
| GET | `/api/v1/cuentas-por-cobrar`, `/resumen`, `/{facturaId}` | autenticado |
| GET/POST | `/api/v1/cobranza/recordatorios` | lectura autenticada; escritura ADMIN, CONTABILIDAD |
| POST | `/api/v1/cobranza/recordatorios/masivo` | ADMIN, CONTABILIDAD |
| GET/POST | `/api/v1/cobranza/promesas` | lectura autenticada; escritura ADMIN, CONTABILIDAD |
| PATCH | `/api/v1/cobranza/promesas/{id}/estado` | ADMIN, CONTABILIDAD |
| GET | `/api/v1/conciliacion`, `/movimientos`, `/movimientos/{id}` | autenticado |
| POST | `/api/v1/conciliacion/simular`, `/ejecutar` | ADMIN, CONTABILIDAD |
| POST | `/api/v1/conciliacion/movimientos/{id}/conciliar` | ADMIN, CONTABILIDAD |
| GET | `/api/v1/dashboard` | autenticado |
| POST | `/api/v1/admin/recalcular` | ADMIN |

## Idempotencia

Las escrituras financieras aceptan el header `Idempotency-Key`:

| Endpoint | Clave |
| --- | --- |
| `POST /api/v1/pagos` | `Idempotency-Key` o derivada de factura, monto y referencia |
| `POST /api/v1/pagos/{id}/anular` | `Idempotency-Key` o `pago-anular:{id}` |
| `POST /api/v1/notas-credito` | `Idempotency-Key` o derivada de factura, tipo, monto y motivo |
| `POST /api/v1/notas-credito/{id}/anular` | `Idempotency-Key` o `nota-anular:{id}` |

- Repetir la misma clave con el mismo cuerpo devuelve la respuesta original con
  `200` y el header `Idempotent-Replay: true`.
- Repetir la clave con un cuerpo distinto devuelve `409 IDEMPOTENCIA_CONFLICTO`.
- Mientras una operacion esta en curso, otra peticion con la misma clave espera
  hasta `erp.idempotencia.espera-ms`; si no termina, recibe
  `409 OPERACION_EN_CURSO`.

## Reglas de negocio

- Los montos se manejan con `BigDecimal`; la moneda y el IVA son configurables.
- El saldo de una factura = total - pagos vigentes - notas de credito vigentes.
- Un pago no puede superar el saldo pendiente.
- Las fechas se validan en formato `YYYY-MM-DD`; una fecha invalida devuelve
  `422 FECHA_INVALIDA` (ya no se reemplaza por la fecha de hoy).
- El estado de la factura se deriva de sus movimientos:
  - `PENDIENTE` -> `ANULADA` manual solo si no hay pagos vigentes.
  - `ANULADA` -> `PENDIENTE` solo si no hay pagos ni nota de anulacion vigente.
  - `PAGADA` y `VENCIDA` los asigna el sistema; no se aceptan por `PATCH`.
  - `PUT /facturas/{id}` se bloquea con `409 FACTURA_CON_MOVIMIENTOS` si la
    factura ya tiene pagos o notas vigentes.
- Una nota de tipo `ANULACION` anula la factura; al anular la nota se reabre.
- La cartera se deriva en linea de facturas, pagos y notas (no se duplica).
- La conciliacion es **simulada**: no importa CSV ni se conecta al banco. El
  boton "Importar cartola" corresponde a `POST /conciliacion/simular`.
- El PDF de factura se genera realmente con OpenPDF.
- El envio por correo es **simulado**: queda registrado en `^ENVIOS` con estado
  `SIMULADO`, sin SMTP.

## Codigos de error relevantes

| Codigo | HTTP | Significado |
| --- | --- | --- |
| `FECHA_INVALIDA` | 422 | Fecha vacia, mal formada o fuera de rango |
| `TRANSICION_INVALIDA` | 409 | Cambio de estado no permitido |
| `FACTURA_CON_MOVIMIENTOS` | 409 | Edicion bloqueada por pagos o notas |
| `IDEMPOTENCIA_CONFLICTO` | 409 | Clave repetida con cuerpo distinto |
| `OPERACION_EN_CURSO` | 409 | Lock o idempotencia aun en ejecucion |
| `CONDICION_INVALIDA` | 400 | Condicion de pago no permitida |
| `LOGIN_BLOQUEADO` | 429 | Demasiados intentos fallidos; incluye `Retry-After` |
| `NO_ENCONTRADO` | 404 | Ruta o recurso inexistente |
| `SERVICIO_DATOS_NO_DISPONIBLE` | 503 | PhaseDB no disponible o error de conexion |
| `ERROR_DATOS` | 502 | Error sanitizado al comunicarse con PhaseDB |

## Datos en Phase

| Global | Contenido |
| --- | --- |
| `^CLIENTES` | Clientes |
| `^PRESUPUESTOS` | Presupuestos |
| `^OPERACIONES` | Operaciones |
| `^FACTURAS` | Facturas |
| `^PAGOS` | Pagos |
| `^NOTASCREDITO` | Notas de credito |
| `^COBRANZA` | Recordatorios |
| `^PROMESAS` | Promesas de pago |
| `^CONCILIACION` | Movimientos bancarios simulados |
| `^ENVIOS` | Envios de facturas simulados |
| `^USUARIOS` | Usuarios de la aplicacion (BCrypt) |
| `^SECUENCIAS` | Secuencias legibles por anio |
| `^IDEMPOTENCIA` | Registro de claves idempotentes (TTL configurable) |
| `^MIGRACION` | Marcadores de migracion |

## Pruebas

Las pruebas automatizadas fueron retiradas del proyecto: el equipo de QA ejecuta
las pruebas manuales descritas en `docs/guia de pruebas.md` desde Swagger UI.
