# Guía de pruebas del API

## 1. Objetivo

Validar manualmente el API del ERP desde **Swagger UI** y los flujos del frontend **ERP-DEMO** conectado a esa API, siguiendo un orden que respete las dependencias entre módulos y permita comprobar:

- Autenticación JWT y permisos por rol.
- CRUD de clientes, presupuestos, operaciones y facturas.
- Reglas financieras: pagos, notas de crédito, cartera, cobranza y conciliación.
- Generación real de PDF y envío de correo simulado.
- Persistencia y consistencia de los datos en PhaseDB.
- Seguridad operativa: bloqueo de login, usuarios inactivos y correlación de errores.
- Navegación, formularios y validaciones del frontend `ERP-DEMO`.

Esta guía cubre únicamente pruebas manuales. Las pruebas automatizadas y unitarias quedan fuera de su alcance.

## 2. Alcance

Incluye:

- API REST en `http://localhost:8080`.
- Frontend `ERP-DEMO` servido en `http://localhost:5500`.
- PhaseDB en `http://127.0.0.1:8484`.
- Usuarios, roles y reglas de negocio.
- PDF real y correo simulado.

No incluye:

- Envío real de correo por SMTP.
- Integración bancaria o importación de cartolas CSV.
- Pruebas de carga, rendimiento o penetración.

## 3. Antes de empezar

| Elemento | Valor |
| --- | --- |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Health | `GET /api/v1/health` |
| Usuario administrador | `admin@empresa.com / admin123` |
| Usuario comercial de apoyo | `comercial@empresa.com / comercial123` |
| Moneda | USD |
| IVA | 0% |
| Formato de fechas | `YYYY-MM-DD` |
| Perfil de la API | `dev` (por defecto) |
| Header de correlación | `X-Request-Id` (opcional) |
| Frontend | `http://localhost:5500` |
| Compilación del frontend | `npm run build` (TypeScript → `dist/`) |
| Servidor estático | `npm run serve` |

Antes de probar, abre Swagger y ejecuta `GET /api/v1/health`. Debe responder:

```json
{
  "status": "ok",
  "phase": "up"
}
```

Si responde `503` o `phase: "down"`, PhaseDB no está disponible y las pruebas de negocio fallarán.

## 4. Autenticación en Swagger

1. Abre `http://localhost:8080/swagger-ui.html`.
2. Despliega `POST /api/v1/auth/login` y pulsa **Try it out**.
3. Envía:

```json
{
  "email": "admin@empresa.com",
  "password": "admin123"
}
```

4. Copia el valor de `token` de la respuesta.
5. Pulsa el botón **Authorize**.
6. Pega **solo el token**, sin la palabra `Bearer`.
7. Confirma con **Authorize** y cierra el diálogo.

Desde ese momento Swagger enviará el header `Authorization: Bearer <token>` en cada petición protegida.

Para probar otro rol, repite el login con otro usuario y reemplaza el token en **Authorize**.

## 5. Roles y permisos

| Módulo | Lectura | Escritura |
| --- | --- | --- |
| Health y login | Público | — |
| Usuarios | Solo ADMIN | Solo ADMIN |
| Clientes | Todos los roles | ADMIN, COMERCIAL |
| Presupuestos | Todos los roles | ADMIN, COMERCIAL |
| Operaciones | Todos los roles | ADMIN, OPERACIONES |
| Facturas | Todos los roles | ADMIN, CONTABILIDAD |
| Pagos y notas de crédito | Todos los roles | ADMIN, CONTABILIDAD |
| Cartera | Todos los roles | — |
| Cobranza | Todos los roles | ADMIN, CONTABILIDAD |
| Conciliación | Todos los roles | ADMIN, CONTABILIDAD |
| Dashboard y PDF | Todos los roles | — |
| Envío de factura por correo | — | ADMIN, CONTABILIDAD |

Prueba de seguridad mínima:

1. Sin token, `GET /api/v1/clientes` debe devolver **401**.
2. Con token de `COMERCIAL`, `GET /api/v1/usuarios` debe devolver **403**.
3. Con token de `COMERCIAL`, `POST /api/v1/pagos` debe devolver **403**.

## 6. Orden sugerido de ejecución

| Orden | Módulo | Objetivo | Depende de |
| ---: | --- | --- | --- |
| 1 | Health | Confirmar API y PhaseDB | — |
| 2 | Autenticación | Obtener token y validar sesión | Health |
| 3 | Roles | Confirmar accesos permitidos y denegados | Login |
| 4 | Usuarios | Crear usuarios de apoyo para QA | Login ADMIN |
| 5 | Clientes | Tener un cliente para el flujo financiero | Login |
| 6 | Presupuestos | Crear la base comercial | Cliente |
| 7 | Operaciones | Asociar la ejecución al presupuesto | Cliente y presupuesto |
| 8 | Facturas | Emitir el documento a cobrar | Cliente y operación |
| 9 | Pagos | Aplicar pagos parciales y totales | Factura |
| 10 | Notas de crédito | Ajustar o anular el saldo | Factura |
| 11 | Cartera | Verificar saldos, vencimientos y resumen | Facturas, pagos y notas |
| 12 | Cobranza | Registrar recordatorios y promesas | Cartera |
| 13 | Conciliación | Simular el cruce bancario | Cartera |
| 14 | PDF y correo | Validar documentos y envío simulado | Factura |
| 15 | Dashboard | Comparar los agregados finales | Todos los módulos |

Los mismos módulos se pueden validar desde la interfaz en la **sección 12**, que recorre login, navegación por rol, formularios, cartera, cobranza y conciliación.

## 7. Casos por módulo

### 7.1 Health

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| HEALTH-01 | `GET /api/v1/health` sin token | `200`, `status: ok`, `phase: up` |
| HEALTH-02 | Detener PhaseDB y repetir | `503`, `status: degraded`, `phase: down` |

### 7.2 Autenticación

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| AUTH-01 | Login con credenciales correctas | `200`, `token`, `tokenType: Bearer`, `expiresIn: 28800` |
| AUTH-02 | `GET /api/v1/auth/me` con el token | `200`, email, rol y datos del usuario |
| AUTH-03 | Login con password incorrecta | `401` |
| AUTH-04 | `GET /api/v1/auth/me` sin token | `401` |
| AUTH-05 | `POST /api/v1/auth/change-password` con un usuario de prueba | `204`; el login con la clave anterior devuelve `401` |

Para AUTH-05 no uses el administrador principal salvo que luego restaures la clave.

### 7.3 Usuarios

Solo disponibles con token `ADMIN`.

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| USR-01 | `GET /api/v1/usuarios` | `200` con lista paginada |
| USR-02 | `POST /api/v1/usuarios` con un usuario QA | `201`, sin exponer `password` |
| USR-03 | Repetir el mismo email | `409`, código `USUARIO_EXISTE` |
| USR-04 | Crear con `password` distinto de `passwordConfirm` | `400`, código `PASSWORD_NO_COINCIDE` |
| USR-05 | `PATCH /api/v1/usuarios/{email}/estado` con `INACTIVO` | `200` con el usuario inactivo |
| USR-06 | Login con el usuario inactivo | `401` |
| USR-07 | `GET /api/v1/usuarios` con rol no ADMIN | `403` |

Body de ejemplo:

```json
{
  "email": "qa.comercial@empresa.com",
  "nombres": "QA",
  "apellidos": "Comercial",
  "rol": "COMERCIAL",
  "estado": "ACTIVO",
  "permisos": ["clientes:leer"],
  "password": "qa123456",
  "passwordConfirm": "qa123456"
}
```

### 7.4 Clientes

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| CLI-01 | `GET /api/v1/clientes?page=0&size=20` | `200` con clientes y paginación |
| CLI-02 | `GET /api/v1/clientes/buscar?q=logistica` | `200` con coincidencias por nombre |
| CLI-03 | `POST /api/v1/clientes` con un RUT nuevo | `201`, documento normalizado sin puntos |
| CLI-04 | Repetir el mismo RUT | `409`, código `CLIENTE_EXISTE` |
| CLI-05 | Crear con RUT inválido | `422`, código `RUT_INVALIDO` |
| CLI-06 | `PUT /api/v1/clientes/{numDoc}` | `200` con los datos actualizados |
| CLI-07 | `DELETE /api/v1/clientes/{numDoc}` | `204`; el cliente queda con `activo: false` |
| CLI-08 | `GET /api/v1/clientes/{numDoc}` tras la baja | `200` con `activo: false` |

Body de ejemplo:

```json
{
  "nombre": "Cliente QA SpA",
  "glosa": "Cliente creado para pruebas manuales",
  "tipoDoc": "RUT",
  "numDoc": "76.543.210-K",
  "pais": "cl",
  "region": "rm",
  "direccion": "Av. QA 123, Santiago",
  "telefono": "+56911112222",
  "email": "qa@cliente.cl"
}
```

El documento se normaliza a `76543210-K`.

### 7.5 Presupuestos

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| PRE-01 | `POST /api/v1/presupuestos` | `201`, id `PRE-AAAA-0001`, totales calculados |
| PRE-02 | `GET /api/v1/presupuestos` | `200` con la lista |
| PRE-03 | `PUT /api/v1/presupuestos/{id}` | `200` con los cambios |
| PRE-04 | `PATCH /api/v1/presupuestos/{id}/estado` con `APROBADO` | `204` |
| PRE-05 | Enviar un estado no permitido | `400`, código `ESTADO_INVALIDO` |

Estados permitidos: `BORRADOR`, `ENVIADO`, `APROBADO`, `RECHAZADO`.

Body de ejemplo:

```json
{
  "rutCliente": "76123456-7",
  "fechaEmision": "2026-11-03",
  "estado": "BORRADOR",
  "items": [
    {
      "descripcion": "Servicio de prueba QA",
      "cantidad": 1,
      "precioUnitario": 1000
    }
  ]
}
```

### 7.6 Operaciones

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| OPE-01 | `POST /api/v1/operaciones` | `201`, id `OP-AAAA-0001` |
| OPE-02 | `GET /api/v1/operaciones` y `/{id}` | `200` con la operación |
| OPE-03 | `PUT /api/v1/operaciones/{id}` | `200` con los cambios |
| OPE-04 | `PATCH /api/v1/operaciones/{id}/estado` con `EN_CURSO` | `204` |
| OPE-05 | `POST /api/v1/operaciones/{id}/documentos/archivo` con `tipo`, `numero`, `fecha` y `archivo` | `200` con el documento y su `archivoToken` |
| OPE-06 | Enviar el formulario de archivo sin `archivo` | `422`, detalle de validación en `archivo` |
| OPE-07 | `GET /api/v1/operaciones/{id}/documentos/{token}/archivo` | `200`, descarga el archivo con su nombre original |
| OPE-08 | Subir un archivo de más de 10 MB | `413`, código `ARCHIVO_DEMASIADO_GRANDE` |
| OPE-09 | Enviar un `tipoOperacion` inválido | `422`, error de validación |

Estados permitidos: `PLANIFICADA`, `EN_CURSO`, `FINALIZADA`, `CANCELADA`, `ATRASADO`.
Tipos permitidos: `IMPORTACION`, `EXPORTACION`, `NACIONAL`.
Vías permitidas: `MARITIMA`, `AEREA`, `TERRESTRE`, `MULTIMODAL`.

Body de ejemplo:

```json
{
  "rutCliente": "76123456-7",
  "tipoOperacion": "IMPORTACION",
  "viaTransporte": "MARITIMA",
  "referencia": "QA-OPE-001",
  "fechaInicio": "2026-11-03",
  "estado": "PLANIFICADA",
  "responsableAsignado": "Equipo QA"
}
```

Si quieres asociarla a un presupuesto, agrega `idPresupuestoOrigen` con el ID creado en el paso anterior.

Documento con archivo real (`multipart/form-data`):

```bash
curl -X POST "http://localhost:8080/api/v1/operaciones/OP-2026-0001/documentos/archivo" \
  -H "Authorization: Bearer $TOKEN" \
  -F "tipo=BL" \
  -F "numero=BL-QA-001" \
  -F "fecha=2026-11-03" \
  -F "archivo=@./bl-qa-001.pdf"
```

La respuesta incluye el documento con `archivo` (nombre original) y `archivoToken`.
Guarda ese token para descargar el archivo:

```bash
curl -o bl-qa-001.pdf \
  "http://localhost:8080/api/v1/operaciones/OP-2026-0001/documentos/{archivoToken}/archivo" \
  -H "Authorization: Bearer $TOKEN"
```

Los archivos se guardan en `api/uploads/operaciones/{id}/` (configurable con
`ERP_UPLOADS_DIR`). El límite es de 10 MB por archivo. El endpoint JSON
`POST /api/v1/operaciones/{id}/documentos` sigue disponible para registrar
referencias de texto en documentos históricos:

```json
{
  "tipo": "BL",
  "numero": "BL-QA-001",
  "fecha": "2026-11-03",
  "archivo": "bl-qa-001.pdf"
}
```

### 7.7 Facturas

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| FAC-01 | `POST /api/v1/facturas` | `201`, id `FAC-AAAA-0001` |
| FAC-02 | Revisar totales | `subtotal = total`, `impuestos = 0.00` |
| FAC-03 | Revisar vencimiento | `fechaEmision + 30 días` para `NETO_30` |
| FAC-04 | `GET /api/v1/facturas` y `/{id}` | `200` con la factura |
| FAC-05 | `PUT /api/v1/facturas/{id}` | `200` con los cambios |
| FAC-06 | `PATCH /api/v1/facturas/{id}/estado` con `ANULADA` (sin pagos) | `204`; ya no admite pagos |
| FAC-07 | Enviar una factura sin items | `422`, error de validación |
| FAC-08 | Emitir con `fechaEmision: "2026-13-40"` | `422`, código `FECHA_INVALIDA` |
| FAC-09 | `PUT /api/v1/facturas/{id}` sobre una factura con pagos | `409`, código `FACTURA_CON_MOVIMIENTOS` |
| FAC-10 | `PATCH /api/v1/facturas/{id}/estado` con `PAGADA` | `409`, código `TRANSICION_INVALIDA` |

Condiciones de pago: `CONTADO`, `NETO_30`, `NETO_60`.
Estados permitidos: `PENDIENTE`, `PAGADA`, `VENCIDA`, `ANULADA`.
`PAGADA` y `VENCIDA` los asigna el sistema; no se aceptan por `PATCH`.

Body de ejemplo:

```json
{
  "rutCliente": "76123456-7",
  "condicionPago": "NETO_30",
  "fechaEmision": "2026-11-03",
  "items": [
    {
      "descripcion": "Servicio de prueba QA",
      "cantidad": 2,
      "precioUnitario": 500
    }
  ]
}
```

Opcional: agrega `idOperacionOrigen` con el ID de la operación creada en el paso anterior.

Resultado esperado: `subtotal: 1000`, `impuestos: 0.00`, `total: 1000`, `estado: PENDIENTE`.

### 7.8 Pagos

Usa una factura recién emitida y pendiente.

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| PAG-01 | `POST /api/v1/pagos` con monto 400 sobre una factura de 1000 | `201`, pago `REGISTRADO` |
| PAG-02 | Consultar la factura | Sigue `PENDIENTE` |
| PAG-03 | `GET /api/v1/pagos?facturaId={id}` | `200` con el pago |
| PAG-04 | Intentar pagar 601 sobre un saldo de 600 | `422`, código `MONTO_EXCEDE_SALDO` |
| PAG-05 | Pagar el saldo restante | `201`; la factura pasa a `PAGADA` |
| PAG-06 | `POST /api/v1/pagos/{id}/anular` | `200`; la factura vuelve a `PENDIENTE` |
| PAG-07 | Intentar pagar una factura `ANULADA` | `409`, código `FACTURA_ANULADA` |
| PAG-08 | Enviar un `medioPago` inválido | `400`, código `MEDIO_INVALIDO` |
| PAG-09 | Repetir el pago con el mismo `Idempotency-Key` | `200`, header `Idempotent-Replay: true` y el mismo pago |
| PAG-10 | Enviar `fecha: "31-12-2026"` | `422`, código `FECHA_INVALIDA` |

Medios permitidos: `TRANSFERENCIA`, `CHEQUE`, `EFECTIVO`, `TARJETA`, `OTRO`.

Para PAG-09 agrega el header `Idempotency-Key: prueba-001` en ambas llamadas.
Si repites la clave con un monto distinto, responde `409 IDEMPOTENCIA_CONFLICTO`.

Body de ejemplo:

```json
{
  "facturaId": "FAC-2026-0001",
  "monto": 400,
  "medioPago": "TRANSFERENCIA",
  "referencia": "TRX-QA-001",
  "observacion": "Pago parcial de prueba"
}
```

### 7.9 Notas de crédito

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| NC-01 | `POST /api/v1/notas-credito` tipo `DESCUENTO` | `201`, id `NC-AAAA-0001` |
| NC-02 | Consultar cartera de la factura | El pendiente baja por el monto de la nota |
| NC-03 | Intentar un descuento mayor al saldo neto | `422`, código `MONTO_EXCEDE_SALDO` |
| NC-04 | `POST /api/v1/notas-credito` tipo `ANULACION` | La factura pasa a `ANULADA` |
| NC-05 | `POST /api/v1/notas-credito/{id}/anular` | `200`; la factura se reabre como `PENDIENTE` |
| NC-06 | Emitir una nota sobre una factura anulada | `409`, código `FACTURA_ANULADA` |
| NC-07 | Emitir con `fechaEmision: "ayer"` | `422`, código `FECHA_INVALIDA` |

Tipos permitidos: `DESCUENTO`, `DEVOLUCION`, `ANULACION`.
Estados: `EMITIDA`, `ANULADA`.

Body de descuento:

```json
{
  "facturaId": "FAC-2026-0001",
  "tipo": "DESCUENTO",
  "montoNeto": 100,
  "motivo": "Descuento comercial de prueba",
  "fechaEmision": "2026-11-03"
}
```

Para `ANULACION` no envíes `montoNeto`: el API calcula el saldo restante.

### 7.10 Cartera

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| CAR-01 | `GET /api/v1/cuentas-por-cobrar` | `200` con todas las cuentas |
| CAR-02 | `GET /api/v1/cuentas-por-cobrar/{facturaId}` | `200` con la cuenta de esa factura |
| CAR-03 | `GET /api/v1/cuentas-por-cobrar/resumen` | `200` con totales y contadores |
| CAR-04 | Verificar una factura pagada | `montoPendiente: 0`, `estado: PAGADA` |
| CAR-05 | Verificar una factura con vencimiento pasado | `estado: ATRASADO` y `diasAtraso > 0` |

Estados de cartera:

- `AL_DIA`: pendiente mayor a cero y vencimiento lejano.
- `POR_VENCER`: vence en 7 días o menos.
- `ATRASADO`: entre 1 y 60 días de atraso.
- `CRITICO`: más de 60 días de atraso.
- `PAGADA`: sin saldo pendiente.

Fórmula:

```text
montoPendiente = total - pagos vigentes - notas de crédito vigentes
```

El resumen debe cumplir:

```text
totalPorCobrar = suma de montoPendiente de facturas no pagadas
totalVencido   = suma de pendientes ATRASADO + CRITICO
facturasPendientes = facturas con saldo
facturasPagadas    = facturas sin saldo
```

### 7.11 Cobranza

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| COB-01 | `POST /api/v1/cobranza/recordatorios` con `EMAIL` | `201`, estado `SIMULADO` |
| COB-02 | Omitir asunto y mensaje | El API genera un texto sugerido |
| COB-03 | `GET /api/v1/cobranza/recordatorios?facturaId={id}` | `200` con el historial |
| COB-04 | `POST /api/v1/cobranza/recordatorios/masivo` | `200` con un recordatorio por factura pendiente |
| COB-05 | `POST /api/v1/cobranza/promesas` | `201`, id `PROM-AAAA-0001`, estado `PENDIENTE` |
| COB-06 | `PATCH /api/v1/cobranza/promesas/{id}/estado` con `CUMPLIDA` | `200` con el estado actualizado |
| COB-07 | Enviar un canal inválido | `400`, código `CANAL_INVALIDO` |

Canales permitidos: `EMAIL`, `WHATSAPP`.
Estados de promesa: `PENDIENTE`, `CUMPLIDA`, `INCUMPLIDA`.

Recordatorio:

```json
{
  "facturaId": "FAC-2026-0001",
  "canal": "EMAIL",
  "asunto": "Recordatorio de pago",
  "mensaje": "Mensaje de prueba"
}
```

Promesa:

```json
{
  "facturaId": "FAC-2026-0001",
  "fechaPromesa": "2026-11-15",
  "monto": 500,
  "nota": "Promesa registrada por QA"
}
```

### 7.12 Conciliación bancaria

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| CON-01 | `GET /api/v1/conciliacion` | `200` con saldo, totales y movimientos |
| CON-02 | `GET /api/v1/conciliacion/movimientos` | `200` con la lista |
| CON-03 | `POST /api/v1/conciliacion/simular` | `200` con movimientos simulados |
| CON-04 | `POST /api/v1/conciliacion/ejecutar` | `200`; intenta cruzar movimientos pendientes |
| CON-05 | `POST /api/v1/conciliacion/movimientos/{id}/conciliar` | `200` con el movimiento `CONCILIADO` |
| CON-06 | Conciliar dos veces el mismo movimiento | `409`, código `YA_CONCILIADO` |

Body para conciliar:

```json
{
  "documentoErp": "FAC-2026-0001"
}
```

La conciliación es simulada: no importa CSV ni consulta al banco. `simular` solo crea movimientos de ejemplo si `^CONCILIACION` está vacío; si ya existen, devuelve el resumen actual.

### 7.13 PDF y correo simulado

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| DOC-01 | `GET /api/v1/facturas/{id}/pdf` | `200`, `application/pdf`, archivo descargable |
| DOC-02 | Abrir el PDF | Debe contener ERP Empresarial, cliente, ítems y totales |
| DOC-03 | `POST /api/v1/facturas/{id}/enviar` con `{}` | `200`, estado `SIMULADO`, usa el email del cliente |
| DOC-04 | Enviar con destinatario, asunto y mensaje propios | `200` con esos valores |
| DOC-05 | `GET /api/v1/facturas/{id}/envios` | `200` con el historial de envíos |
| DOC-06 | Enviar una factura cuyo cliente no existe o no tiene email, sin destinatario | `400`, código `DESTINATARIO_REQUERIDO` |

Body opcional:

```json
{
  "destinatario": "cobranzas@cliente.cl",
  "asunto": "Factura pendiente",
  "mensaje": "Adjuntamos su factura."
}
```

El correo no sale realmente: queda registrado en `^ENVIOS` con estado `SIMULADO`.

### 7.14 Dashboard

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| DASH-01 | `GET /api/v1/dashboard` | `200` con todos los indicadores |
| DASH-02 | Comparar `pendienteCobro` con el resumen de cartera | Deben coincidir |
| DASH-03 | Comparar `facturasPagadas` y `facturasPendientes` | Deben coincidir con cartera |
| DASH-04 | Revisar `moneda` e `ivaRate` | `USD` y `0.00` |

Campos principales: `facturadoMes`, `pendienteCobro`, `montoVencido`, `pagosMes`, `clientesActivos`, `operacionesActivas`, `presupuestosPendientes`, `facturasPendientes`, `facturasVencidas`, `facturasPagadas`, `moneda`, `ivaRate` y `cartera`.

### 7.15 Administración

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| ADM-01 | `POST /api/v1/admin/recalcular` con token ADMIN | `200` con `facturas` y `corregidas` |
| ADM-02 | Repetir con token COMERCIAL | `403` |
| ADM-03 | Alterar un estado manualmente en Phase y recalcular | El estado vuelve a reflejar pagos y notas |

Este endpoint repara facturas cuyo estado haya quedado desalineado por un fallo
entre el movimiento y la actualización del estado.

### 7.16 Seguridad y correlación

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| SEC-01 | Login enviando `X-Request-Id: qa-sec-01` | La respuesta repite `X-Request-Id: qa-sec-01` |
| SEC-02 | Login sin `X-Request-Id` | La respuesta genera un UUID en el header |
| SEC-03 | Provocar `401`, `403` o `404` | El body incluye `requestId` igual al header |
| SEC-04 | Fallar el login 5 veces con un correo de prueba | `401` en los cinco intentos |
| SEC-05 | Sexto intento fallido con el mismo correo | `429`, `LOGIN_BLOQUEADO` y header `Retry-After` |
| SEC-06 | Login correcto con el correo bloqueado antes del vencimiento | `429` |
| SEC-07 | Dar de baja un usuario con sesión activa y usar su token | `401`, mensaje `La cuenta esta inactiva` |
| SEC-08 | Reactivar el usuario y reutilizar el token | `200`; la cache se invalida al cambiar el estado |
| SEC-09 | Detener PhaseDB y consultar un endpoint con token | `503` o `504` con código sanitizado |
| SEC-10 | Revisar el log de una petición con error (opcional) | El detalle de PhaseDB aparece con el mismo `requestId` |

Notas:

- El bloqueo de login se cuenta por correo + IP y aplica incluso a correos
  inexistentes. Dura 15 minutos; usa un correo de prueba para no bloquear al
  administrador.
- La revalidación de usuarios usa una cache de 30 segundos; el cambio de estado
  desde `PATCH /api/v1/usuarios` la invalida de inmediato.
- El limitador de login y la cache de usuarios son locales a una instancia.
- En el perfil `prod`, Swagger UI y OpenAPI están deshabilitados y responden
  `401` sin token o `404` con token.

## 8. Pruebas negativas transversales

| Escenario | Acción | Resultado esperado |
| --- | --- | --- |
| Sin token | Cualquier endpoint protegido | `401` |
| Token inválido o expirado | Cualquier endpoint protegido | `401` |
| Rol insuficiente | Operación no permitida | `403`, código `SIN_PERMISO` |
| Recurso inexistente | `GET` con ID o documento falso | `404` |
| Duplicado | Cliente o usuario ya existente | `409` |
| Validación de campos | Body con campos obligatorios vacíos | `422`, código `VALIDACION` |
| JSON malformado | Body con sintaxis inválida | `400`, código `JSON_INVALIDO` |
| Archivo demasiado grande | Documento de operación de más de 10 MB | `413`, código `ARCHIVO_DEMASIADO_GRANDE` |
| Archivo no adjunto | `POST .../documentos/archivo` sin parte `archivo` | `422`, código `VALIDACION` |
| Token de archivo inexistente | `GET .../documentos/{token}/archivo` con token falso | `404`, código `NO_ENCONTRADO` |
| Regla financiera | Pago o nota mayor al saldo | `422`, código `MONTO_EXCEDE_SALDO` |
| Estado inválido | PATCH con estado no permitido | `400`, código `ESTADO_INVALIDO` |
| Fecha inválida | Cualquier fecha mal formada | `422`, código `FECHA_INVALIDA` |
| Transición inválida | PATCH a `PAGADA` o reactivar con pagos | `409`, código `TRANSICION_INVALIDA` |
| Edición bloqueada | `PUT` de factura con pagos o notas | `409`, código `FACTURA_CON_MOVIMIENTOS` |
| Idempotencia | Misma clave con cuerpo distinto | `409`, código `IDEMPOTENCIA_CONFLICTO` |
| Operación en curso | Dos pagos simultáneos con la misma clave | `409`, código `OPERACION_EN_CURSO` |
| Login bloqueado | Sexto fallo seguido con el mismo correo | `429`, `LOGIN_BLOQUEADO`, header `Retry-After` |
| Usuario inactivo | Token vigente de un usuario dado de baja | `401`, mensaje `La cuenta esta inactiva` |
| Ruta inexistente | `GET /ruta-inexistente` con token | `404`, código `NO_ENCONTRADO` |
| PhaseDB caído | Operación con token y PhaseDB detenido | `503`, código sanitizado, sin detalle interno |

## 9. Checklist de aceptación

- [ ] Health responde `ok` y `phase: up`.
- [ ] Login devuelve token y `/auth/me` funciona.
- [ ] Los roles sin permiso reciben `403`.
- [ ] Se puede crear, editar y dar de baja un cliente.
- [ ] Se puede crear un presupuesto y aprobarlo.
- [ ] Se puede crear una operación, adjuntar un archivo y descargarlo.
- [ ] Se puede emitir una factura con IVA 0% y totales correctos.
- [ ] Un pago parcial deja la factura pendiente y actualiza la cartera.
- [ ] Un pago total deja la factura pagada.
- [ ] Una nota de crédito reduce el saldo o anula la factura.
- [ ] Una fecha inválida devuelve `422 FECHA_INVALIDA`.
- [ ] Un cambio de estado no permitido devuelve `409 TRANSICION_INVALIDA`.
- [ ] Repetir un pago con la misma `Idempotency-Key` no crea un segundo pago.
- [ ] La cartera y su resumen reflejan pagos y notas.
- [ ] Se registran recordatorios y promesas de cobranza.
- [ ] La conciliación simulada responde y permite conciliar.
- [ ] El PDF se descarga y contiene los datos de la factura.
- [ ] El envío de correo queda registrado como `SIMULADO`.
- [ ] El dashboard coincide con la cartera.
- [ ] `X-Request-Id` se repite en la respuesta y aparece en los errores.
- [ ] El sexto login fallido devuelve `429 LOGIN_BLOQUEADO` con `Retry-After`.
- [ ] Un usuario inactivo recibe `401` con `La cuenta esta inactiva`.
- [ ] Los errores de PhaseDB no exponen el detalle interno.
- [ ] `npm run build` del frontend termina sin errores.
- [ ] El frontend se sirve en `http://localhost:5500` y el login usa la API real.
- [ ] El dashboard y los menús se adaptan al rol autenticado.
- [ ] Los formularios de cliente, operación, factura, nota y usuario validan antes de enviar.
- [ ] La cartera se actualiza al registrar pagos o anular notas.
- [ ] Cerrar sesión limpia el token y vuelve al login.

## 10. Problemas frecuentes

| Síntoma | Causa probable | Solución |
| --- | --- | --- |
| `401` | Token ausente, vencido o pegado con `Bearer` en Swagger | Vuelve a hacer login y pega solo el token |
| `403` | El rol del usuario no permite la operación | Usa un usuario con el rol adecuado |
| `404` | ID o documento inexistente | Verifica el valor en un `GET` de lista |
| `409` | Registro duplicado o factura anulada | Usa otro documento o revisa el estado |
| `422` | Validación o regla de negocio | Revisa el campo indicado en `details` |
| `400` | JSON inválido o estado no permitido | Corrige el body y los valores permitidos |
| `429` | Demasiados intentos de login fallidos | Espera el tiempo indicado en `Retry-After` |
| `401` con `La cuenta esta inactiva` | El usuario fue dado de baja | Reactívalo o usa otro usuario |
| `503` | PhaseDB no disponible | Inicia PhaseDB y repite el health |
| Swagger `401`/`404` | Perfil `prod` con Swagger deshabilitado | Usa el perfil `dev` para QA |
| Datos inesperados | Los datos persisten entre ejecuciones | Usa IDs nuevos o consulta los listados antes de crear |

## 11. Datos de ejemplo ya disponibles

La base demo puede contener registros de pruebas anteriores. Antes de crear datos nuevos, consulta los listados para evitar duplicados.

| Dato | Valor de referencia |
| --- | --- |
| Cliente activo | `76123456-7` - Logistica del Sur SpA |
| Cliente inactivo | `21776525-0` |
| Presupuesto aprobado | `PRE-6A22A6DC` (476000) |
| Factura con abono parcial | `FAC-2026-0001` (1200, pagos por 500) |
| Pago | `PAG-2026-0001`, `PAG-2026-0002` |
| Nota de crédito | `NC-2026-0001` |
| Promesa | `PROM-2026-0001` |
| Movimientos de conciliación | `MOV-2026-0001` a `MOV-2026-0003` |
| Envíos simulados | `ENV-2026-0001`, `ENV-2026-0002` |

Los IDs nuevos se generan por año con secuencias legibles: `FAC-2026-000X`, `PAG-2026-000X`, `NC-2026-000X`, etc.

## 12. Pruebas manuales del frontend ERP-DEMO

### 12.1 Preparación

El frontend es estático, se sirve por HTTP y consume la API real. **No abras los HTML con `file://`**, porque el navegador bloqueará las llamadas a la API.

```powershell
cd ERP-DEMO
npm install
npm run build
npm run serve
```

Luego abre `http://localhost:5500`.

Condiciones previas:

- API en `http://localhost:8080` con perfil `dev`.
- PhaseDB disponible en `http://127.0.0.1:8484`.
- CORS de la API habilitado para `http://localhost:5500` y `http://127.0.0.1:5500`.

Si la API está en otro host o puerto, define la base antes de iniciar sesión:

```js
localStorage.setItem('erp.apiBase', 'http://192.168.1.10:8080/api/v1');
```

Para volver al valor por defecto:

```js
localStorage.removeItem('erp.apiBase');
```

> El código fuente está en `ERP-DEMO/src/*.ts` y se compila a `ERP-DEMO/dist/*.js` (carpeta ignorada por git). Después de cambiar un `.ts`, ejecuta `npm run build` o deja `npm run watch` activo.

### 12.2 Casos transversales

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| FR-01 | Abrir `http://localhost:5500` | Se muestra el login sin errores en la consola |
| FR-02 | Abrir un HTML con `file://` | El login falla por CORS o red; se recomienda usar el servidor |
| FR-03 | Recargar una página privada con sesión activa | La sesión se conserva y la página carga |
| FR-04 | Cerrar sesión y volver atrás | La sesión se limpia y redirige al login |
| FR-05 | Detener la API y navegar | Mensaje de conexión, sin pantalla en blanco |
| FR-06 | Abrir la consola del navegador | No debe haber errores de importación de módulos |
| FR-07 | Iniciar sesión como comercial y abrir `views/compartidas/crear-factura.html` | Redirige al dashboard del comercial con aviso de permisos |
| FR-08 | Iniciar sesión como contabilidad y abrir `views/compartidas/crear-cliente.html` | Redirige al dashboard de contabilidad con aviso de permisos |

### 12.3 Login, redirección por rol y sesión

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| FR-LOG-01 | Enviar el formulario vacío | Errores junto a «Correo electrónico» y «Contraseña»; no se envía la petición |
| FR-LOG-02 | Email con formato inválido | Error «no tiene un formato válido» |
| FR-LOG-03 | Contraseña incorrecta | Mensaje «Correo o contraseña incorrectos» |
| FR-LOG-04 | Login con `admin@empresa.com / admin123` | Redirige a `views/admin/dashboard.html` |
| FR-LOG-05 | Login con `comercial@empresa.com / comercial123` | Redirige a `views/comercial/dashboard.html` |
| FR-LOG-06 | Login con un usuario de operaciones creado en QA | Redirige a `views/operaciones/dashboard.html` |
| FR-LOG-07 | Login con un usuario de contabilidad creado en QA | Redirige a `views/contabilidad/dashboard.html` |
| FR-LOG-08 | Repetir 5 veces una contraseña incorrecta con un correo de prueba | El siguiente intento muestra el bloqueo temporal y los minutos restantes |
| FR-LOG-09 | Cerrar sesión desde el menú lateral | Vuelve a `Index.html` y limpia `erp.token` y `erp.user` |
| FR-LOG-10 | Entrar como comercial y abrir manualmente `views/admin/users.html` | Redirige al dashboard del comercial con aviso de permisos |
| FR-LOG-11 | Abrir `views/compartidas/clientes.html` sin sesión | Redirige al login |

> Para probar el bloqueo usa un correo de prueba, no la cuenta `admin@empresa.com`, para no bloquear la cuenta principal durante la demo.

### 12.4 Dashboard

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| FR-DASH-01 | Entrar como administrador | KPIs, alertas, gráfico de barras, estado del sistema y cartera prioritaria |
| FR-DASH-02 | Entrar como comercial | KPIs orientados a clientes y operaciones, y acciones comerciales |
| FR-DASH-03 | Entrar como operaciones | KPIs de operaciones activas y accesos a operaciones |
| FR-DASH-04 | Entrar como contabilidad | KPIs financieros y accesos a facturación y cartera |
| FR-DASH-05 | Comparar «Pendiente de cobro» con la pantalla de cartera | El monto coincide con el resumen de cartera |
| FR-DASH-06 | Pulsar un acceso rápido | Navega a la pantalla correspondiente sin cerrar sesión |

Los botones de acción del dashboard cambian según el rol: clientes (admin/comercial), operaciones (admin/operaciones), facturas (admin/contabilidad) y usuarios (solo admin).

### 12.5 Clientes

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| FR-CLI-01 | Abrir «Clientes» | Lista con KPIs de total, activos e inactivos |
| FR-CLI-02 | Buscar por nombre, RUT o email | La tabla se filtra mientras se escribe |
| FR-CLI-03 | Crear cliente con nombre vacío | Error de campo; no se envía la petición |
| FR-CLI-04 | Escribir un RUT como `123` | Error de formato `12345678-9` |
| FR-CLI-05 | Email inválido | Error de formato de email |
| FR-CLI-06 | Crear un cliente válido | Aviso de éxito, redirección a la lista y registro visible |
| FR-CLI-07 | Editar un cliente | Formulario precargado; el número de documento queda bloqueado |
| FR-CLI-08 | Guardar cambios | Aviso de éxito y datos actualizados en la lista |
| FR-CLI-09 | Desactivar un cliente | Confirmación, badge «Inactivo» y KPI actualizado |
| FR-CLI-10 | Reactivar el mismo cliente | Confirmación y badge «Activo» |
| FR-CLI-11 | Entrar como contabilidad u operaciones | Puede consultar la lista, pero no ve «Nuevo cliente», «Editar» ni el cambio de estado |

### 12.6 Operaciones

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| FR-OPE-01 | Abrir «Operaciones» | Lista con resumen de total, activas y finalizadas |
| FR-OPE-02 | Buscar por ID, cliente o referencia | La tabla se filtra |
| FR-OPE-03 | Crear una operación sin cliente | Error de campo y sugerencia de la lista |
| FR-OPE-04 | Crear una operación con cliente, tipo y vía | Aviso de éxito y operación visible |
| FR-OPE-05 | Abrir «Documentos» de una operación | Modal con documentos registrados y formulario con selector de archivo |
| FR-OPE-06 | Agregar un documento sin archivo | Error de campo; no se envía la solicitud |
| FR-OPE-07 | Agregar un documento con un archivo menor a 10 MB | Aparece como «Con archivo» y se habilita «Descargar» |
| FR-OPE-08 | Pulsar «Descargar» en un documento con archivo | Se descarga el archivo con su nombre original |
| FR-OPE-09 | Intentar subir un archivo de más de 10 MB | Aviso de límite y la API responde `413` si se fuerza el envío |
| FR-OPE-10 | Pulsar «Cerrar» en una operación activa | Confirmación y estado «Finalizada» |
| FR-OPE-11 | Entrar como comercial o contabilidad | Puede abrir los documentos y descargar archivos, pero no ve «Cerrar» ni el formulario para registrar documentos |

### 12.7 Presupuestos

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| FR-PRE-01 | Abrir «Presupuestos» | KPIs de total, pendientes, aprobados y monto; listado con cliente, emisión, total y estado |
| FR-PRE-02 | Buscar por número, cliente, RUT o estado y filtrar por estado | La tabla se filtra sin recargar la página |
| FR-PRE-03 | Abrir «Ver» en un presupuesto | Modal con cliente, RUT, emisión, estado, conceptos, subtotal, impuestos y total |
| FR-PRE-04 | Crear un presupuesto sin cliente | Error de campo; no se envía la petición |
| FR-PRE-05 | Dejar un concepto sin descripción, cantidad 0 o precio negativo | Aviso de validación y no se guarda |
| FR-PRE-06 | Agregar y eliminar conceptos | Las filas se agregan/eliminan y el total de línea se recalcula |
| FR-PRE-07 | Cambiar cantidad o precio | Subtotal, impuestos y total se recalculan al instante |
| FR-PRE-08 | Crear un presupuesto válido en borrador | Aviso de éxito, redirección y registro visible con estado «Borrador» |
| FR-PRE-09 | Editar un presupuesto en borrador | Formulario precargado; al guardar se actualizan conceptos y total |
| FR-PRE-10 | Pulsar «Enviar» | Confirmación y estado «Enviado» |
| FR-PRE-11 | Pulsar «Aprobar» sobre un presupuesto enviado | Confirmación y estado «Aprobado» |
| FR-PRE-12 | Pulsar «Rechazar» y luego «Reabrir» | El estado pasa a «Rechazado» y vuelve a «Borrador» |
| FR-PRE-13 | Crear una operación y elegir un presupuesto aprobado en «Presupuesto de origen» | La operación guarda `idPresupuestoOrigen` y se lista correctamente |
| FR-PRE-14 | Entrar como operaciones o contabilidad | Puede consultar y ver el detalle, pero no ve «Nuevo presupuesto», «Editar» ni los botones de estado |

El servidor calcula `subtotal`, `impuestos` y `total`; el frontend solo muestra
una previsualización. Solo `admin` y `comercial` pueden crear, editar o cambiar
el estado de un presupuesto.

### 12.8 Facturación

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| FR-FAC-01 | Abrir «Facturación» | KPIs, buscador, filtro por estado y listado |
| FR-FAC-02 | Filtrar por estado | Solo se muestran facturas de ese estado |
| FR-FAC-03 | Crear una factura sin cliente | Error de campo |
| FR-FAC-04 | Agregar y eliminar conceptos | Las filas se agregan y eliminan correctamente |
| FR-FAC-05 | Cambiar cantidad o precio | Subtotal, impuestos y total se recalculan al instante |
| FR-FAC-06 | Emitir una factura válida | Aviso de éxito, redirección y factura en la lista |
| FR-FAC-07 | Abrir «Ver detalle» | Modal con cliente, fechas, condición, ítems y totales |
| FR-FAC-08 | Descargar el PDF | Se descarga un archivo con los datos de la factura |
| FR-FAC-09 | Enviar por email | Aviso de envío simulado correcto |
| FR-FAC-10 | Registrar un pago parcial | El estado y el saldo de la factura se actualizan |
| FR-FAC-11 | Entrar como comercial y abrir una factura | No aparecen «Registrar pago» ni «Enviar por email»; el PDF sigue disponible |

### 12.9 Cuentas por cobrar y cobranza

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| FR-CAR-01 | Abrir «Cuentas por cobrar» | KPIs de total, vencido, por vencer y pagadas |
| FR-CAR-02 | Filtrar por estado | La tabla muestra solo los estados seleccionados |
| FR-CAR-03 | Buscar por factura, cliente o RUT | La tabla se filtra |
| FR-CAR-04 | Registrar un pago desde la cartera | El saldo pendiente y los KPIs se actualizan |
| FR-CAR-05 | Abrir «Gestión de cobranza» | Se muestran cartera pendiente, recordatorios y promesas |
| FR-CAR-06 | Enviar un recordatorio | Confirmación y registro en la tabla de recordatorios |
| FR-CAR-07 | Registrar una promesa con fecha válida y monto | La promesa aparece en la tabla |
| FR-CAR-08 | Marcar una promesa como cumplida o incumplida | El estado cambia y los KPIs se actualizan |
| FR-CAR-09 | Abrir cobranza desde una factura concreta | La tabla queda filtrada y aparece «Ver toda la cartera» |
| FR-CAR-10 | Entrar como comercial | Puede consultar cartera y cobranza, pero no ve «Registrar pago», «Recordatorio» ni «Promesa» |

### 12.10 Notas de crédito

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| FR-NOT-01 | Abrir «Notas de crédito» | KPIs y listado de notas |
| FR-NOT-02 | Buscar o filtrar por tipo | La tabla se filtra |
| FR-NOT-03 | Crear una nota sin factura | Error de campo |
| FR-NOT-04 | Seleccionar una factura | Se muestra el saldo pendiente disponible |
| FR-NOT-05 | Monto mayor al saldo | La API responde con el detalle y se marca el campo |
| FR-NOT-06 | Emitir una nota válida | Aviso de éxito y nota visible en la lista |
| FR-NOT-07 | Anular una nota vigente | Confirmación, estado «Anulada» y cartera recalculada |
| FR-NOT-08 | Repetir la anulación | La API responde `409` y se muestra el mensaje |
| FR-NOT-09 | Entrar como comercial | Puede consultar el listado, pero no ve «Nueva nota» ni «Anular» |

### 12.11 Conciliación bancaria

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| FR-CON-01 | Abrir «Conciliación bancaria» | KPIs y movimientos existentes |
| FR-CON-02 | Pulsar «Simular movimientos» como admin | Se generan movimientos y se actualizan los KPIs |
| FR-CON-03 | Pulsar «Conciliar automáticamente» | Los movimientos coincidentes quedan conciliados |
| FR-CON-04 | Conciliar un movimiento pendiente a mano | Se solicita el documento ERP y el estado cambia |
| FR-CON-05 | Entrar como comercial | Los botones de simular, ejecutar y conciliar no están disponibles |
| FR-CON-06 | Conciliar un movimiento ya conciliado | La API responde `409` y se muestra el mensaje |

### 12.12 Usuarios (solo administrador)

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| FR-USU-01 | Abrir «Gestión de usuarios» | KPIs y listado de usuarios |
| FR-USU-02 | Buscar por nombre, email o permiso | La tabla se filtra |
| FR-USU-03 | Filtrar por rol y estado | La tabla muestra solo las coincidencias |
| FR-USU-04 | Crear usuario con email inválido | Error de formato |
| FR-USU-05 | Crear usuario con contraseña de menos de 8 caracteres | Error de longitud |
| FR-USU-06 | Confirmar contraseña distinta | Error «Las contraseñas no coinciden» |
| FR-USU-07 | Crear usuario válido con rol comercial | Aviso de éxito y usuario en la lista |
| FR-USU-08 | Editar el rol y el estado | Formulario precargado y cambios guardados |
| FR-USU-09 | Dejar las contraseñas vacías al editar | La contraseña actual se conserva |
| FR-USU-10 | Desactivar un usuario | Badge «Inactivo» |
| FR-USU-11 | Iniciar sesión con el usuario desactivado | La API responde `401` con «La cuenta esta inactiva» |
| FR-USU-12 | Reactivar el usuario e iniciar sesión | Acceso correcto con su rol |
| FR-USU-13 | Pulsar «Recalcular estados» como admin | Confirmación; aviso con facturas revisadas y corregidas |
| FR-USU-14 | Revisar una factura con estado desalineado después del recálculo | El estado vuelve a reflejar sus pagos y notas (por ejemplo, `FAC-2026-0001` queda «Pendiente» si solo tiene un abono parcial) |

Los permisos adicionales se muestran como etiquetas en la lista. El rol define el acceso base; los permisos se suman a ese rol.

### 12.13 Cambio de contraseña

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| FR-PWD-01 | Pulsar «Cambiar contraseña» en el menú lateral | Se abre el modal con contraseña actual, nueva y confirmación |
| FR-PWD-02 | Enviar el modal vacío | Errores en los tres campos; no se envía la petición |
| FR-PWD-03 | Nueva contraseña de menos de 8 caracteres | Error de longitud |
| FR-PWD-04 | Confirmación distinta de la nueva contraseña | Error «Las contraseñas no coinciden» |
| FR-PWD-05 | Contraseña actual incorrecta | La API responde `401` y el mensaje se muestra en el modal |
| FR-PWD-06 | Cambiar la contraseña de un usuario QA y cerrar sesión | El login con la clave anterior falla y el login con la nueva funciona |
| FR-PWD-07 | Restaurar la contraseña original del usuario QA | Se puede volver a iniciar sesión con la clave inicial |

Usa un usuario de prueba para estos casos; no cambies la clave del administrador
principal sin restaurarla después.

### 12.14 Cierre y seguridad de sesión

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| FR-SEC-01 | Inspeccionar `localStorage` tras el login | Contiene `erp.token` y `erp.user`; no contiene el rol del mock anterior |
| FR-SEC-02 | Esperar a que expire el token o invalidarlo y navegar | La API responde `401` y el frontend redirige al login |
| FR-SEC-03 | Cerrar sesión | `erp.token`, `erp.user` y `erp.currency` no exponen datos sensibles |
| FR-SEC-04 | Abrir dos pestañas con sesión activa y cerrar en una | La otra pestaña vuelve al login en la siguiente petición protegida |
| FR-SEC-05 | Revisar la consola ante un error de la API | Se muestra un mensaje legible, sin trazas internas |

### 12.15 Checklist de aceptación del frontend

- [ ] `npm run build` termina sin errores.
- [ ] `npm run serve` publica el frontend en `http://localhost:5500`.
- [ ] El login real redirige al dashboard según el rol.
- [ ] El formulario de login valida campos vacíos y formatos.
- [ ] Las páginas privadas redirigen al login sin sesión.
- [ ] El guard de rol impide abrir usuarios con un rol distinto de admin.
- [ ] El menú lateral se renderiza y marca la sección activa.
- [ ] El dashboard muestra KPIs reales y accesos según el rol.
- [ ] Clientes permite listar, buscar, crear, editar y cambiar de estado.
- [ ] Presupuestos permite listar, filtrar, ver detalle, crear, editar y cambiar de estado.
- [ ] Una operación puede vincularse a un presupuesto aprobado.
- [ ] Operaciones permite crear, adjuntar/descargar archivos y cerrar.
- [ ] Facturación permite emitir, ver detalle, descargar PDF y registrar pagos.
- [ ] La cartera refleja pagos y notas.
- [ ] La cobranza registra recordatorios y promesas.
- [ ] Las notas de crédito validan el saldo y permiten anular.
- [ ] La conciliación simula, ejecuta y concilia movimientos.
- [ ] Usuarios valida correo, contraseña y confirmación.
- [ ] El botón «Recalcular estados» solo aparece para admin y repara facturas desalineadas.
- [ ] El cambio de contraseña valida los tres campos y permite entrar con la clave nueva.
- [ ] Los errores de la API se muestran junto al campo o en el aviso del formulario.
- [ ] Cerrar sesión limpia el almacenamiento y vuelve al login.
- [ ] Las pantallas principales se adaptan a una ventana angosta.

### 12.16 Problemas frecuentes del frontend

| Síntoma | Causa probable | Solución |
| --- | --- | --- |
| Pantalla en blanco | Error de importación o `dist/main.js` no existe | Ejecuta `npm run build` y revisa la consola |
| `Failed to fetch` | API detenida, URL incorrecta o CORS | Inicia la API y revisa `erp.apiBase` y los orígenes CORS |
| Redirección constante al login | Token vencido o almacenamiento inconsistente | Borra `erp.token` y `erp.user`, y vuelve a iniciar sesión |
| «No tienes permisos» | El rol no corresponde a la página | Usa el rol correcto o entra desde el menú |
| Los cambios no aparecen | Caché del navegador o compilación antigua | Recarga con `Ctrl+F5` y ejecuta `npm run build` |
| Estilos rotos | Ruta de CSS incorrecta | Verifica que la página cargue desde `http://localhost:5500` |
| Error `401` al crear un pago | El token expiró durante la edición | Vuelve a iniciar sesión y repite la operación |
| El campo de saldo no aparece | No hay facturas vigentes | Emite o reactiva una factura y recarga la página |
| Fecha de promesa rechazada | Formato distinto de `AAAA-MM-DD` | Usa el selector o escribe el formato indicado |
