# ERP API

API REST del ERP sobre **PhaseDB**, con autenticacion JWT, roles de negocio y
persistencia real en los globales de Phase. El frontend `ERP-DEMO` todavia no
esta conectado a esta API.

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

Al primer arranque se crea el usuario administrador y se migran los clientes
del formato anterior. Ambos procesos son idempotentes.

Credenciales demo por defecto:

```
admin@empresa.com / admin123
```

## Configuracion

Todo se puede sobrescribir con variables de entorno:

| Variable | Default | Descripcion |
| --- | --- | --- |
| `ERP_PHASE_URL` | `http://127.0.0.1:8484` | URL de PhaseDB |
| `ERP_PHASE_USER` / `ERP_PHASE_PASSWORD` | `admin` / `admin123` | Cuenta de servicio |
| `ERP_JWT_SECRET` | secreto demo | Firma HS256 (minimo 32 bytes) |
| `ERP_JWT_EXPIRATION` | `8h` | Vigencia del token |
| `ERP_CURRENCY` | `USD` | Moneda de la demo |
| `ERP_IVA_RATE` | `0.00` | IVA como fraccion (`0.19` = 19%) |
| `ERP_RUT_DV` | `false` | Validar digito verificador del RUT |
| `ERP_CORS_ORIGINS` | localhost:5500/8080 | Origenes permitidos |
| `ERP_SEED_ENABLED` | `true` | Crear admin inicial |
| `ERP_MIGRATION_CLIENTES` | `true` | Migrar clientes legacy |

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
| GET | `/api/v1/facturas`, `/api/v1/facturas/{id}` | autenticado |
| POST/PUT/PATCH | `/api/v1/facturas...` | ADMIN, CONTABILIDAD |
| GET | `/api/v1/facturas/{id}/pdf` | autenticado |
| POST | `/api/v1/facturas/{id}/enviar` | ADMIN, CONTABILIDAD |
| GET | `/api/v1/facturas/{id}/envios` | autenticado |

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

## Reglas de negocio

- Los montos se manejan con `BigDecimal`; la moneda y el IVA son configurables.
- El saldo de una factura = total - pagos vigentes - notas de credito vigentes.
- Un pago no puede superar el saldo pendiente.
- Una nota de tipo `ANULACION` anula la factura; al anular la nota se reabre.
- La cartera se deriva en linea de facturas, pagos y notas (no se duplica).
- La conciliacion es **simulada**: no importa CSV ni se conecta al banco. El
  boton "Importar cartola" corresponde a `POST /conciliacion/simular`.
- El PDF de factura se genera realmente con OpenPDF.
- El envio por correo es **simulado**: queda registrado en `^ENVIOS` con estado
  `SIMULADO`, sin SMTP.

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
| `^MIGRACION` | Marcadores de migracion |

## Pruebas

Las pruebas automatizadas fueron retiradas del proyecto: el equipo de QA ejecuta
las pruebas manuales descritas en `guia de pruebas.md` desde Swagger UI.
