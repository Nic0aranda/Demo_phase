import { api, ApiError } from './api.js';
import { hasAnyRole, WRITE_ROLES } from './permissions.js';
import { getUser, setCurrency } from './storage.js';
import {
  $,
  $$,
  closeModal,
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
import { openPaymentModal } from './pagos.js';
import type {
  Cliente,
  CuentaPorCobrar,
  Dashboard,
  Factura,
  FacturaPayload,
  Operacion,
  Usuario
} from './types.js';

let facturas: Factura[] = [];
let clientesPorRut = new Map<string, Cliente>();
let carteraPorFactura = new Map<string, CuentaPorCobrar>();
let dashboardData: Dashboard | null = null;
let invoiceModalId: string | null = null;
let operacionesPorId = new Map<string, Operacion>();

function userCanWriteFinance(user: Usuario | null): boolean {
  return hasAnyRole(WRITE_ROLES.finanzas, user);
}

function renderKpis(): void {
  const container = $('#facturacionKpis');
  if (!container) return;
  const data = dashboardData;
  const currency = data?.moneda || 'USD';
  container.innerHTML = `
    <div class="card kpi-card">
      <h3>Facturado este mes</h3>
      <div class="kpi-value">${money(data?.facturadoMes || 0, currency)}</div>
      <p class="trend positive">${facturas.length} facturas registradas</p>
    </div>
    <div class="card kpi-card">
      <h3>Pendiente de cobro</h3>
      <div class="kpi-value" style="color:#d97706;">${money(data?.pendienteCobro || 0, currency)}</div>
      <p class="trend neutral">${data?.facturasPendientes || 0} facturas pendientes</p>
    </div>
    <div class="card kpi-card">
      <h3>Monto vencido</h3>
      <div class="kpi-value" style="color:#dc2626;">${money(data?.montoVencido || 0, currency)}</div>
      <p class="trend error">${data?.facturasVencidas || 0} facturas vencidas</p>
    </div>
    <div class="card kpi-card">
      <h3>Facturas pagadas</h3>
      <div class="kpi-value">${data?.facturasPagadas || 0}</div>
      <p class="trend positive">Cobros completados</p>
    </div>
  `;
}

function filteredFacturas(): Factura[] {
  const query = ($<HTMLInputElement>('#facturaSearch')?.value || '').trim().toLowerCase();
  const estado = ($<HTMLSelectElement>('#facturaEstado')?.value || '').toUpperCase();
  return facturas.filter((factura) => {
    const cliente = clientesPorRut.get(factura.rutCliente);
    const matchQuery =
      !query ||
      [factura.id, factura.rutCliente, cliente?.nombre, factura.estado]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(query));
    const matchEstado = !estado || (factura.estado || '').toUpperCase() === estado;
    return matchQuery && matchEstado;
  });
}

function renderTabla(): void {
  const tbody = $('#facturasTableBody');
  if (!tbody) return;
  const rows = filteredFacturas();
  if (rows.length === 0) {
    tbody.innerHTML = emptyRow(7, 'No se encontraron facturas con ese criterio.');
    return;
  }

  const canPay = userCanWriteFinance(getUser());
  tbody.innerHTML = rows
    .map((factura) => {
      const cliente = clientesPorRut.get(factura.rutCliente);
      const pendiente = (factura.estado || '').toUpperCase() !== 'PAGADA' &&
        (factura.estado || '').toUpperCase() !== 'ANULADA';
      return `
        <tr>
          <td><strong>${escapeHtml(factura.id)}</strong></td>
          <td>
            ${escapeHtml(cliente?.nombre || 'Cliente no encontrado')}
            <span class="sub-text">${escapeHtml(factura.rutCliente)}</span>
          </td>
          <td>${escapeHtml(factura.fechaEmision || '—')}</td>
          <td>${escapeHtml(factura.fechaVencimiento || '—')}</td>
          <td><strong>${money(factura.total)}</strong></td>
          <td>${statusBadge(factura.estado)}</td>
          <td class="table-actions">
            <button type="button" class="btn-action" data-invoice="${escapeHtml(factura.id)}">Ver detalle</button>
            <button type="button" class="btn-action" data-pdf="${escapeHtml(factura.id)}">PDF</button>
            ${pendiente && canPay ? `<button type="button" class="btn-action btn-action-ok" data-pay="${escapeHtml(factura.id)}">Registrar pago</button>` : ''}
          </td>
        </tr>
      `;
    })
    .join('');
}

function renderInvoiceModal(factura: Factura): void {
  const title = $('#invoiceModalTitle');
  const body = $('#invoiceModalBody');
  if (!title || !body) return;
  const cliente = clientesPorRut.get(factura.rutCliente);
  const canPay = userCanWriteFinance(getUser());
  const pendiente = (factura.estado || '').toUpperCase() !== 'PAGADA' &&
    (factura.estado || '').toUpperCase() !== 'ANULADA';
  invoiceModalId = factura.id;
  title.textContent = `Detalle de factura: ${factura.id}`;

  body.innerHTML = `
    <div class="invoice-meta">
      <div><span>Cliente</span><span>${escapeHtml(cliente?.nombre || 'Cliente no encontrado')}</span></div>
      <div><span>RUT</span><span>${escapeHtml(factura.rutCliente)}</span></div>
      <div><span>Emisión</span><span>${escapeHtml(factura.fechaEmision || '—')}</span></div>
      <div><span>Vencimiento</span><span>${escapeHtml(factura.fechaVencimiento || '—')}</span></div>
      <div><span>Condición de pago</span><span>${escapeHtml(statusLabel(factura.condicionPago))}</span></div>
      <div><span>Operación asociada</span><span>${escapeHtml(factura.idOperacionOrigen || 'Sin operación')}</span></div>
      <div><span>Estado</span><span>${statusBadge(factura.estado)}</span></div>
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
        ${(factura.items || [])
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
      <div class="summary-row"><span>Subtotal</span><span>${money(factura.subtotal)}</span></div>
      <div class="summary-row"><span>Impuestos</span><span>${money(factura.impuestos)}</span></div>
      <div class="summary-row total"><span>Total</span><span>${money(factura.total)}</span></div>
    </div>
  `;

  const payButton = $<HTMLButtonElement>('#invoicePayBtn');
  if (payButton) payButton.hidden = !(pendiente && canPay);
}

export async function initFacturacionPage(): Promise<void> {
  const tbody = $('#facturasTableBody');
  if (!tbody) return;
  tbody.innerHTML = loadingRow(7);
  initModalDismiss('invoiceModal');
  const emailButton = $<HTMLButtonElement>('#invoiceEmailBtn');
  if (emailButton) emailButton.hidden = !userCanWriteFinance(getUser());

  try {
    const [facturasResponse, clientesResponse, dashboard, cartera] = await Promise.all([
      api.facturas(),
      api.clientes('', 0, 100),
      api.dashboard(),
      api.cartera()
    ]);
    facturas = facturasResponse;
    dashboardData = dashboard;
    setCurrency(dashboard.moneda);
    clientesPorRut = new Map(
      clientesResponse.content.flatMap((cliente) => [
        [cliente.numDoc, cliente] as [string, Cliente],
        [cliente.numDoc.replace(/\./g, ''), cliente] as [string, Cliente]
      ])
    );
    carteraPorFactura = new Map(cartera.map((cuenta) => [cuenta.facturaId, cuenta]));
    renderKpis();
    renderTabla();
  } catch (error) {
    tbody.innerHTML = emptyRow(7, errorMessage(error, 'No se pudieron cargar las facturas.'));
    toast(errorMessage(error), 'error');
  }

  $<HTMLInputElement>('#facturaSearch')?.addEventListener(
    'input',
    debounce(() => renderTabla(), 150)
  );
  $<HTMLSelectElement>('#facturaEstado')?.addEventListener('change', () => renderTabla());

  tbody.addEventListener('click', async (event) => {
    const target = event.target as HTMLElement;

    const detailButton = target.closest<HTMLButtonElement>('[data-invoice]');
    if (detailButton) {
      const id = detailButton.dataset.invoice || '';
      const factura = facturas.find((item) => item.id === id);
      if (!factura) return;
      renderInvoiceModal(factura);
      openModal('invoiceModal');
      return;
    }

    const pdfButton = target.closest<HTMLButtonElement>('[data-pdf]');
    if (pdfButton) {
      const id = pdfButton.dataset.pdf || '';
      setBusy(pdfButton, true, 'Generando…');
      try {
        await api.descargarFacturaPdf(id);
      } catch (error) {
        toast(errorMessage(error, 'No se pudo descargar el PDF.'), 'error');
      } finally {
        setBusy(pdfButton, false);
      }
      return;
    }

    const payButton = target.closest<HTMLButtonElement>('[data-pay]');
    if (payButton) {
      const id = payButton.dataset.pay || '';
      const factura = facturas.find((item) => item.id === id);
      if (!factura) return;
      const cliente = clientesPorRut.get(factura.rutCliente);
      openPaymentModal({
        facturaId: id,
        cliente: cliente?.nombre,
        montoSugerido: carteraPorFactura.get(id)?.montoPendiente || factura.total,
        onSuccess: async () => {
          facturas = await api.facturas();
          const [dashboard, cartera] = await Promise.all([api.dashboard(), api.cartera()]);
          dashboardData = dashboard;
          carteraPorFactura = new Map(cartera.map((cuenta) => [cuenta.facturaId, cuenta]));
          renderKpis();
          renderTabla();
          closeModal('invoiceModal');
        }
      });
    }
  });

  $('#invoiceEmailBtn')?.addEventListener('click', async () => {
    if (!invoiceModalId) return;
    const button = $<HTMLButtonElement>('#invoiceEmailBtn');
    setBusy(button, true, 'Enviando…');
    try {
      await api.enviarFactura(invoiceModalId, {});
      toast('Envío simulado registrado correctamente.');
    } catch (error) {
      toast(errorMessage(error, 'No se pudo registrar el envío.'), 'error');
    } finally {
      setBusy(button, false);
    }
  });

  $('#invoicePdfBtn')?.addEventListener('click', async () => {
    if (!invoiceModalId) return;
    const button = $<HTMLButtonElement>('#invoicePdfBtn');
    setBusy(button, true, 'Generando…');
    try {
      await api.descargarFacturaPdf(invoiceModalId);
    } catch (error) {
      toast(errorMessage(error, 'No se pudo descargar el PDF.'), 'error');
    } finally {
      setBusy(button, false);
    }
  });

  $('#invoicePayBtn')?.addEventListener('click', () => {
    if (!invoiceModalId) return;
    const factura = facturas.find((item) => item.id === invoiceModalId);
    if (!factura) return;
    const cliente = clientesPorRut.get(factura.rutCliente);
    openPaymentModal({
      facturaId: factura.id,
      cliente: cliente?.nombre,
      montoSugerido: carteraPorFactura.get(factura.id)?.montoPendiente || factura.total,
      onSuccess: async () => {
        facturas = await api.facturas();
        const [dashboard, cartera] = await Promise.all([api.dashboard(), api.cartera()]);
        dashboardData = dashboard;
        carteraPorFactura = new Map(cartera.map((cuenta) => [cuenta.facturaId, cuenta]));
        renderKpis();
        renderTabla();
        closeModal('invoiceModal');
      }
    });
  });
}

/* ------------------------------------------------------------------ */
/* Formulario de creación de factura                                   */
/* ------------------------------------------------------------------ */

function conceptRows(): Array<{
  descripcion: string;
  cantidad: number;
  precioUnitario: number;
  row: HTMLTableRowElement;
}> {
  return $$<HTMLTableRowElement>('#conceptosBody tr').map((row) => ({
    descripcion: ($<HTMLInputElement>('.concepto-descripcion', row)?.value || '').trim(),
    cantidad: Number(($<HTMLInputElement>('.concepto-cantidad', row)?.value || '0').replace(',', '.')),
    precioUnitario: Number(($<HTMLInputElement>('.concepto-precio', row)?.value || '0').replace(',', '.')),
    row
  }));
}

function addConceptRow(descripcion = '', cantidad = 1, precio = 0): void {
  const tbody = $('#conceptosBody');
  if (!tbody) return;
  const row = document.createElement('tr');
  row.innerHTML = `
    <td><input type="text" class="concepto-descripcion" maxlength="120" placeholder="Ej. Flete internacional" value="${escapeHtml(descripcion)}"></td>
    <td><input type="number" class="concepto-cantidad" min="1" step="1" value="${cantidad}"></td>
    <td><input type="number" class="concepto-precio" min="0" step="0.01" value="${precio}"></td>
    <td><input type="text" class="concepto-total" readonly tabindex="-1"></td>
    <td><button type="button" class="btn-action btn-action-danger concepto-remove" aria-label="Eliminar línea">×</button></td>
  `;
  tbody.appendChild(row);
  row.querySelectorAll<HTMLInputElement>('input').forEach((input) => {
    input.addEventListener('input', updateTotals);
  });
  row.querySelector<HTMLButtonElement>('.concepto-remove')?.addEventListener('click', () => {
    row.remove();
    updateTotals();
  });
  updateTotals();
}

function updateTotals(): void {
  const rows = conceptRows();
  const subtotal = rows.reduce((sum, item) => {
    const total = Number.isFinite(item.cantidad * item.precioUnitario)
      ? item.cantidad * item.precioUnitario
      : 0;
    const output = $<HTMLInputElement>('.concepto-total', item.row);
    if (output) output.value = total.toFixed(2);
    return sum + total;
  }, 0);
  const ivaRate = dashboardData?.ivaRate || 0;
  const impuestos = subtotal * ivaRate;
  const total = subtotal + impuestos;
  const currency = dashboardData?.moneda || 'USD';
  const subtotalEl = $('#facturaSubtotal');
  const impuestosEl = $('#facturaImpuestos');
  const totalEl = $('#facturaTotal');
  if (subtotalEl) subtotalEl.textContent = money(subtotal, currency);
  if (impuestosEl) impuestosEl.textContent = money(impuestos, currency);
  if (totalEl) totalEl.textContent = money(total, currency);
}

export async function initCrearFacturaForm(): Promise<void> {
  const form = $<HTMLFormElement>('#facturaForm');
  if (!form) return;
  const submitButton = $<HTMLButtonElement>('#facturaSubmit');
  if (!fieldValue('facturaFecha')) {
    const fecha = $<HTMLInputElement>('#facturaFecha');
    if (fecha) fecha.value = today();
  }

  try {
    const [clientesResponse, dashboard, operaciones] = await Promise.all([
      api.clientes('', 0, 100),
      api.dashboard(),
      api.operaciones()
    ]);
    dashboardData = dashboard;
    setCurrency(dashboard.moneda);

    const clientesList = $('#clientesList');
    if (clientesList) {
      clientesList.innerHTML = clientesResponse.content
        .map(
          (cliente) =>
            `<option value="${escapeHtml(cliente.nombre)} | ${escapeHtml(cliente.numDoc)}"></option>`
        )
        .join('');
    }

    const operacionesList = $('#operacionesList');
    if (operacionesList) {
      operacionesPorId = new Map((operaciones as Operacion[]).map((operacion) => [operacion.id, operacion]));
      operacionesList.innerHTML = (operaciones as Operacion[])
        .map((operacion) => {
          const cliente = clientesResponse.content.find(
            (item) => item.numDoc === operacion.rutCliente
          );
          return `<option value="${escapeHtml(operacion.id)} | ${escapeHtml(cliente?.nombre || operacion.rutCliente)}"></option>`;
        })
        .join('');
    }
  } catch (error) {
    showFormAlert(form, errorMessage(error, 'No se pudieron cargar los datos de apoyo.'));
  }

  addConceptRow();
  updateTotals();
  $('#addConceptoBtn')?.addEventListener('click', () => addConceptRow());

  const validation = initValidation(
    form,
    {
      facturaCliente: {
        label: 'El cliente',
        required: true,
        custom: (value) => {
          const parsed = parseClienteValue(value);
          if (!parsed.rut) return 'Selecciona un cliente de la lista o escribe su RUT.';
          return null;
        }
      },
      facturaCondicion: { label: 'La condición de pago', required: true },
      facturaFecha: { label: 'La fecha de emisión', required: true, date: true },
      facturaOperacion: {
        label: 'La operación',
        maxLength: 160,
        custom: (value) => {
          if (!value) return null;
          const id = value.includes('|') ? value.split('|')[0].trim() : value.trim();
          if (operacionesPorId.size > 0 && !operacionesPorId.has(id)) {
            return 'Selecciona una operación de la lista o deja el campo vacío.';
          }
          return null;
        }
      }
    },
    {
      onValid: async () => {
        const rows = conceptRows();
        const invalidRow = rows.find(
          (item) =>
            !item.descripcion ||
            !Number.isInteger(item.cantidad) ||
            item.cantidad < 1 ||
            !Number.isFinite(item.precioUnitario) ||
            item.precioUnitario < 0
        );
        if (rows.length === 0) {
          showFormAlert(form, 'Agrega al menos un concepto a la factura.');
          return;
        }
        if (invalidRow) {
          showFormAlert(
            form,
            'Completa la descripción, una cantidad entera mayor a 0 y un precio válido en cada concepto.'
          );
          return;
        }

        setBusy(submitButton, true, 'Emitiendo…');
        try {
          const parsedCliente = parseClienteValue(fieldValue('facturaCliente'));
          const operacionRaw = fieldValue('facturaOperacion');
          const operacionId = operacionRaw.includes('|')
            ? operacionRaw.split('|')[0]?.trim()
            : operacionRaw;
          const payload: FacturaPayload = {
            rutCliente: parsedCliente.rut,
            idOperacionOrigen: operacionId || undefined,
            fechaEmision: fieldValue('facturaFecha') || today(),
            condicionPago: fieldValue('facturaCondicion').toUpperCase(),
            items: rows.map((item) => ({
              descripcion: item.descripcion,
              cantidad: item.cantidad,
              precioUnitario: item.precioUnitario
            }))
          };
          const factura = await api.crearFactura(payload);
          flash(`Factura ${factura.id} emitida correctamente.`, 'success');
          location.href = 'facturacion.html';
        } catch (error) {
          if (error instanceof ApiError && error.details) {
            applyServerDetails(validation, error.details);
          }
          showFormAlert(form, errorMessage(error, 'No se pudo emitir la factura.'));
        } finally {
          setBusy(submitButton, false);
        }
      }
    }
  );
}
