La API escucha en `http://localhost:8080` y el frontend en
`http://localhost:5500`.

## Requisitos
| JDK | 17 o superior |
| Node.js | 18 o superior |
| Maven | No hace falta instalarlo: se usa `mvnw.cmd` |
| PhaseDB | Disponible en `http://127.0.0.1:8484` |
| Cuenta PhaseDB | `admin / admin123` (configurable) |

> PhaseDB debe estar iniciado **antes** que la API. La API usa la cuenta de
> servicio indicada para leer y escribir sus globales.

## Instalación y arranque de la API

Abre PowerShell en la raíz del repositorio:

cd api

.\mvnw.cmd spring-boot:run

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

## Instalación y arranque del frontend

En otra terminal, desde la raíz del repositorio:

cd ERP-DEMO
npm install
npm start
```

Abre **`http://localhost:5500`**. No abras los HTML con `file://`: el navegador
bloquearía las llamadas a la API por CORS.

## Orden de arranque recomendado

1. Inicia PhaseDB y verifica `http://127.0.0.1:8484`.
2. Inicia la API y comprueba `http://localhost:8080/api/v1/health` (`phase: up`).
3. Compila y sirve el frontend.
4. Entra a `http://localhost:5500` con `admin@empresa.com / admin123`.
