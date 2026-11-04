# Guía de pruebas del API

## 1. Objetivo

Validar manualmente el API del ERP desde **Swagger UI**, siguiendo un orden que respete las dependencias entre módulos y permita comprobar:

- Autenticación JWT y permisos por rol.
- CRUD de clientes, presupuestos, operaciones y facturas.
- Reglas financieras: pagos, notas de crédito, cartera, cobranza y conciliación.
- Generación real de PDF y envío de correo simulado.
- Persistencia y consistencia de los datos en PhaseDB.

Esta guía cubre únicamente pruebas manuales con Swagger. Las pruebas automatizadas y unitarias quedan fuera de su alcance.

## 2. Alcance

Incluye:

- API REST en `http://localhost:8080`.
- PhaseDB en `http://127.0.0.1:8484`.
- Usuarios, roles y reglas de negocio.
- PDF real y correo simulado.

No incluye:

- Frontend `ERP-DEMO`.
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
| OPE-05 | `POST /api/v1/operaciones/{id}/documentos` | `200` con el documento agregado |
| OPE-06 | Enviar un `tipoOperacion` inválido | `422`, error de validación |

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

Documento:

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
| FAC-06 | `PATCH /api/v1/facturas/{id}/estado` con `ANULADA` | `204`; ya no admite pagos |
| FAC-07 | Enviar una factura sin items | `422`, error de validación |

Condiciones de pago: `CONTADO`, `NETO_30`, `NETO_60`.
Estados permitidos: `PENDIENTE`, `PAGADA`, `VENCIDA`, `ANULADA`.

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

Medios permitidos: `TRANSFERENCIA`, `CHEQUE`, `EFECTIVO`, `TARJETA`, `OTRO`.

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
| Regla financiera | Pago o nota mayor al saldo | `422`, código `MONTO_EXCEDE_SALDO` |
| Estado inválido | PATCH con estado no permitido | `400`, código `ESTADO_INVALIDO` |

## 9. Checklist de aceptación

- [ ] Health responde `ok` y `phase: up`.
- [ ] Login devuelve token y `/auth/me` funciona.
- [ ] Los roles sin permiso reciben `403`.
- [ ] Se puede crear, editar y dar de baja un cliente.
- [ ] Se puede crear un presupuesto y aprobarlo.
- [ ] Se puede crear una operación y agregarle un documento.
- [ ] Se puede emitir una factura con IVA 0% y totales correctos.
- [ ] Un pago parcial deja la factura pendiente y actualiza la cartera.
- [ ] Un pago total deja la factura pagada.
- [ ] Una nota de crédito reduce el saldo o anula la factura.
- [ ] La cartera y su resumen reflejan pagos y notas.
- [ ] Se registran recordatorios y promesas de cobranza.
- [ ] La conciliación simulada responde y permite conciliar.
- [ ] El PDF se descarga y contiene los datos de la factura.
- [ ] El envío de correo queda registrado como `SIMULADO`.
- [ ] El dashboard coincide con la cartera.

## 10. Problemas frecuentes

| Síntoma | Causa probable | Solución |
| --- | --- | --- |
| `401` | Token ausente, vencido o pegado con `Bearer` en Swagger | Vuelve a hacer login y pega solo el token |
| `403` | El rol del usuario no permite la operación | Usa un usuario con el rol adecuado |
| `404` | ID o documento inexistente | Verifica el valor en un `GET` de lista |
| `409` | Registro duplicado o factura anulada | Usa otro documento o revisa el estado |
| `422` | Validación o regla de negocio | Revisa el campo indicado en `details` |
| `400` | JSON inválido o estado no permitido | Corrige el body y los valores permitidos |
| `503` | PhaseDB no disponible | Inicia PhaseDB y repite el health |
| Datos inesperados | Los datos persisten entre ejecuciones | Usa IDs nuevos o consulta los listados antes de crear |

## 11. Datos de ejemplo ya disponibles

La base demo puede contener registros de pruebas anteriores. Antes de crear datos nuevos, consulta los listados para evitar duplicados.

| Dato | Valor de referencia |
| --- | --- |
| Cliente activo | `76123456-7` - Logistica del Sur SpA |
| Cliente inactivo | `21776525-0` |
| Factura pagada | `FAC-2026-0001` |
| Pago | `PAG-2026-0001`, `PAG-2026-0002` |
| Nota de crédito | `NC-2026-0001` |
| Promesa | `PROM-2026-0001` |
| Movimientos de conciliación | `MOV-2026-0001` a `MOV-2026-0003` |
| Envíos simulados | `ENV-2026-0001`, `ENV-2026-0002` |

Los IDs nuevos se generan por año con secuencias legibles: `FAC-2026-000X`, `PAG-2026-000X`, `NC-2026-000X`, etc.
