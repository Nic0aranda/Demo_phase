import { api, ApiError } from './api.js';
import { hasAnyRole, WRITE_ROLES } from './permissions.js';
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
  setBusy,
  showFormAlert,
  statusBadge,
  toast
} from './ui.js';
import { applyServerDetails, initValidation } from './validation.js';
import type { Cliente, ClientePayload } from './types.js';

let allClientes: Cliente[] = [];

function canManageClientes(): boolean {
  return hasAnyRole(WRITE_ROLES.clientes);
}

function renderResumen(filtered: Cliente[]): void {
  const container = $('#clientesResumen');
  if (!container) return;
  const activos = filtered.filter((cliente) => cliente.activo).length;
  const inactivos = filtered.length - activos;
  container.innerHTML = `
    <div class="card kpi-card">
      <h3>Total de clientes</h3>
      <div class="kpi-value">${filtered.length}</div>
      <p class="trend neutral">Resultado del filtro actual</p>
    </div>
    <div class="card kpi-card">
      <h3>Clientes activos</h3>
      <div class="kpi-value">${activos}</div>
      <p class="trend positive">Cuentas habilitadas</p>
    </div>
    <div class="card kpi-card">
      <h3>Clientes inactivos</h3>
      <div class="kpi-value">${inactivos}</div>
      <p class="trend neutral">Cuentas deshabilitadas</p>
    </div>
  `;
}

function filteredClientes(): Cliente[] {
  const query = ($<HTMLInputElement>('#clienteSearch')?.value || '').trim().toLowerCase();
  if (!query) return allClientes;
  return allClientes.filter((cliente) =>
    [cliente.nombre, cliente.numDoc, cliente.email, cliente.telefono, cliente.region]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(query))
  );
}

function renderTabla(): void {
  const tbody = $('#clientesTableBody');
  if (!tbody) return;
  const rows = filteredClientes();
  renderResumen(rows);

  if (rows.length === 0) {
    tbody.innerHTML = emptyRow(6, 'No se encontraron clientes con ese criterio.');
    return;
  }

  const canManage = canManageClientes();
  tbody.innerHTML = rows
    .map(
      (cliente) => `
        <tr>
          <td>
            <strong>${escapeHtml(cliente.nombre)}</strong>
            <span class="sub-text">${escapeHtml(cliente.glosa || cliente.tipoDoc)}</span>
          </td>
          <td>${escapeHtml(cliente.numDoc)}</td>
          <td>
            ${escapeHtml(cliente.email)}
            <span class="sub-text">${escapeHtml(cliente.telefono || 'Sin teléfono')}</span>
          </td>
          <td>${escapeHtml(cliente.region || '—')}</td>
          <td>${statusBadge(cliente.activo ? 'ACTIVO' : 'INACTIVO')}</td>
          <td class="table-actions">
            ${
              canManage
                ? `<a class="btn-action" href="crear-cliente.html?doc=${encodeURIComponent(cliente.numDoc)}">Editar</a>
                   <button
                     type="button"
                     class="btn-action ${cliente.activo ? 'btn-action-danger' : 'btn-action-ok'}"
                     data-toggle-cliente="${escapeHtml(cliente.numDoc)}"
                     data-activo="${cliente.activo}"
                   >${cliente.activo ? 'Desactivar' : 'Activar'}</button>`
                : '<span class="sub-text">—</span>'
            }
          </td>
        </tr>
      `
    )
    .join('');
}

export async function initClientesPage(): Promise<void> {
  const tbody = $('#clientesTableBody');
  if (!tbody) return;
  tbody.innerHTML = loadingRow(6);

  try {
    const response = await api.clientes('', 0, 100);
    allClientes = response.content;
    renderTabla();
  } catch (error) {
    tbody.innerHTML = emptyRow(6, errorMessage(error, 'No se pudieron cargar los clientes.'));
    toast(errorMessage(error), 'error');
  }

  const search = $<HTMLInputElement>('#clienteSearch');
  search?.addEventListener(
    'input',
    debounce(() => renderTabla(), 150)
  );

  tbody.addEventListener('click', async (event) => {
    const button = (event.target as HTMLElement).closest<HTMLButtonElement>('[data-toggle-cliente]');
    if (!button) return;
    const numDoc = button.dataset.toggleCliente || '';
    const activo = button.dataset.activo === 'true';
    const cliente = allClientes.find((item) => item.numDoc === numDoc);
    if (!cliente) return;
    const action = activo ? 'desactivar' : 'activar';
    if (!confirmAction(`¿Confirmas ${action} al cliente ${cliente.nombre}?`)) return;

    setBusy(button, true, 'Guardando…');
    try {
      await api.cambiarEstadoCliente(numDoc, !activo);
      toast(`Cliente ${activo ? 'desactivado' : 'activado'} correctamente.`);
      const response = await api.clientes('', 0, 100);
      allClientes = response.content;
      renderTabla();
    } catch (error) {
      toast(errorMessage(error), 'error');
      setBusy(button, false);
    }
  });
}

function fillForm(cliente: Cliente): void {
  const values: Record<string, string> = {
    nombre: cliente.nombre,
    glosa: cliente.glosa || '',
    tipoDoc: cliente.tipoDoc?.toUpperCase() || 'RUT',
    numDoc: cliente.numDoc,
    pais: (cliente.pais || 'cl').toLowerCase(),
    region: cliente.region || '',
    direccion: cliente.direccion || '',
    telefono: cliente.telefono || '',
    email: cliente.email || ''
  };
  for (const [id, value] of Object.entries(values)) {
    const element = $<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>(`#${id}`);
    if (element) element.value = value;
  }
}

function buildPayload(activo: boolean): ClientePayload {
  const tipoDoc = fieldValue('tipoDoc').toUpperCase();
  return {
    nombre: fieldValue('nombre'),
    glosa: fieldValue('glosa') || undefined,
    tipoDoc,
    numDoc: fieldValue('numDoc'),
    pais: fieldValue('pais').toLowerCase(),
    region: fieldValue('region'),
    direccion: fieldValue('direccion'),
    telefono: fieldValue('telefono') || undefined,
    email: fieldValue('email').toLowerCase(),
    activo
  };
}

export async function initClienteForm(): Promise<void> {
  const form = $<HTMLFormElement>('#clienteForm');
  if (!form) return;

  const params = new URLSearchParams(location.search);
  const editDoc = params.get('doc');
  const title = $<HTMLElement>('#clienteFormTitle');
  const subtitle = $<HTMLElement>('#clienteFormSubtitle');
  const submitButton = $<HTMLButtonElement>('#clienteSubmit');
  let currentCliente: Cliente | null = null;

  if (editDoc) {
    title!.textContent = 'Editar cliente';
    subtitle!.textContent = 'Actualiza los datos de la empresa y sus canales de contacto.';
    if (submitButton) submitButton.textContent = '✓ Guardar cambios';
    try {
      currentCliente = await api.cliente(editDoc);
      fillForm(currentCliente);
      const numDoc = $<HTMLInputElement>('#numDoc');
      if (numDoc) {
        numDoc.readOnly = true;
        numDoc.classList.add('input-readonly');
      }
    } catch (error) {
      toast(errorMessage(error, 'No se pudo cargar el cliente.'), 'error');
      location.replace('clientes.html');
      return;
    }
  }

  initCharacterCounters(form);

  const validation = initValidation(
    form,
    {
      nombre: { label: 'El nombre o razón social', required: true, maxLength: 120 },
      glosa: { label: 'La glosa', maxLength: 250 },
      tipoDoc: { label: 'El tipo de documento', required: true },
      numDoc: {
        label: 'El número de documento',
        required: true,
        maxLength: 20,
        custom: (value) => {
          const tipo = fieldValue('tipoDoc').toUpperCase();
          if (tipo === 'RUT') {
            const normalized = value.replace(/\./g, '').replace(/\s/g, '').toUpperCase();
            if (!/^\d{7,9}-[\dK]$/.test(normalized)) {
              return 'El RUT debe tener el formato 12345678-9.';
            }
          } else {
            const normalized = value.replace(/\s/g, '').toUpperCase();
            if (!/^[A-Z0-9-]{3,20}$/.test(normalized)) {
              return 'El documento debe tener entre 3 y 20 caracteres alfanuméricos.';
            }
          }
          return null;
        }
      },
      pais: { label: 'El país', required: true },
      region: { label: 'La región', required: true, maxLength: 80 },
      direccion: { label: 'La dirección', required: true, maxLength: 200 },
      telefono: {
        label: 'El teléfono',
        maxLength: 30,
        pattern: {
          regex: /^[+()\d\s.-]{6,30}$/,
          message: 'El teléfono tiene un formato inválido.'
        }
      },
      email: { label: 'El email', required: true, email: true, maxLength: 120 }
    },
    {
      onValid: async () => {
        setBusy(submitButton, true, editDoc ? 'Guardando…' : 'Creando…');
        try {
          const payload = buildPayload(currentCliente?.activo ?? true);
          if (editDoc && currentCliente) {
            await api.actualizarCliente(editDoc, payload);
            flash('Cliente actualizado correctamente.', 'success');
          } else {
            await api.crearCliente(payload);
            flash('Cliente creado correctamente.', 'success');
          }
          location.href = 'clientes.html';
        } catch (error) {
          if (error instanceof ApiError && error.details) {
            applyServerDetails(validation, error.details);
          }
          showFormAlert(form, errorMessage(error, 'No se pudo guardar el cliente.'));
        } finally {
          setBusy(submitButton, false);
        }
      }
    }
  );
}
