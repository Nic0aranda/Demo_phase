import { api } from './api.js';
import { hasAnyRole, WRITE_ROLES } from './permissions.js';
import { setCurrency } from './storage.js';
import {
  $,
  debounce,
  emptyRow,
  errorMessage,
  escapeHtml,
  loadingRow,
  money,
  number,
  statusBadge,
  toast
} from './ui.js';
import { openPaymentModal } from './pagos.js';
import type { CuentaPorCobrar, ResumenCartera } from './types.js';

let cuentas: CuentaPorCobrar[] = [];
let resumen: ResumenCartera | null = null;

function currency(): string {
  return resumen?.moneda || 'USD';
}

function renderKpis(): void {
  const container = $('#carteraKpis');
  if (!container) return;
  const data = resumen;
  container.innerHTML = `
    <div class="card kpi-card">
      <h3>Total por cobrar</h3>
      <div class="kpi-value">${money(data?.totalPorCobrar || 0, currency())}</div>
      <p class="trend neutral">${number(data?.facturasPendientes || 0)} facturas pendientes</p>
    </div>
    <div class="card kpi-card">
      <h3>Monto vencido</h3>
      <div class="kpi-value" style="color:#dc2626;">${money(data?.totalVencido || 0, currency())}</div>
      <p class="trend error">${number(data?.facturasVencidas || 0)} facturas vencidas</p>
    </div>
    <div class="card kpi-card">
      <h3>Por vencer</h3>
      <div class="kpi-value" style="color:#d97706;">${money(data?.totalPorVencer || 0, currency())}</div>
      <p class="trend neutral">Saldos aún dentro de plazo</p>
    </div>
    <div class="card kpi-card">
      <h3>Facturas pagadas</h3>
      <div class="kpi-value">${number(data?.facturasPagadas || 0)}</div>
      <p class="trend positive">Cartera cerrada</p>
    </div>
  `;
}

function filteredCuentas(): CuentaPorCobrar[] {
  const query = ($<HTMLInputElement>('#carteraSearch')?.value || '').trim().toLowerCase();
  const estado = ($<HTMLSelectElement>('#carteraEstado')?.value || '').toUpperCase();
  return cuentas.filter((cuenta) => {
    const matchQuery =
      !query ||
      [cuenta.facturaId, cuenta.rutCliente, cuenta.cliente]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(query));
    const matchEstado = !estado || (cuenta.estado || '').toUpperCase() === estado;
    return matchQuery && matchEstado;
  });
}

function canRegisterPayments(): boolean {
  return hasAnyRole(WRITE_ROLES.finanzas);
}

function renderTabla(): void {
  const tbody = $('#carteraTableBody');
  if (!tbody) return;
  const rows = filteredCuentas();

  if (rows.length === 0) {
    tbody.innerHTML = emptyRow(9, 'No se encontraron cuentas por cobrar con ese criterio.');
    return;
  }

  const canPay = canRegisterPayments();
  tbody.innerHTML = rows
    .map((cuenta) => {
      const pendiente = Number(cuenta.montoPendiente || 0) > 0;
      return `
        <tr>
          <td>
            <strong>${escapeHtml(cuenta.cliente || 'Cliente no encontrado')}</strong>
            <span class="sub-text">${escapeHtml(cuenta.rutCliente)}</span>
          </td>
          <td>${escapeHtml(cuenta.facturaId)}</td>
          <td>${escapeHtml(cuenta.fechaEmision || '—')}</td>
          <td>${escapeHtml(cuenta.fechaVencimiento || '—')}</td>
          <td>${money(cuenta.total, currency())}</td>
          <td>${money(cuenta.pagado, currency())}</td>
          <td><strong>${money(cuenta.montoPendiente, currency())}</strong></td>
          <td>
            ${statusBadge(cuenta.estado)}
            ${
              Number(cuenta.diasAtraso || 0) > 0
                ? `<span class="sub-text">${number(cuenta.diasAtraso)} días de atraso</span>`
                : ''
            }
          </td>
          <td class="table-actions">
            ${
              pendiente && canPay
                ? `<button type="button" class="btn-action btn-action-ok" data-pay-cartera="${escapeHtml(cuenta.facturaId)}">Registrar pago</button>`
                : ''
            }
            <a class="btn-action" href="gestion-cobranza.html?factura=${encodeURIComponent(cuenta.facturaId)}">Cobranza</a>
          </td>
        </tr>
      `;
    })
    .join('');
}

async function reload(): Promise<void> {
  const [carteraResponse, dashboard] = await Promise.all([api.cartera(), api.dashboard()]);
  cuentas = carteraResponse;
  resumen = dashboard.cartera;
  setCurrency(dashboard.moneda);
  renderKpis();
  renderTabla();
}

export async function initCarteraPage(): Promise<void> {
  const tbody = $('#carteraTableBody');
  if (!tbody) return;
  tbody.innerHTML = loadingRow(9);

  try {
    await reload();
  } catch (error) {
    tbody.innerHTML = emptyRow(9, errorMessage(error, 'No se pudo cargar la cartera.'));
    toast(errorMessage(error), 'error');
  }

  $<HTMLInputElement>('#carteraSearch')?.addEventListener(
    'input',
    debounce(() => renderTabla(), 150)
  );
  $<HTMLSelectElement>('#carteraEstado')?.addEventListener('change', () => renderTabla());

  tbody.addEventListener('click', (event) => {
    const button = (event.target as HTMLElement).closest<HTMLButtonElement>('[data-pay-cartera]');
    if (!button) return;
    const facturaId = button.dataset.payCartera || '';
    const cuenta = cuentas.find((item) => item.facturaId === facturaId);
    if (!cuenta) return;

    openPaymentModal({
      facturaId,
      cliente: cuenta.cliente,
      montoSugerido: Number(cuenta.montoPendiente || 0),
      onSuccess: async () => {
        try {
          await reload();
          toast('Cartera actualizada.', 'success');
        } catch (error) {
          toast(errorMessage(error), 'error');
        }
      }
    });
  });
}
