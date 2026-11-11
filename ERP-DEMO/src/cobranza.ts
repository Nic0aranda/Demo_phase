import { api } from './api.js';
import { hasAnyRole, WRITE_ROLES } from './permissions.js';
import { getUser, setCurrency } from './storage.js';
import {
  $,
  emptyRow,
  errorMessage,
  escapeHtml,
  loadingRow,
  money,
  number,
  promptModal,
  setBusy,
  statusBadge,
  toast,
  daysFromNow
} from './ui.js';
import type {
  CuentaPorCobrar,
  PromesaPago,
  Recordatorio,
  Usuario
} from './types.js';

let cuentas: CuentaPorCobrar[] = [];
let recordatorios: Recordatorio[] = [];
let promesas: PromesaPago[] = [];
let filtroFactura = '';

function canManage(user: Usuario | null): boolean {
  return hasAnyRole(WRITE_ROLES.finanzas, user);
}

function renderKpis(): void {
  const container = $('#cobranzaKpis');
  if (!container) return;
  const pendiente = cuentas.reduce((sum, cuenta) => sum + Number(cuenta.montoPendiente || 0), 0);
  const vencido = cuentas
    .filter((cuenta) => ['ATRASADO', 'CRITICO', 'VENCIDA'].includes((cuenta.estado || '').toUpperCase()))
    .reduce((sum, cuenta) => sum + Number(cuenta.montoPendiente || 0), 0);
  const activas = promesas.filter((promesa) => (promesa.estado || '').toUpperCase() === 'PENDIENTE');
  container.innerHTML = `
    <div class="card kpi-card">
      <h3>Cartera pendiente</h3>
      <div class="kpi-value">${money(pendiente)}</div>
      <p class="trend neutral">${number(cuentas.length)} facturas en gestión</p>
    </div>
    <div class="card kpi-card">
      <h3>Monto vencido</h3>
      <div class="kpi-value" style="color:#dc2626;">${money(vencido)}</div>
      <p class="trend error">Requiere contacto inmediato</p>
    </div>
    <div class="card kpi-card">
      <h3>Recordatorios enviados</h3>
      <div class="kpi-value">${number(recordatorios.length)}</div>
      <p class="trend neutral">Historial de comunicaciones</p>
    </div>
    <div class="card kpi-card">
      <h3>Promesas activas</h3>
      <div class="kpi-value">${number(activas.length)}</div>
      <p class="trend positive">Compromisos de pago vigentes</p>
    </div>
  `;
}

function generarRecordatorio(facturaId: string): void {
  const cuenta = cuentas.find((item) => item.facturaId === facturaId);
  if (!cuenta) return;

  const confirmar = window.confirm(
    `¿Enviar un recordatorio por correo al cliente ${cuenta.cliente} por la factura ${facturaId}?`
  );
  if (!confirmar) return;

  const button = document.querySelector<HTMLButtonElement>(
    `[data-recordar="${CSS.escape(facturaId)}"]`
  );
  setBusy(button, true, 'Enviando…');
  api
    .enviarRecordatorio({ facturaId, canal: 'EMAIL' })
    .then(async () => {
      toast('Recordatorio registrado correctamente.');
      recordatorios = await api.recordatorios();
      renderKpis();
      renderRecordatorios();
    })
    .catch((error: unknown) => toast(errorMessage(error), 'error'))
    .finally(() => setBusy(button, false));
}

async function registrarPromesa(facturaId: string): Promise<void> {
  const cuenta = cuentas.find((item) => item.facturaId === facturaId);
  if (!cuenta) return;

  const fecha = await promptModal(
    'Registrar promesa de pago',
    'Fecha prometida (AAAA-MM-DD)',
    daysFromNow(15),
    'Ej. 2026-11-30'
  );
  if (!fecha) return;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fecha)) {
    toast('La fecha debe tener el formato AAAA-MM-DD.', 'error');
    return;
  }

  const montoTexto = await promptModal(
    'Monto prometido',
    `Monto prometido (saldo: ${money(cuenta.montoPendiente)})`,
    String(Number(cuenta.montoPendiente || 0).toFixed(2)),
    '0.00'
  );
  if (!montoTexto) return;
  const monto = Number(montoTexto.replace(',', '.'));
  if (!Number.isFinite(monto) || monto <= 0) {
    toast('El monto de la promesa debe ser mayor a cero.', 'error');
    return;
  }

  try {
    await api.registrarPromesa({ facturaId, fechaPromesa: fecha, monto, nota: 'Registrada desde gestión de cobranza' });
    toast('Promesa de pago registrada correctamente.');
    promesas = await api.promesas();
    renderKpis();
    renderPromesas();
  } catch (error) {
    toast(errorMessage(error), 'error');
  }
}

function renderCartera(): void {
  const tbody = $('#cobranzaTableBody');
  if (!tbody) return;
  const rows = cuentas.filter(
    (cuenta) =>
      Number(cuenta.montoPendiente || 0) > 0 &&
      (!filtroFactura || cuenta.facturaId === filtroFactura)
  );

  if (rows.length === 0) {
    tbody.innerHTML = emptyRow(6, 'No hay facturas pendientes de cobro.');
    return;
  }

  const manage = canManage(getUser());
  tbody.innerHTML = rows
    .map(
      (cuenta) => `
        <tr>
          <td>
            <strong>${escapeHtml(cuenta.cliente || 'Cliente')}</strong>
            <span class="sub-text">${escapeHtml(cuenta.rutCliente)}</span>
          </td>
          <td>${escapeHtml(cuenta.facturaId)}</td>
          <td>${escapeHtml(cuenta.fechaVencimiento || '—')}</td>
          <td><strong>${money(cuenta.montoPendiente)}</strong></td>
          <td>${statusBadge(cuenta.estado)}</td>
          <td class="table-actions">
            ${
              manage
                ? `<button type="button" class="btn-action" data-recordar="${escapeHtml(cuenta.facturaId)}">Recordatorio</button>
                   <button type="button" class="btn-action btn-action-ok" data-promesa="${escapeHtml(cuenta.facturaId)}">Promesa</button>`
                : '<span class="sub-text">—</span>'
            }
          </td>
        </tr>
      `
    )
    .join('');
}

function renderRecordatorios(): void {
  const tbody = $('#recordatoriosTableBody');
  if (!tbody) return;
  if (recordatorios.length === 0) {
    tbody.innerHTML = emptyRow(5, 'Aún no se han enviado recordatorios.');
    return;
  }
  tbody.innerHTML = recordatorios
    .slice()
    .reverse()
    .map(
      (recordatorio) => `
        <tr>
          <td>${escapeHtml(recordatorio.fechaEnvio || '—')}</td>
          <td>${escapeHtml(recordatorio.facturaId)}</td>
          <td>${escapeHtml(recordatorio.cliente || '—')}</td>
          <td>${escapeHtml(recordatorio.canal || '—')}</td>
          <td>${statusBadge(recordatorio.estado)}</td>
        </tr>
      `
    )
    .join('');
}

function renderPromesas(): void {
  const tbody = $('#promesasTableBody');
  if (!tbody) return;
  if (promesas.length === 0) {
    tbody.innerHTML = emptyRow(6, 'Aún no hay promesas de pago registradas.');
    return;
  }
  const manage = canManage(getUser());
  tbody.innerHTML = promesas
    .slice()
    .reverse()
    .map(
      (promesa) => `
        <tr>
          <td>${escapeHtml(promesa.facturaId)}</td>
          <td>${escapeHtml(promesa.cliente || '—')}</td>
          <td>${escapeHtml(promesa.fechaPromesa || '—')}</td>
          <td>${money(promesa.monto)}</td>
          <td>${statusBadge(promesa.estado)}</td>
          <td class="table-actions">
            ${
              manage && (promesa.estado || '').toUpperCase() === 'PENDIENTE'
                ? `<button type="button" class="btn-action btn-action-ok" data-promesa-estado="${escapeHtml(promesa.id)}" data-estado="CUMPLIDA">Cumplida</button>
                   <button type="button" class="btn-action btn-action-danger" data-promesa-estado="${escapeHtml(promesa.id)}" data-estado="INCUMPLIDA">Incumplida</button>`
                : '<span class="sub-text">—</span>'
            }
          </td>
        </tr>
      `
    )
    .join('');
}

export async function initCobranzaPage(): Promise<void> {
  const tbody = $('#cobranzaTableBody');
  if (!tbody) return;
  tbody.innerHTML = loadingRow(6);

  filtroFactura = new URLSearchParams(location.search).get('factura') || '';

  try {
    const [carteraResponse, recordatoriosResponse, promesasResponse] = await Promise.all([
      api.cartera(),
      api.recordatorios(),
      api.promesas()
    ]);
    cuentas = carteraResponse;
    recordatorios = recordatoriosResponse;
    promesas = promesasResponse;
    const dashboard = await api.dashboard();
    setCurrency(dashboard.moneda);
    renderKpis();
    renderCartera();
    renderRecordatorios();
    renderPromesas();
  } catch (error) {
    tbody.innerHTML = emptyRow(6, errorMessage(error, 'No se pudo cargar la gestión de cobranza.'));
    toast(errorMessage(error), 'error');
  }

  tbody.addEventListener('click', (event) => {
    const target = event.target as HTMLElement;
    const recordar = target.closest<HTMLButtonElement>('[data-recordar]');
    if (recordar) {
      generarRecordatorio(recordar.dataset.recordar || '');
      return;
    }
    const promesa = target.closest<HTMLButtonElement>('[data-promesa]');
    if (promesa) {
      void registrarPromesa(promesa.dataset.promesa || '');
    }
  });

  $('#promesasTableBody')?.addEventListener('click', async (event) => {
    const button = (event.target as HTMLElement).closest<HTMLButtonElement>('[data-promesa-estado]');
    if (!button) return;
    const id = button.dataset.promesaEstado || '';
    const estado = button.dataset.estado || '';
    setBusy(button, true, 'Guardando…');
    try {
      await api.actualizarEstadoPromesa(id, estado);
      promesas = await api.promesas();
      renderPromesas();
      renderKpis();
      toast(`Promesa marcada como ${estado.toLowerCase()}.`);
    } catch (error) {
      toast(errorMessage(error), 'error');
      setBusy(button, false);
    }
  });

  if (filtroFactura) {
    const clear = $('#clearCobranzaFilter');
    if (clear) {
      clear.hidden = false;
      clear.addEventListener('click', () => {
        filtroFactura = '';
        clear.hidden = true;
        renderCartera();
      });
    }
  }
}
