# ERP-DEMO (frontend)

Frontend estático del ERP Empresarial. El código fuente está en **TypeScript**
(`src/`) y se compila a **JavaScript ES Modules** en `dist/`. La única entrada
es `dist/main.js`.

`dist/` se genera con `npm run build` y está ignorado por git: el repositorio
solo conserva el código TypeScript y los HTML/CSS.

## Requisitos

- Node.js 18 o superior.
- API del ERP disponible en `http://localhost:8080/api/v1`.
- PhaseDB disponible (la API lo requiere para operar).

## Comandos

```powershell
npm install
npm run build     # compila src/*.ts -> dist/*.js
npm run watch     # recompila al guardar cambios
npm run serve     # sirve el frontend en http://localhost:5500
npm start         # build + serve
```

No abras los HTML con `file://`: el navegador bloquea las llamadas a la API.
Usa siempre el servidor local.

## Configuración de la API

Por defecto se usa `http://<host-actual>:8080/api/v1`. Se puede cambiar sin
recompilar desde la consola del navegador:

```js
localStorage.setItem('erp.apiBase', 'http://192.168.1.10:8080/api/v1');
localStorage.removeItem('erp.apiBase'); // volver al valor por defecto
```

## Estructura

| Archivo | Responsabilidad |
| --- | --- |
| `src/main.ts` | Punto de entrada; inicializa layout y despacha la página según `data-page` |
| `src/config.ts` | URL base de la API y claves de almacenamiento |
| `src/storage.ts` | Sesión (`erp.token`, `erp.user`), rol y moneda |
| `src/api.ts` | Cliente `fetch`, `ApiError` y métodos de todos los endpoints |
| `src/ui.ts` | Helpers de DOM, formato, toasts, modales y alertas |
| `src/validation.ts` | Motor reutilizable de validación de formularios |
| `src/permissions.ts` | Roles de escritura por módulo, alineados con la API |
| `src/layout.ts` | Sidebar dinámico, guardia de sesión/rol, cambio de contraseña y logout |
| `src/auth.ts` | Login real contra la API y redirección por rol |
| `src/dashboard.ts` | Dashboard compartido por los cuatro roles |
| `src/clientes.ts` | Listado, búsqueda, alta y edición de clientes |
| `src/presupuestos.ts` | Listado, filtros, detalle, alta/edición y transiciones de presupuestos |
| `src/operaciones.ts` | Listado, alta, vínculo con presupuesto y documentos y archivos adjuntos de operaciones |
| `src/facturacion.ts` | Facturas, detalle, PDF, envío y pagos |
| `src/pagos.ts` | Modal reutilizable de registro de pagos |
| `src/cartera.ts` | Cuentas por cobrar y pagos desde cartera |
| `src/cobranza.ts` | Recordatorios y promesas de pago |
| `src/notas.ts` | Notas de crédito y anulación |
| `src/conciliacion.ts` | Simulación, ejecución y conciliación manual |
| `src/usuarios.ts` | Listado, alta, edición y recálculo administrativo de facturas |

## Páginas

Cada página declara `data-page` en `<body>` y carga `../../dist/main.js`. Las
páginas privadas usan el `<aside class="sidebar"></aside>` vacío; `layout.ts`
lo rellena con el menú y el perfil del usuario.

| Ruta | Página |
| --- | --- |
| `Index.html` | Login |
| `views/{rol}/dashboard.html` | Dashboard del rol (`admin`, `comercial`, `operaciones`, `contabilidad`) |
| `views/compartidas/clientes.html` | Clientes |
| `views/compartidas/crear-cliente.html` | Alta/edición de cliente (`?doc=...`) |
| `views/compartidas/presupuestos.html` | Presupuestos, filtros y detalle |
| `views/compartidas/crear-presupuesto.html` | Alta/edición de presupuesto (`?id=...`) |
| `views/compartidas/operaciones.html` | Operaciones y documentos |
| `views/compartidas/crear-operaciones.html` | Alta de operación y vínculo opcional a presupuesto |
| `views/compartidas/facturacion.html` | Facturas y pagos |
| `views/compartidas/crear-factura.html` | Emisión de factura |
| `views/compartidas/cuentas-por-cobrar.html` | Cartera |
| `views/compartidas/gestion-cobranza.html` | Recordatorios y promesas |
| `views/compartidas/notas-credito.html` | Notas de crédito |
| `views/compartidas/crear-nota-credito.html` | Emisión de nota |
| `views/compartidas/conciliacion-bancaria.html` | Conciliación |
| `views/admin/users.html` | Usuarios y recálculo de estados (solo admin) |
| `views/admin/crear-usuario.html` | Alta/edición de usuario (`?email=...`) |

## Acciones transversales

- **Cambiar contraseña**: botón en el pie del menú lateral de cualquier página
  privada. Abre un modal que llama a `POST /auth/change-password` y valida la
  contraseña actual, la nueva (8–72 caracteres) y su confirmación.
- **Recalcular estados**: botón en `views/admin/users.html` (solo admin) que
  llama a `POST /admin/recalcular` y repara facturas cuyo estado quedó
  desalineado con sus pagos y notas.

## Notas

- Los formularios usan `initValidation`; los errores de la API se mapean a los
  campos mediante `details`.
- Los montos se formatean con la moneda que entrega `/dashboard`.
- Los pagos y notas usan `Idempotency-Key` generada automáticamente.
- Los presupuestos usan estados `BORRADOR`, `ENVIADO`, `APROBADO` y `RECHAZADO`;
  solo `admin` y `comercial` pueden escribir.
- La guía manual de pruebas, incluida la sección del frontend, está en
  `../api/docs/guia de pruebas.md`.
