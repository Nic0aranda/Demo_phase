# ERP Empresarial - demo académica

Monorepositorio de la demo del ERP:

- `api/`: API REST en Spring Boot sobre PhaseDB, con JWT, roles y Swagger.
- `ERP-DEMO/`: frontend estático en TypeScript que consume la API por `fetch`.

La API escucha en `http://localhost:8080` y el frontend en
`http://localhost:5500`.

## Requisitos

| Componente | Versión / valor |
| --- | --- |
| JDK | 17 o superior |
| Node.js | 18 o superior |
| Maven | No hace falta instalarlo: se usa `mvnw.cmd` |
| PhaseDB | Disponible en `http://127.0.0.1:8484` |
| Cuenta PhaseDB | `admin / admin123` (configurable) |

> PhaseDB debe estar iniciado **antes** que la API. La API usa la cuenta de
> servicio indicada para leer y escribir sus globales.

## Instalación y arranque de la API

Abre PowerShell en la raíz del repositorio:

```powershell
cd api

# Ajusta la ruta a tu JDK 17+ si no está en JAVA_HOME.
$env:JAVA_HOME = 'C:\ruta\al\jdk-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\mvnw.cmd spring-boot:run
```

La API queda disponible en:

- API: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/api/v1/health`

El perfil por defecto es `dev`. En ese perfil se crea el administrador inicial
(si no existe), se migran clientes legacy y se habilita Swagger. Las
credenciales demo son:

```text
admin@empresa.com / admin123
comercial@empresa.com / comercial123
```

Para compilar un JAR y ejecutarlo:

```powershell
cd api
.\mvnw.cmd clean package
java -jar target\api-0.0.1-SNAPSHOT.jar
```

Para producción usa `--spring.profiles.active=prod` y define al menos
`ERP_JWT_SECRET`, `ERP_PHASE_USER`, `ERP_PHASE_PASSWORD` y
`ERP_CORS_ORIGINS`. En `prod` Swagger queda deshabilitado.

## Instalación y arranque del frontend

En otra terminal, desde la raíz del repositorio:

```powershell
cd ERP-DEMO
npm install
npm run build
npm run serve
```

Abre **`http://localhost:5500`**. No abras los HTML con `file://`: el navegador
bloquearía las llamadas a la API por CORS.

Comandos útiles:

```powershell
npm run build   # compila src/*.ts -> dist/*.js
npm run watch   # recompila al guardar cambios
npm run serve   # sirve el frontend en http://localhost:5500
npm start       # build + serve
```

`dist/` es salida generada e ignorada por git. El repositorio conserva el
código TypeScript, los HTML y los CSS; después de cambiar un `.ts` ejecuta
`npm run build` para actualizar `dist/main.js`.

## Orden de arranque recomendado

1. Inicia PhaseDB y verifica `http://127.0.0.1:8484`.
2. Inicia la API y comprueba `http://localhost:8080/api/v1/health` (`phase: up`).
3. Compila y sirve el frontend.
4. Entra a `http://localhost:5500` con `admin@empresa.com / admin123`.

## Verificación rápida

```powershell
# Health de la API
Invoke-RestMethod http://localhost:8080/api/v1/health

# Login
$login = Invoke-RestMethod `
  -Uri http://localhost:8080/api/v1/auth/login `
  -Method Post `
  -ContentType 'application/json' `
  -Body '{"email":"admin@empresa.com","password":"admin123"}'

$login.user.rol
```

Si el login responde y el rol es `admin`, el frontend podrá autenticarse.

## Módulos principales del frontend

- Dashboard por rol (`admin`, `comercial`, `operaciones`, `contabilidad`).
- Clientes, presupuestos y operaciones (con vínculo opcional a un presupuesto aprobado).
- Facturación, pagos, notas de crédito, cartera, cobranza y conciliación.
- Usuarios (solo admin) y recálculo administrativo de estados de factura.
- Cambio de contraseña desde el menú lateral de cualquier página privada.

## Documentación

- API: [`api/README.md`](api/README.md)
- Frontend: [`ERP-DEMO/README.md`](ERP-DEMO/README.md)
- Guía manual de pruebas: [`api/docs/guia de pruebas.md`](api/docs/guia%20de%20pruebas.md)
- Documentación técnica: [`api/docs/documentacion-tecnica.docx`](api/docs/documentacion-tecnica.docx)

## Problemas frecuentes

| Síntoma | Causa probable | Solución |
| --- | --- | --- |
| La API no arranca y pide credenciales `ERP_*` | Perfil `prod` activo | Usa el perfil `dev` para la demo o define las variables exigidas |
| `503` o `phase: down` | PhaseDB no está disponible | Inicia PhaseDB en `127.0.0.1:8484` y reintenta |
| El frontend muestra `Failed to fetch` | API detenida, URL incorrecta o CORS | Inicia la API y revisa `erp.apiBase` y `ERP_CORS_ORIGINS` |
| Pantalla en blanco | `dist/main.js` no existe | Ejecuta `npm run build` y recarga con `Ctrl+F5` |
| El login redirige al inicio | Token vencido o almacenamiento inconsistente | Borra `erp.token` y `erp.user` en `localStorage` y vuelve a entrar |
| `401` después de cambiar la contraseña | Sesión antigua en otra pestaña | Vuelve a iniciar sesión con la contraseña nueva |

La guía de pruebas incluye los pasos manuales de API y frontend, incluidos
presupuestos, cambio de contraseña y recálculo administrativo.
