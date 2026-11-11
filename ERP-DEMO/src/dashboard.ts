import { api, ApiError } from './api.js';
import { getCurrency, getUser, setCurrency } from './storage.js';
import {
  $,
  emptyRow,
  escapeHtml,
  money,
  number,
  roleLabel,
  statusBadge,
  toast
} from './ui.js';
import type { CuentaPorCobrar, Dashboard, Rol, Usuario } from './types.js';

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return 'Buenos días';
  if (hour < 19) return 'Buenas tardes';
  return 'Buenas noches';
}

function subtitleFor(rol: Rol): string {
  const subtitles: Record<Rol, string> = {
    admin: 'Visión global de la organización y sus operaciones.',
    comercial: 'Seguimiento comercial de clientes, presupuestos y oportunidades.',
    operaciones: 'Estado logístico de las operaciones y su documentación.',
    contabilidad: 'Facturación, cobranza y conciliación financiera.'
  };
  return subtitles[rol] || subtitles.admin;
}

function shared(file: string): string {
  return `../compartidas/${file}`;
}

function actionsFor(user: Usuario): string {
  const links: string[] = [];
  if (user.rol === 'admin' || user.rol === 'comercial') {
    links.push(`<a href="${shared('crear-cliente.html')}" class="btn-primary">+ Crear cliente</a>`);
  }
  if (user.rol === 'admin' || user.rol === 'operaciones') {
    links.push(`<a href="${shared('crear-operaciones.html')}" class="btn-secondary">+ Crear operación</a>`);
  }
  if (user.rol === 'admin' || user.rol === 'contabilidad') {
    links.push(`<a href="${shared('crear-factura.html')}" class="btn-secondary">+ Nueva factura</a>`);
  }
  if (user.rol === 'admin') {
    links.push('<a href="users.html" class="btn-secondary">Gestionar usuarios</a>');
  }
  return links.join('');
}

function kpiCards(data: Dashboard, user: Usuario): string {
  const currency = data.moneda || getCurrency();
  const common = [
    {
      title: 'Facturado este mes',
      value: money(data.facturadoMes, currency),
      detail: `${number(data.facturasPagadas)} facturas pagadas`,
      tone: 'positive'
    },
    {
      title: 'Pendiente de cobro',
      value: money(data.pendienteCobro, currency),
      detail: `${number(data.facturasPendientes)} facturas pendientes`,
      tone: 'neutral'
    },
    {
      title: 'Monto vencido',
      value: money(data.montoVencido, currency),
      detail: `${number(data.facturasVencidas)} facturas vencidas`,
      tone: data.montoVencido > 0 ? 'error' : 'positive'
    },
    {
      title: 'Pagos del mes',
      value: money(data.pagosMes, currency),
      detail: 'Cobros registrados en el periodo',
      tone: 'positive'
    }
  ];

  const roleCards =
    user.rol === 'comercial'
      ? [
          {
            title: 'Clientes activos',
            value: number(data.clientesActivos),
            detail: 'Cartera comercial vigente',
            tone: 'positive'
          },
          {
            title: 'Operaciones activas',
            value: number(data.operacionesActivas),
            detail: 'En planificación o ejecución',
            tone: 'neutral'
          },
          ...common.slice(0, 2)
        ]
      : user.rol === 'operaciones'
        ? [
            {
              title: 'Operaciones activas',
              value: number(data.operacionesActivas),
              detail: 'En planificación o ejecución',
              tone: 'positive'
            },
            {
              title: 'Clientes activos',
              value: number(data.clientesActivos),
              detail: 'Cuentas habilitadas',
              tone: 'neutral'
            },
            ...common.slice(1, 3)
          ]
        : common;

  return roleCards
    .map(
      (card) => `
        <div class="card kpi-card">
          <h3>${escapeHtml(card.title)}</h3>
          <div class="kpi-value">${escapeHtml(card.value)}</div>
          <p class="trend ${escapeHtml(card.tone)}">${escapeHtml(card.detail)}</p>
        </div>
      `
    )
    .join('');
}

function alertCards(data: Dashboard): string {
  return `
    <div class="card alert-card critical">
      <div class="alert-icon">${number(data.facturasVencidas)}</div>
      <div class="alert-text">
        <h4>Facturas vencidas</h4>
        <p>${money(data.montoVencido)} por gestionar</p>
      </div>
      <a href="${shared('cuentas-por-cobrar.html')}">Revisar &gt;</a>
    </div>
    <div class="card alert-card warning">
      <div class="alert-icon">${number(data.facturasPendientes)}</div>
      <div class="alert-text">
        <h4>Facturas pendientes</h4>
        <p>${money(data.pendienteCobro)} en cartera</p>
      </div>
      <a href="${shared('cuentas-por-cobrar.html')}">Cobrar &gt;</a>
    </div>
    <div class="card alert-card info">
      <div class="alert-icon">${number(data.operacionesActivas)}</div>
      <div class="alert-text">
        <h4>Operaciones activas</h4>
        <p>Seguimiento logístico en curso</p>
      </div>
      <a href="${shared('operaciones.html')}">Ver &gt;</a>
    </div>
    <div class="card alert-card info">
      <div class="alert-icon">${number(data.clientesActivos)}</div>
      <div class="alert-text">
        <h4>Clientes activos</h4>
        <p>Cuentas habilitadas en el ERP</p>
      </div>
      <a href="${shared('clientes.html')}">Abrir &gt;</a>
    </div>
  `;
}

function chartWidget(data: Dashboard): string {
  const currency = data.moneda || getCurrency();
  const total = Math.max(data.facturadoMes, data.pagosMes, data.pendienteCobro, 1);
  const bars = [
    { label: 'Facturado', value: data.facturadoMes },
    { label: 'Cobrado', value: data.pagosMes },
    { label: 'Pendiente', value: data.pendienteCobro },
    { label: 'Vencido', value: data.montoVencido }
  ];

  return `
    <div class="card chart-widget">
      <div class="widget-header">
        <h3>Resumen financiero</h3>
        <p>Comparativo del periodo actual en ${escapeHtml(currency)}.</p>
      </div>
      <div class="bar-chart">
        ${bars
          .map((bar) => {
            const width = Math.max(2, Math.round((bar.value / total) * 100));
            return `
              <div class="bar-row">
                <span class="bar-label">${escapeHtml(bar.label)}</span>
                <div class="bar-track"><div class="bar-fill" style="width:${width}%"></div></div>
                <span class="bar-value">${escapeHtml(money(bar.value, currency))}</span>
              </div>
            `;
          })
          .join('')}
      </div>
    </div>
  `;
}

function systemWidget(data: Dashboard): string {
  return `
    <div class="card system-widget">
      <div class="widget-header">
        <h3>Estado del sistema</h3>
        <p>Indicadores clave y prioridades del día.</p>
      </div>
      <ul class="system-list">
        <li><span>Clientes activos</span><span class="status-ok">${number(data.clientesActivos)}</span></li>
        <li><span>Operaciones activas</span><span class="status-warn">${number(data.operacionesActivas)}</span></li>
        <li><span>Facturas pagadas</span><span class="status-ok">${number(data.facturasPagadas)}</span></li>
        <li><span>Presupuestos pendientes</span><span>${number(data.presupuestosPendientes)}</span></li>
      </ul>
    </div>
  `;
}

function recentCartera(cartera: CuentaPorCobrar[], currency: string): string {
  const rows = cartera.slice(0, 5);
  return `
    <div class="card table-card dashboard-table">
      <div class="table-card-header">
        <div>
          <h3>Cuentas por cobrar prioritarias</h3>
          <p>Los saldos con mayor antigüedad o urgencia.</p>
        </div>
        <a href="${shared('cuentas-por-cobrar.html')}" class="btn-secondary">Ver toda la cartera</a>
      </div>
      <table class="data-table">
        <thead>
          <tr>
            <th>CLIENTE</th>
            <th>FACTURA</th>
            <th>VENCIMIENTO</th>
            <th>PENDIENTE</th>
            <th>ESTADO</th>
          </tr>
        </thead>
        <tbody>
          ${
            rows.length === 0
              ? emptyRow(5, 'No hay cuentas por cobrar registradas.')
              : rows
                  .map(
                    (cuenta) => `
                      <tr>
                        <td><strong>${escapeHtml(cuenta.cliente)}</strong><span class="sub-text">${escapeHtml(cuenta.rutCliente)}</span></td>
                        <td>${escapeHtml(cuenta.facturaId)}</td>
                        <td>${escapeHtml(cuenta.fechaVencimiento || '—')}</td>
                        <td><strong>${escapeHtml(money(cuenta.montoPendiente, currency))}</strong></td>
                        <td>${statusBadge(cuenta.estado)}</td>
                      </tr>
                    `
                  )
                  .join('')
          }
        </tbody>
      </table>
    </div>
  `;
}

export async function initDashboard(): Promise<void> {
  const user = getUser();
  const root = $<HTMLElement>('#dashboard-root');
  if (!user || !root) return;

  const firstNames = user.nombres || user.email;
  $('#dashboard-greeting')!.textContent = `${greeting()}, ${firstNames}`;
  $('#dashboard-subtitle')!.textContent = subtitleFor(user.rol);
  $('#dashboard-badge')!.textContent = `TIPO DE CUENTA: ${roleLabel(user.rol)}`;
  $('#dashboard-actions')!.innerHTML = actionsFor(user);

  root.innerHTML = `
    <div class="card empty-state"><p>Cargando indicadores…</p></div>
  `;

  try {
    const [data, cartera] = await Promise.all([api.dashboard(), api.cartera()]);
    setCurrency(data.moneda);
    const currency = data.moneda || getCurrency();

    root.innerHTML = `
      <div class="kpi-cards">${kpiCards(data, user)}</div>
      <div class="alerts-section">${alertCards(data)}</div>
      <div class="main-widgets">
        ${chartWidget(data)}
        ${systemWidget(data)}
      </div>
      ${recentCartera(cartera, currency)}
    `;
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : 'No se pudieron cargar los indicadores del panel.';
    root.innerHTML = `
      <div class="card empty-state">
        <h3>No se pudo cargar el panel</h3>
        <p>${escapeHtml(message)}</p>
        <button type="button" class="btn-primary" onclick="location.reload()">Reintentar</button>
      </div>
    `;
    toast(message, 'error');
  }
}
