import { api, ApiError } from './api.js';
import { hasAnyRole, WRITE_ROLES } from './permissions.js';
import { setCurrency } from './storage.js';
import {
  $,
  $$,
  confirmAction,
  debounce,
  emptyRow,
  errorMessage,
  escapeHtml,
  fieldValue,
  flash,
  initModalDismiss,
  loadingRow,
  money,
  openModal,
  parseClienteValue,
  setBusy,
  showFormAlert,
  statusBadge,
  statusLabel,
  toast,
  today
} from './ui.js';
import { applyServerDetails, initValidation } from './validation.js';
import type { Cliente, Dashboard, Presupuesto, PresupuestoPayload } from './types.js';

let presupuestos: Presupuesto[] = [];
let clientesPorRut = new Map<string, Cliente>();
let dashboardData: Dashboard | null = null;

function canManagePresupuestos(): boolean {
  return hasAnyRole(WRITE_ROLES.presupuestos);
}

function clienteNombre(rut: string): string {
  return clientesPorRut.get(rut)?.nombre || 'Cliente no encontrado';
}

function filteredPresupuestos(): Presupuesto[] {
  const query = ($<HTMLInputElement>('#presupuestoSearch')?.value || '').trim().toLowerCase();
  const estado = ($<HTMLSelectElement>('#presupuestoEstado')?.value || '').toUpperCase();
  return presupuestos.filter((presupuesto) => {
    const matchQuery =
      !query ||
      [presupuesto.id, presupuesto.rutCliente, clienteNombre(presupuesto.rutCliente), presupuesto.estado]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(query));
    const matchEstado = !estado || (presupuesto.estado || '').toUpperCase() === estado;
    return matchQuery && matchEstado;
  });
}

function renderKpis(rows: Presupuesto[]): void {
  const container = $('#presupuestosKpis');
  if (!container) return;
  const pendientes = rows.filter((item) =>
    ['BORRADOR', 'ENVIADO'].includes((item.estado || '').toUpperCase())
  ).length;
  const aprobados = rows.filter((item) => (item.estado || '').toUpperCase() === 'APROBADO').length;
  const monto = rows.reduce((sum, item) => sum + Number(item.total || 0), 0);
  const currency = dashboardData?.moneda || 'USD';
  container.innerHTML = `
    <div class="card kpi-card">
      <h3>Presupuestos</h3>
      <div class="kpi-value">${rows.length}</div>
      <p class="trend neutral">Resultado del filtro actual</p>
    </div>
    <div class="card kpi-card">
      <h3>Pendientes</h3>
      <div class="kpi-value" style="color:#d97706;">${pendientes}</div>
      <p class="trend neutral">En borrador o enviados</p>
    </div>
    <div class="card kpi-card">
      <h3>Aprobados</h3>
      <div class="kpi-value" style="color:#16a34a;">${aprobados}</div>
      <p class="trend positive">Listos para ejecutar</p>
    </div>
    <div class="card kpi-card">
      <h3>Monto total</h3>
      <div class="kpi-value">${money(monto, currency)}</div>
      <p class="trend neutral">Suma de los presupuestos listados</p>
    </div>
  `;
}

function acciones(presupuesto: Presupuesto): string {
  const id = escapeHtml(presupuesto.id);
  const estado = (presupuesto.estado || '').toUpperCase();
  const parts = [
    `<button type="button" class="btn-action" data-view-presupuesto="${id}">Ver</button>`
  ];
  if (!canManagePresupuestos()) return parts.join(' ');

  if (['BORRADOR', 'ENVIADO', 'RECHAZADO'].includes(estado)) {
    parts.push(
      `<a class="btn-action" href="crear-presupuesto.html?id=${encodeURIComponent(presupuesto.id)}">Editar</a>`
    );
  }
  if (estado === 'BORRADOR') {
    parts.push(
      `<button type="button" class="btn-action btn-action-ok" data-estado-presupuesto="${id}" data-next="ENVIADO">Enviar</button>`
    );
  }
  if (estado === 'ENVIADO') {
    parts.push(
      `<button type="button" class="btn-action btn-action-ok" data-estado-presupuesto="${id}" data-next="APROBADO">Aprobar</button>`,
      `<button type="button" class="btn-action btn-action-danger" data-estado-presupuesto="${id}" data-next="RECHAZADO">Rechazar</button>`
    );
  }
  if (estado === 'RECHAZADO') {
    parts.push(
      `<button type="button" class="btn-action" data-estado-presupuesto="${id}" data-next="BORRADOR">Reabrir</button>`
    );
  }
  return parts.join(' ');
}

function renderTabla(): void {
  const tbody = $('#presupuestosTableBody');
  if (!tbody) return;
  const rows = filteredPresupuestos();
  renderKpis(rows);

  if (rows.length === 0) {
    tbody.innerHTML = emptyRow(6, 'No se encontraron presupuestos con ese criterio.');
    return;
  }

  tbody.innerHTML = rows
    .map(
      (presupuesto) => `
        <tr>
          <td><strong>${escapeHtml(presupuesto.id)}</strong></td>
          <td>
            ${escapeHtml(clienteNombre(presupuesto.rutCliente))}
            <span class="sub-text">${escapeHtml(presupuesto.rutCliente)}</span>
          </td>
          <td>${escapeHtml(presupuesto.fechaEmision || '—')}</td>
          <td><strong>${money(presupuesto.total)}</strong></td>
          <td>${statusBadge(presupuesto.estado)}</td>
          <td class="table-actions">${acciones(presupuesto)}</td>
        </tr>
      `
    )
    .join('');
}

function renderModal(presupuesto: Presupuesto): void {
  const title = $('#presupuestoModalTitle');
  const body = $('#presupuestoModalBody');
  if (!title || !body) return;
  title.textContent = `Detalle del presupuesto: ${presupuesto.id}`;

  body.innerHTML = `
    <div class="invoice-meta">
      <div><span>Cliente</span><span>${escapeHtml(clienteNombre(presupuesto.rutCliente))}</span></div>
      <div><span>RUT</span><span>${escapeHtml(presupuesto.rutCliente)}</span></div>
      <div><span>Emisión</span><span>${escapeHtml(presupuesto.fechaEmision || '—')}</span></div>
      <div><span>Estado</span><span>${statusBadge(presupuesto.estado)}</span></div>
    </div>

    <table class="invoice-items-table">
      <thead>
        <tr>
          <th>Descripción</th>
          <th style="text-align:center;">Cant.</th>
          <th class="money">Precio unit.</th>
          <th class="money">Total</th>
        </tr>
      </thead>
      <tbody>
        ${(presupuesto.items || [])
          .map(
            (item) => `
              <tr>
                <td>${escapeHtml(item.descripcion)}</td>
                <td style="text-align:center;">${item.cantidad}</td>
                <td class="money">${money(item.precioUnitario)}</td>
                <td class="money">${money(item.totalLinea)}</td>
              </tr>
            `
          )
          .join('')}
      </tbody>
    </table>

    <div class="invoice-summary">
      <div class="summary-row"><span>Subtotal</span><span>${money(presupuesto.subtotal)}</span></div>
      <div class="summary-row"><span>Impuestos</span><span>${money(presupuesto.impuestos)}</span></div>
      <div class="summary-row total"><span>Total</span><span>${money(presupuesto.total)}</span></div>
    </div>
  `;
}

async function refresh(): Promise<void> {
  presupuestos = await api.presupuestos();
  renderTabla();
}

export async function initPresupuestosPage(): Promise<void> {
  const tbody = $('#presupuestosTableBody');
  if (!tbody) return;
  tbody.innerHTML = loadingRow(6);
  initModalDismiss('presupuestoModal');

  try {
    const [presupuestosResponse, clientesResponse, dashboard] = await Promise.all([
      api.presupuestos(),
      api.clientes('', 0, 100),
      api.dashboard()
    ]);
    presupuestos = presupuestosResponse;
    dashboardData = dashboard;
    setCurrency(dashboard.moneda);
    clientesPorRut = new Map(
      clientesResponse.content.flatMap((cliente) => [
        [cliente.numDoc, cliente] as [string, Cliente],
        [cliente.numDoc.replace(/\./g, ''), cliente] as [string, Cliente]
      ])
    );
    renderTabla();
  } catch (error) {
    tbody.innerHTML = emptyRow(6, errorMessage(error, 'No se pudieron cargar los presupuestos.'));
    toast(errorMessage(error), 'error');
  }

  $<HTMLInputElement>('#presupuestoSearch')?.addEventListener(
    'input',
    debounce(() => renderTabla(), 150)
  );
  $<HTMLSelectElement>('#presupuestoEstado')?.addEventListener('change', () => renderTabla());

  tbody.addEventListener('click', async (event) => {
    const target = event.target as HTMLElement;

    const viewButton = target.closest<HTMLButtonElement>('[data-view-presupuesto]');
    if (viewButton) {
      const presupuesto = presupuestos.find(
        (item) => item.id === viewButton.dataset.viewPresupuesto
      );
      if (!presupuesto) return;
      renderModal(presupuesto);
      openModal('presupuestoModal');
      return;
    }

    const stateButton = target.closest<HTMLButtonElement>('[data-estado-presupuesto]');
    if (!stateButton || !canManagePresupuestos()) return;
    const id = stateButton.dataset.estadoPresupuesto || '';
    const next = (stateButton.dataset.next || '').toUpperCase();
    if (!id || !next) return;
    const labels: Record<string, string> = {
      ENVIADO: 'enviar',
      APROBADO: 'aprobar',
      RECHAZADO: 'rechazar',
      BORRADOR: 'reabrir'
    };
    if (!confirmAction(`¿Confirmas ${labels[next] || 'cambiar'} el presupuesto ${id}?`)) return;

    setBusy(stateButton, true, 'Guardando…');
    try {
      await api.cambiarEstadoPresupuesto(id, next);
      toast(`Presupuesto ${id} actualizado a ${statusLabel(next)}.`);
      await refresh();
    } catch (error) {
      toast(errorMessage(error, 'No se pudo cambiar el estado del presupuesto.'), 'error');
      setBusy(stateButton, false);
    }
  });

}

/* ------------------------------------------------------------------ */
/* Formulario de creación / edición de presupuesto                     */
/* ------------------------------------------------------------------ */

interface ItemRow {
  descripcion: string;
  cantidad: number;
  precioUnitario: number;
  row: HTMLTableRowElement;
}

function itemRows(): ItemRow[] {
  return $$<HTMLTableRowElement>('#presupuestoItemsBody tr').map((row) => ({
    descripcion: ($<HTMLInputElement>('.presupuesto-descripcion', row)?.value || '').trim(),
    cantidad: Number(($<HTMLInputElement>('.presupuesto-cantidad', row)?.value || '0').replace(',', '.')),
    precioUnitario: Number(($<HTMLInputElement>('.presupuesto-precio', row)?.value || '0').replace(',', '.')),
    row
  }));
}

function addItemRow(descripcion = '', cantidad = 1, precio = 0): void {
  const tbody = $('#presupuestoItemsBody');
  if (!tbody) return;
  const row = document.createElement('tr');
  row.innerHTML = `
    <td><input type="text" class="presupuesto-descripcion" maxlength="120" placeholder="Ej. Servicio de consultoría" value="${escapeHtml(descripcion)}"></td>
    <td><input type="number" class="presupuesto-cantidad" min="1" step="1" value="${cantidad}"></td>
    <td><input type="number" class="presupuesto-precio" min="0" step="0.01" value="${precio}"></td>
    <td><input type="text" class="presupuesto-total" readonly tabindex="-1"></td>
    <td><button type="button" class="btn-action btn-action-danger presupuesto-remove" aria-label="Eliminar línea">×</button></td>
  `;
  tbody.appendChild(row);
  row.querySelectorAll<HTMLInputElement>('input').forEach((input) => {
    input.addEventListener('input', updateTotals);
  });
  row.querySelector<HTMLButtonElement>('.presupuesto-remove')?.addEventListener('click', () => {
    row.remove();
    updateTotals();
  });
  updateTotals();
}

function updateTotals(): void {
  const rows = itemRows();
  const subtotal = rows.reduce((sum, item) => {
    const total = Number.isFinite(item.cantidad * item.precioUnitario)
      ? item.cantidad * item.precioUnitario
      : 0;
    const output = $<HTMLInputElement>('.presupuesto-total', item.row);
    if (output) output.value = total.toFixed(2);
    return sum + total;
  }, 0);
  const ivaRate = dashboardData?.ivaRate || 0;
  const impuestos = subtotal * ivaRate;
  const total = subtotal + impuestos;
  const currency = dashboardData?.moneda || 'USD';
  const subtotalEl = $('#presupuestoSubtotal');
  const impuestosEl = $('#presupuestoImpuestos');
  const totalEl = $('#presupuestoTotal');
  if (subtotalEl) subtotalEl.textContent = money(subtotal, currency);
  if (impuestosEl) impuestosEl.textContent = money(impuestos, currency);
  if (totalEl) totalEl.textContent = money(total, currency);
}

function fillForm(presupuesto: Presupuesto): void {
  const cliente = clientesPorRut.get(presupuesto.rutCliente);
  const clienteInput = $<HTMLInputElement>('#presupuestoCliente');
  if (clienteInput) {
    clienteInput.value = `${cliente?.nombre || presupuesto.rutCliente} | ${presupuesto.rutCliente}`;
  }
  const fecha = $<HTMLInputElement>('#presupuestoFecha');
  if (fecha) fecha.value = presupuesto.fechaEmision || today();
  const estado = $<HTMLSelectElement>('#presupuestoEstado');
  if (estado) estado.value = (presupuesto.estado || 'BORRADOR').toUpperCase();
  for (const item of presupuesto.items || []) {
    addItemRow(item.descripcion, item.cantidad, item.precioUnitario);
  }
}

export async function initPresupuestoForm(): Promise<void> {
  const form = $<HTMLFormElement>('#presupuestoForm');
  if (!form) return;

  const params = new URLSearchParams(location.search);
  const editId = (params.get('id') || '').trim();
  const isEdit = Boolean(editId);
  const title = $('#presupuestoFormTitle');
  const subtitle = $('#presupuestoFormSubtitle');
  const submitButton = $<HTMLButtonElement>('#presupuestoSubmit');
  let current: Presupuesto | null = null;

  if (isEdit) {
    if (title) title.textContent = 'Editar presupuesto';
    if (subtitle) subtitle.textContent = 'Actualiza los conceptos y el estado del presupuesto.';
    if (submitButton) submitButton.textContent = '✓ Guardar cambios';
    const breadcrumb = $('#presupuestoFormBreadcrumb');
    if (breadcrumb) breadcrumb.textContent = `Editar ${editId}`;
  }

  try {
    const [clientesResponse, dashboard] = await Promise.all([
      api.clientes('', 0, 100),
      api.dashboard()
    ]);
    dashboardData = dashboard;
    setCurrency(dashboard.moneda);
    clientesPorRut = new Map(
      clientesResponse.content.flatMap((cliente) => [
        [cliente.numDoc, cliente] as [string, Cliente],
        [cliente.numDoc.replace(/\./g, ''), cliente] as [string, Cliente]
      ])
    );
    const datalist = $('#clientesList');
    if (datalist) {
      datalist.innerHTML = clientesResponse.content
        .map(
          (cliente) =>
            `<option value="${escapeHtml(cliente.nombre)} | ${escapeHtml(cliente.numDoc)}"></option>`
        )
        .join('');
    }

    if (isEdit) {
      current = await api.presupuesto(editId);
      fillForm(current);
    }
  } catch (error) {
    toast(errorMessage(error, 'No se pudieron cargar los datos del presupuesto.'), 'error');
    if (isEdit) location.replace('presupuestos.html');
    return;
  }

  const fecha = $<HTMLInputElement>('#presupuestoFecha');
  if (fecha && !fecha.value) fecha.value = today();
  if (itemRows().length === 0) addItemRow();
  updateTotals();
  $('#addPresupuestoItemBtn')?.addEventListener('click', () => addItemRow());

  const validation = initValidation(
    form,
    {
      presupuestoCliente: {
        label: 'El cliente',
        required: true,
        custom: (value) => {
          const parsed = parseClienteValue(value);
          if (!parsed.rut) return 'Selecciona un cliente de la lista o escribe su RUT.';
          return null;
        }
      },
      presupuestoFecha: { label: 'La fecha de emisión', required: true, date: true },
      presupuestoEstado: { label: 'El estado', required: true }
    },
    {
      onValid: async () => {
        const rows = itemRows();
        if (rows.length === 0) {
          showFormAlert(form, 'Agrega al menos un concepto al presupuesto.');
          return;
        }
        const invalidRow = rows.find(
          (item) =>
            !item.descripcion ||
            !Number.isInteger(item.cantidad) ||
            item.cantidad < 1 ||
            !Number.isFinite(item.precioUnitario) ||
            item.precioUnitario < 0
        );
        if (invalidRow) {
          showFormAlert(
            form,
            'Completa la descripción, una cantidad entera mayor a 0 y un precio válido en cada concepto.'
          );
          return;
        }

        setBusy(submitButton, true, isEdit ? 'Guardando…' : 'Creando…');
        try {
          const parsed = parseClienteValue(fieldValue('presupuestoCliente'));
          const payload: PresupuestoPayload = {
            rutCliente: parsed.rut,
            fechaEmision: fieldValue('presupuestoFecha') || today(),
            estado: fieldValue('presupuestoEstado').toUpperCase(),
            items: rows.map((item) => ({
              descripcion: item.descripcion,
              cantidad: item.cantidad,
              precioUnitario: item.precioUnitario
            }))
          };
          if (isEdit && current) {
            await api.actualizarPresupuesto(editId, payload);
            flash(`Presupuesto ${editId} actualizado correctamente.`, 'success');
          } else {
            const creado = await api.crearPresupuesto(payload);
            flash(`Presupuesto ${creado.id} creado correctamente.`, 'success');
          }
          location.href = 'presupuestos.html';
        } catch (error) {
          if (error instanceof ApiError && error.details) {
            applyServerDetails(validation, error.details);
          }
          showFormAlert(form, errorMessage(error, 'No se pudo guardar el presupuesto.'));
        } finally {
          setBusy(submitButton, false);
        }
      }
    }
  );
}
