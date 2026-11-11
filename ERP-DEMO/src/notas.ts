import { api, ApiError } from './api.js';
import { hasAnyRole, WRITE_ROLES } from './permissions.js';
import { getUser, setCurrency } from './storage.js';
import {
  $,
  confirmAction,
  debounce,
  emptyRow,
  errorMessage,
  escapeHtml,
  fieldValue,
  flash,
  initCharacterCounters,
  loadingRow,
  money,
  number,
  setBusy,
  showFormAlert,
  statusBadge,
  statusLabel,
  toast
} from './ui.js';
import { applyServerDetails, initValidation } from './validation.js';
import type { CuentaPorCobrar, Factura, NotaCredito, Usuario } from './types.js';

let notas: NotaCredito[] = [];

function canManageNotas(user: Usuario | null): boolean {
  return hasAnyRole(WRITE_ROLES.finanzas, user);
}

function filteredNotas(): NotaCredito[] {
  const query = ($<HTMLInputElement>('#notaSearch')?.value || '').trim().toLowerCase();
  const tipo = ($<HTMLSelectElement>('#notaTipoFilter')?.value || '').toUpperCase();
  return notas.filter((nota) => {
    const matchQuery =
      !query ||
      [nota.id, nota.facturaId, nota.cliente, nota.rutCliente, nota.motivo]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(query));
    const matchTipo = !tipo || (nota.tipo || '').toUpperCase() === tipo;
    return matchQuery && matchTipo;
  });
}

function renderKpis(): void {
  const container = $('#notasKpis');
  if (!container) return;
  const vigentes = notas.filter((nota) => (nota.estado || '').toUpperCase() === 'EMITIDA');
  const anuladas = notas.filter((nota) => (nota.estado || '').toUpperCase() === 'ANULADA');
  const monto = vigentes.reduce((sum, nota) => sum + Number(nota.montoTotal || 0), 0);
  container.innerHTML = `
    <div class="card kpi-card">
      <h3>Notas registradas</h3>
      <div class="kpi-value">${number(notas.length)}</div>
      <p class="trend neutral">Total histórico de notas</p>
    </div>
    <div class="card kpi-card">
      <h3>Notas vigentes</h3>
      <div class="kpi-value">${number(vigentes.length)}</div>
      <p class="trend positive">Aplicadas a facturas</p>
    </div>
    <div class="card kpi-card">
      <h3>Monto acreditado</h3>
      <div class="kpi-value">${money(monto)}</div>
      <p class="trend neutral">Notas vigentes del periodo</p>
    </div>
    <div class="card kpi-card">
      <h3>Notas anuladas</h3>
      <div class="kpi-value">${number(anuladas.length)}</div>
      <p class="trend error">Sin efecto contable</p>
    </div>
  `;
}

function renderTabla(): void {
  const tbody = $('#notasTableBody');
  if (!tbody) return;
  const rows = filteredNotas();
  renderKpis();

  if (rows.length === 0) {
    tbody.innerHTML = emptyRow(9, 'No se encontraron notas de crédito con ese criterio.');
    return;
  }

  const canManage = canManageNotas(getUser());
  tbody.innerHTML = rows
    .map((nota) => {
      const vigente = (nota.estado || '').toUpperCase() === 'EMITIDA';
      return `
        <tr>
          <td><strong>${escapeHtml(nota.id)}</strong></td>
          <td>${escapeHtml(nota.facturaId)}</td>
          <td>
            ${escapeHtml(nota.cliente || 'Cliente no encontrado')}
            <span class="sub-text">${escapeHtml(nota.rutCliente || '')}</span>
          </td>
          <td>${escapeHtml(statusLabel(nota.tipo))}</td>
          <td>${money(nota.montoNeto)}</td>
          <td>${money(nota.montoTotal)}</td>
          <td>${escapeHtml(nota.fechaEmision || '—')}</td>
          <td>${statusBadge(nota.estado)}</td>
          <td class="table-actions">
            ${
              vigente && canManage
                ? `<button type="button" class="btn-action btn-action-danger" data-anular-nota="${escapeHtml(nota.id)}">Anular</button>`
                : '<span class="sub-text">—</span>'
            }
          </td>
        </tr>
      `;
    })
    .join('');
}

async function reload(): Promise<void> {
  notas = await api.notas();
  renderTabla();
}

export async function initNotasPage(): Promise<void> {
  const tbody = $('#notasTableBody');
  if (!tbody) return;
  tbody.innerHTML = loadingRow(9);

  try {
    const [notasResponse, dashboard] = await Promise.all([api.notas(), api.dashboard()]);
    notas = notasResponse;
    setCurrency(dashboard.moneda);
    renderTabla();
  } catch (error) {
    tbody.innerHTML = emptyRow(9, errorMessage(error, 'No se pudieron cargar las notas de crédito.'));
    toast(errorMessage(error), 'error');
  }

  $<HTMLInputElement>('#notaSearch')?.addEventListener(
    'input',
    debounce(() => renderTabla(), 150)
  );
  $<HTMLSelectElement>('#notaTipoFilter')?.addEventListener('change', () => renderTabla());

  tbody.addEventListener('click', async (event) => {
    const button = (event.target as HTMLElement).closest<HTMLButtonElement>('[data-anular-nota]');
    if (!button) return;
    const id = button.dataset.anularNota || '';
    const nota = notas.find((item) => item.id === id);
    if (!nota) return;
    if (!confirmAction(`¿Anular la nota ${id} de la factura ${nota.facturaId}?`)) return;

    setBusy(button, true, 'Anulando…');
    try {
      await api.anularNota(id);
      toast('Nota de crédito anulada correctamente.');
      await reload();
    } catch (error) {
      toast(errorMessage(error), 'error');
      setBusy(button, false);
    }
  });
}

/* ------------------------------------------------------------------ */
/* Formulario de emisión                                               */
/* ------------------------------------------------------------------ */

function renderSaldo(facturaId: string): void {
  const hint = $('#notaSaldo');
  const input = $<HTMLInputElement>('#notaMonto');
  if (!hint || !input) return;
  const option = $<HTMLOptionElement>(`#notaFactura option[value="${CSS.escape(facturaId)}"]`);
  const saldo = option?.dataset.saldo || '';
  const full = option?.dataset.total || '';
  hint.textContent = saldo
    ? `Saldo pendiente de la factura: ${money(Number(saldo))}. Total facturado: ${money(Number(full))}.`
    : 'Selecciona una factura para ver su saldo disponible.';
  if (saldo) input.max = String(Number(saldo));
}

export async function initNotaForm(): Promise<void> {
  const form = $<HTMLFormElement>('#notaForm');
  if (!form) return;
  initCharacterCounters(form);
  const submitButton = $<HTMLButtonElement>('#notaSubmit');
  const select = $<HTMLSelectElement>('#notaFactura');

  try {
    const [facturas, cartera, dashboard] = await Promise.all([
      api.facturas(),
      api.cartera(),
      api.dashboard()
    ]);
    setCurrency(dashboard.moneda);
    const saldos = new Map<string, CuentaPorCobrar>(cartera.map((cuenta) => [cuenta.facturaId, cuenta]));
    if (select) {
      select.innerHTML = (facturas as Factura[])
        .filter((factura) => (factura.estado || '').toUpperCase() !== 'ANULADA')
        .map((factura) => {
          const cuenta = saldos.get(factura.id);
          return `<option
            value="${escapeHtml(factura.id)}"
            data-saldo="${Number(cuenta?.montoPendiente || 0)}"
            data-total="${Number(factura.total || 0)}"
          >${escapeHtml(factura.id)} · ${escapeHtml(cuenta?.cliente || factura.rutCliente)} · saldo ${escapeHtml(
            money(cuenta?.montoPendiente || 0, dashboard.moneda)
          )}</option>`;
        })
        .join('');
      select.addEventListener('change', () => renderSaldo(select.value));
      renderSaldo(select.value);
    }
  } catch (error) {
    showFormAlert(form, errorMessage(error, 'No se pudieron cargar las facturas disponibles.'));
  }

  const validation = initValidation(
    form,
    {
      notaFactura: { label: 'La factura de origen', required: true },
      notaTipo: { label: 'El tipo de nota', required: true },
      notaMonto: {
        label: 'El monto neto',
        required: true,
        number: { min: 0.01, message: 'El monto neto debe ser mayor a cero.' }
      },
      notaMotivo: {
        label: 'El motivo',
        required: true,
        minLength: 5,
        maxLength: 250
      }
    },
    {
      onValid: async () => {
        setBusy(submitButton, true, 'Emitiendo…');
        try {
          const nota = await api.crearNota({
            facturaId: fieldValue('notaFactura'),
            tipo: fieldValue('notaTipo').toUpperCase(),
            montoNeto: Number(fieldValue('notaMonto').replace(',', '.')),
            motivo: fieldValue('notaMotivo')
          });
          flash(`Nota de crédito ${nota.id} emitida correctamente.`, 'success');
          location.href = 'notas-credito.html';
        } catch (error) {
          if (error instanceof ApiError && error.details) {
            applyServerDetails(validation, error.details);
          }
          showFormAlert(form, errorMessage(error, 'No se pudo emitir la nota de crédito.'));
        } finally {
          setBusy(submitButton, false);
        }
      }
    }
  );
}
