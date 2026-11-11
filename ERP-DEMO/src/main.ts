import { initLogin } from './auth.js';
import { initLayout } from './layout.js';
import { initDashboard } from './dashboard.js';
import { initClientesPage, initClienteForm } from './clientes.js';
import { initPresupuestosPage, initPresupuestoForm } from './presupuestos.js';
import { initOperacionesPage, initOperacionForm } from './operaciones.js';
import { initFacturacionPage, initCrearFacturaForm } from './facturacion.js';
import { initCarteraPage } from './cartera.js';
import { initCobranzaPage } from './cobranza.js';
import { initNotasPage, initNotaForm } from './notas.js';
import { initConciliacionPage } from './conciliacion.js';
import { initUsuariosPage, initUsuarioForm } from './usuarios.js';
import { errorMessage, toast } from './ui.js';

const page = document.body.dataset.page || '';

async function boot(): Promise<void> {
  if (page === 'login') {
    await initLogin();
    return;
  }

  // Todas las páginas privadas comparten guardia de sesión, menú y cierre de sesión.
  try {
    await initLayout();
  } catch {
    return;
  }

  switch (page) {
    case 'dashboard':
      await initDashboard();
      break;
    case 'clientes':
      await initClientesPage();
      break;
    case 'crear-cliente':
      await initClienteForm();
      break;
    case 'presupuestos':
      await initPresupuestosPage();
      break;
    case 'crear-presupuesto':
      await initPresupuestoForm();
      break;
    case 'operaciones':
      await initOperacionesPage();
      break;
    case 'crear-operacion':
      await initOperacionForm();
      break;
    case 'facturacion':
      await initFacturacionPage();
      break;
    case 'crear-factura':
      await initCrearFacturaForm();
      break;
    case 'cartera':
      await initCarteraPage();
      break;
    case 'cobranza':
      await initCobranzaPage();
      break;
    case 'notas':
      await initNotasPage();
      break;
    case 'crear-nota':
      await initNotaForm();
      break;
    case 'conciliacion':
      await initConciliacionPage();
      break;
    case 'usuarios':
      await initUsuariosPage();
      break;
    case 'crear-usuario':
      await initUsuarioForm();
      break;
    default:
      break;
  }
}

void boot().catch((error: unknown) => {
  toast(errorMessage(error), 'error');
});
