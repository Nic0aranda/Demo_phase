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
  toast
} from './ui.js';
import type { MovimientoConciliacion, ResumenConciliacion, Usuario } from './types.js';

let resumen: ResumenConciliacion | null = null;

function canManage(user: Usuario | null): boolean {
  return hasAnyRole(WRITE_ROLES.finanzas, user);
}

function renderKpis(): void {
  const container = $('#conciliacionKpis');
  if (!container) return;
  const data = resumen;
  const currency = data?.moneda || 'USD';
  container.innerHTML = `
    <div class="card kpi-card">
      <h3>Saldo en banco</h3>
      <div class="kpi-value">${money(data?.saldoBanco || 0, currency)}</div>
      <p class="trend neutral">Saldo simulado del periodo</p>
    </div>
    <div class="card kpi-card">
      <h3>Ingresos</h3>
      <div class="kpi-value" style="color:#1e8e3e;">${money(data?.totalIngresos || 0, currency)}</div>
      <p class="trend positive">Movimientos positivos</p>
    </div>
    <div class="card kpi-card">
      <h3>Egresos</h3>
      <div class="kpi-value" style="color:#dc2626;">${money(data?.totalEgresos || 0, currency)}</div>
      <p class="trend error">Movimientos negativos</p>
    </div>
    <div class="card kpi-card">
      <h3>Pendiente de conciliar</h3>
      <div class="kpi-value">${money(data?.montoPendienteConciliar || 0, currency)}</div>
      <p class="trend neutral">
        ${number(data?.movimientosPendientes || 0)} pendientes · ${number(data?.movimientosConciliados || 0)} conciliados
      </p>
    </div>
  `;
}

function renderTabla(): void {
  const tbody = $('#conciliacionTableBody');
  if (!tbody || !resumen) return;
  const movimientos = resumen.movimientos || [];
  if (movimientos.length === 0) {
    tbody.innerHTML = emptyRow(7, 'No hay movimientos bancarios registrados. Usa «Simular» para generarlos.');
    return;
  }

  const manage = canManage(getUser());
  tbody.innerHTML = movimientos
    .map((movimiento) => {
      const conciliado = (movimiento.estado || '').toUpperCase() === 'CONCILIADO';
      const monto = Number(movimiento.monto || 0);
      return `
        <tr>
          <td>${escapeHtml(movimiento.fecha || '—')}</td>
          <td>${escapeHtml(movimiento.concepto || '—')}</td>
          <td class="${monto >= 0 ? 'monto-ingreso' : 'monto-egreso'}">${money(monto)}</td>
          <td>${escapeHtml(movimiento.documentoErp || '—')}</td>
          <td>${escapeHtml(movimiento.origen || '—')}</td>
          <td>${statusBadge(movimiento.estado)}</td>
          <td class="table-actions">
            ${
              !conciliado && manage
                ? `<button type="button" class="btn-action btn-action-ok" data-conciliar="${escapeHtml(movimiento.id)}">Conciliar</button>`
                : '<span class="sub-text">—</span>'
            }
          </td>
        </tr>
      `;
    })
    .join('');
}

async function reload(): Promise<void> {
  resumen = await api.conciliacion();
  setCurrency(resumen.moneda);
  renderKpis();
  renderTabla();
}

async function runAction(kind: 'simular' | 'ejecutar', button: HTMLButtonElement | null): Promise<void> {
  const labels = {
    simular: { busy: 'Simulando…', done: 'Simulación generada correctamente.' },
    ejecutar: { busy: 'Conciliando…', done: 'Conciliación ejecutada correctamente.' }
  };
  setBusy(button, true, labels[kind].busy);
  try {
    if (kind === 'simular') await api.simularConciliacion();
    else await api.ejecutarConciliacion();
    toast(labels[kind].done);
    await reload();
  } catch (error) {
    toast(errorMessage(error), 'error');
  } finally {
    setBusy(button, false);
  }
}

export async function initConciliacionPage(): Promise<void> {
  const tbody = $('#conciliacionTableBody');
  if (!tbody) return;
  tbody.innerHTML = loadingRow(7);

  try {
    await reload();
  } catch (error) {
    tbody.innerHTML = emptyRow(7, errorMessage(error, 'No se pudo cargar la conciliación bancaria.'));
    toast(errorMessage(error), 'error');
  }

  const manage = canManage(getUser());
  const simulate = $<HTMLButtonElement>('#simularConciliacion');
  const execute = $<HTMLButtonElement>('#ejecutarConciliacion');
  if (simulate) {
    simulate.hidden = !manage;
    simulate.addEventListener('click', () => runAction('simular', simulate));
  }
  if (execute) {
    execute.hidden = !manage;
    execute.addEventListener('click', () => runAction('ejecutar', execute));
  }

  tbody.addEventListener('click', async (event) => {
    const button = (event.target as HTMLElement).closest<HTMLButtonElement>('[data-conciliar]');
    if (!button) return;
    const id = button.dataset.conciliar || '';
    const movimiento = (resumen?.movimientos || []).find(
      (item: MovimientoConciliacion) => item.id === id
    );
    if (!movimiento) return;

    const documento = await promptModal(
      'Conciliar movimiento',
      'Documento ERP asociado (factura, pago o nota)',
      movimiento.documentoErp || '',
      'Ej. FAC-000123'
    );
    if (!documento) return;

    setBusy(button, true, 'Conciliando…');
    try {
      await api.conciliarMovimiento(id, documento);
      toast('Movimiento conciliado correctamente.');
      await reload();
    } catch (error) {
      toast(errorMessage(error), 'error');
      setBusy(button, false);
    }
  });
}
