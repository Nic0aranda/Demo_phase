import { api, ApiError } from './api.js';
import { hasAnyRole, WRITE_ROLES } from './permissions.js';
import {
  $,
  closeModal,
  confirmAction,
  debounce,
  emptyRow,
  errorMessage,
  escapeHtml,
  fieldValue,
  flash,
  initCharacterCounters,
  initModalDismiss,
  loadingRow,
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
import type {
  Cliente,
  Operacion,
  OperacionPayload,
  Presupuesto
} from './types.js';

let operaciones: Operacion[] = [];
let clientesPorRut = new Map<string, Cliente>();
let presupuestosPorId = new Map<string, Presupuesto>();
let selectedOperacionId: string | null = null;

function canManageOperaciones(): boolean {
  return hasAnyRole(WRITE_ROLES.operaciones);
}

function buscarCliente(rut: string): Cliente | undefined {
  return clientesPorRut.get(rut) || clientesPorRut.get(rut.replace(/\./g, ''));
}

function renderResumen(filtered: Operacion[]): void {
  const container = $('#operacionesResumen');
  if (!container) return;
  const activas = filtered.filter((op) =>
    ['PLANIFICADA', 'EN_CURSO', 'ATRASADO'].includes((op.estado || '').toUpperCase())
  ).length;
  const finalizadas = filtered.filter((op) => (op.estado || '').toUpperCase() === 'FINALIZADA').length;
  container.innerHTML = `
    <div class="card kpi-card">
      <h3>Total de operaciones</h3>
      <div class="kpi-value">${filtered.length}</div>
      <p class="trend neutral">Resultado del filtro actual</p>
    </div>
    <div class="card kpi-card">
      <h3>Operaciones activas</h3>
      <div class="kpi-value">${activas}</div>
      <p class="trend positive">En planificación o ejecución</p>
    </div>
    <div class="card kpi-card">
      <h3>Operaciones finalizadas</h3>
      <div class="kpi-value">${finalizadas}</div>
      <p class="trend neutral">Procesos cerrados</p>
    </div>
  `;
}

function filteredOperaciones(): Operacion[] {
  const query = ($<HTMLInputElement>('#operacionSearch')?.value || '').trim().toLowerCase();
  if (!query) return operaciones;
  return operaciones.filter((operacion) => {
    const cliente = buscarCliente(operacion.rutCliente);
    return [
      operacion.id,
      operacion.rutCliente,
      cliente?.nombre,
      operacion.tipoOperacion,
      operacion.viaTransporte,
      operacion.referencia,
      operacion.estado
    ]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(query));
  });
}

function hasDocumento(operacion: Operacion, kinds: string[]): boolean {
  return (operacion.documentos || []).some((documento) =>
    kinds.some((kind) => (documento.tipo || '').toUpperCase().includes(kind))
  );
}

function documentIcon(operacion: Operacion, kinds: string[]): string {
  return hasDocumento(operacion, kinds)
    ? '<span class="check-icon">☑️</span>'
    : '<span class="cross-icon">❌</span>';
}

function renderTabla(): void {
  const tbody = $('#operacionesTableBody');
  if (!tbody) return;
  const rows = filteredOperaciones();
  renderResumen(rows);

  if (rows.length === 0) {
    tbody.innerHTML = emptyRow(9, 'No se encontraron operaciones con ese criterio.');
    return;
  }

  tbody.innerHTML = rows
    .map((operacion) => {
      const cliente = buscarCliente(operacion.rutCliente);
      const cerrada = ['FINALIZADA', 'CANCELADA'].includes((operacion.estado || '').toUpperCase());
      const canManage = canManageOperaciones();
      return `
        <tr>
          <td><strong>${escapeHtml(operacion.id)}</strong></td>
          <td>
            ${escapeHtml(cliente?.nombre || 'Cliente no encontrado')}
            <span class="sub-text">${escapeHtml(operacion.rutCliente)}</span>
          </td>
          <td>${escapeHtml(cliente?.numDoc || operacion.rutCliente)}</td>
          <td>${escapeHtml(statusLabel(operacion.tipoOperacion))}</td>
          <td>${escapeHtml(statusLabel(operacion.viaTransporte || '—'))}</td>
          <td class="icon-cell">${documentIcon(operacion, ['INVOICE', 'FACTURA'])}</td>
          <td class="icon-cell">${documentIcon(operacion, ['BL', 'AWB'])}</td>
          <td>${statusBadge(operacion.estado)}</td>
          <td class="actions-cell">
            <button type="button" class="btn-action doc-btn" data-docs="${escapeHtml(operacion.id)}">Documentos</button>
            ${
              canManage && !cerrada
                ? `<button type="button" class="btn-action close-btn" data-close-op="${escapeHtml(operacion.id)}">Cerrar</button>`
                : ''
            }
          </td>
        </tr>
      `;
    })
    .join('');
}

function renderDocumentos(operacion: Operacion): void {
  const body = $('#docsModalBody');
  const title = $('#docsModalTitle');
  if (!body || !title) return;
  title.textContent = `Documentos de ${operacion.id}`;
  const documentos = operacion.documentos || [];
  const canManage = canManageOperaciones();

  body.innerHTML = `
    <p class="modal-subtitle">
      Cliente: <strong>${escapeHtml(buscarCliente(operacion.rutCliente)?.nombre || operacion.rutCliente)}</strong>
      · Estado: ${statusBadge(operacion.estado)}
    </p>

    <div class="docs-list">
      <h4>Documentación registrada</h4>
      ${
        documentos.length === 0
          ? '<p class="table-message table-empty">Aún no hay documentos registrados para esta operación.</p>'
          : `<ul>${documentos
              .map(
                (documento) => `
                  <li>
                    <div class="doc-info">
                      <span class="doc-icon">📄</span>
                      <span>
                        <strong>${escapeHtml(statusLabel(documento.tipo))}</strong>
                        <span class="sub-text">${escapeHtml(documento.numero)}${documento.fecha ? ` · ${escapeHtml(documento.fecha)}` : ''}</span>
                        ${documento.archivo ? `<span class="sub-text doc-file">${escapeHtml(documento.archivo)}</span>` : ''}
                      </span>
                    </div>
                    <div class="doc-actions">
                      ${
                        documento.archivoToken
                          ? `<button type="button" class="btn-link" data-download="${escapeHtml(documento.archivoToken)}">Descargar</button>`
                          : ''
                      }
                      <span class="doc-status ${documento.archivo ? 'uploaded' : 'pending'}">${documento.archivo ? 'Con archivo' : 'Sin archivo'}</span>
                    </div>
                  </li>
                `
              )
              .join('')}</ul>`
      }
    </div>

    ${
      canManage
        ? `<form id="documentoForm" class="inline-form" novalidate>
      <h4 class="inline-form-title">Registrar documento</h4>
      <div class="grid-2-cols">
        <div class="form-group">
          <label for="docTipo">Tipo <span class="required">*</span></label>
          <select id="docTipo" name="docTipo" required>
            <option value="FACTURA">Factura / Invoice</option>
            <option value="BL">BL</option>
            <option value="AWB">AWB</option>
            <option value="CERTIFICADO">Certificado</option>
            <option value="OTRO">Otro</option>
          </select>
        </div>
        <div class="form-group">
          <label for="docNumero">Número <span class="required">*</span></label>
          <input id="docNumero" name="docNumero" type="text" maxlength="40" placeholder="Ej. INV-2026-001" required>
        </div>
        <div class="form-group">
          <label for="docFecha">Fecha</label>
          <input id="docFecha" name="docFecha" type="date" value="${today()}">
        </div>
        <div class="form-group form-group-wide">
          <label for="docArchivo">Archivo <span class="required">*</span></label>
          <input id="docArchivo" name="docArchivo" type="file" required>
          <span class="upload-hint">Formatos habituales (PDF, Word, Excel, imágenes). Máximo 10 MB.</span>
        </div>
      </div>
      <div class="form-actions inline-form-actions">
        <button type="submit" class="btn-primary" id="docSubmit">Agregar documento</button>
      </div>
    </form>`
        : '<p class="field-help">Solo administración y operaciones pueden registrar documentos.</p>'
    }
  `;

  const form = $<HTMLFormElement>('#documentoForm', body);
  if (!form) return;
  const submitButton = $<HTMLButtonElement>('#docSubmit', body);
  const validation = initValidation(
    form,
    {
      docNumero: { label: 'El número de documento', required: true, maxLength: 40 },
      docFecha: { label: 'La fecha', date: true }
    },
    {
      onValid: async () => {
        const archivoInput = $<HTMLInputElement>('#docArchivo', form);
        const archivo = archivoInput?.files?.[0];
        if (!archivo) {
          showFormAlert(form, 'Selecciona un archivo para adjuntar.');
          return;
        }
        if (archivo.size > 10 * 1024 * 1024) {
          showFormAlert(form, 'El archivo supera el límite de 10 MB.');
          return;
        }

        setBusy(submitButton, true, 'Guardando…');
        try {
          const actualizada = await api.subirDocumento(operacion.id, {
            tipo: ($<HTMLSelectElement>('#docTipo', form)?.value || 'OTRO').toUpperCase(),
            numero: ($<HTMLInputElement>('#docNumero', form)?.value || '').trim(),
            fecha: ($<HTMLInputElement>('#docFecha', form)?.value || '').trim() || undefined,
            archivo
          });
          operaciones = operaciones.map((item) => (item.id === actualizada.id ? actualizada : item));
          renderDocumentos(actualizada);
          renderTabla();
          toast('Documento registrado correctamente.');
        } catch (error) {
          if (error instanceof ApiError && error.details) {
            applyServerDetails(validation, error.details);
          }
          showFormAlert(form, errorMessage(error, 'No se pudo registrar el documento.'));
        } finally {
          setBusy(submitButton, false);
        }
      }
    }
  );
}

export async function initOperacionesPage(): Promise<void> {
  const tbody = $('#operacionesTableBody');
  if (!tbody) return;
  tbody.innerHTML = loadingRow(9);
  initModalDismiss('docsModal');

  try {
    const [operacionesResponse, clientesResponse] = await Promise.all([
      api.operaciones(),
      api.clientes('', 0, 100)
    ]);
    operaciones = operacionesResponse;
    clientesPorRut = new Map(
      clientesResponse.content.flatMap((cliente) => [
        [cliente.numDoc, cliente] as [string, Cliente],
        [cliente.numDoc.replace(/\./g, ''), cliente] as [string, Cliente]
      ])
    );
    renderTabla();
  } catch (error) {
    tbody.innerHTML = emptyRow(9, errorMessage(error, 'No se pudieron cargar las operaciones.'));
    toast(errorMessage(error), 'error');
  }

  $<HTMLInputElement>('#operacionSearch')?.addEventListener(
    'input',
    debounce(() => renderTabla(), 150)
  );

  tbody.addEventListener('click', async (event) => {
    const target = event.target as HTMLElement;
    const docsButton = target.closest<HTMLButtonElement>('[data-docs]');
    if (docsButton) {
      const id = docsButton.dataset.docs || '';
      const operacion = operaciones.find((item) => item.id === id);
      if (!operacion) return;
      selectedOperacionId = id;
      renderDocumentos(operacion);
      openModal('docsModal');
      return;
    }

    const closeButton = target.closest<HTMLButtonElement>('[data-close-op]');
    if (closeButton) {
      if (!canManageOperaciones()) return;
      const id = closeButton.dataset.closeOp || '';
      const operacion = operaciones.find((item) => item.id === id);
      if (!operacion) return;
      if (!confirmAction(`¿Cerrar la operación ${operacion.id}? Esta acción marca la operación como finalizada.`)) {
        return;
      }
      setBusy(closeButton, true, 'Cerrando…');
      try {
        await api.cambiarEstadoOperacion(id, 'FINALIZADA');
        toast(`Operación ${id} finalizada.`);
        operaciones = await api.operaciones();
        renderTabla();
      } catch (error) {
        toast(errorMessage(error), 'error');
        setBusy(closeButton, false);
      }
    }
  });

  $('#docsModalBody')?.addEventListener('click', async (event) => {
    const button = (event.target as HTMLElement).closest<HTMLButtonElement>('[data-download]');
    const token = button?.dataset.download;
    if (!button || !token || !selectedOperacionId) return;
    const documento = operaciones
      .find((item) => item.id === selectedOperacionId)
      ?.documentos?.find((item) => item.archivoToken === token);
    setBusy(button, true, 'Descargando…');
    try {
      await api.descargarDocumento(selectedOperacionId, {
        archivoToken: token,
        archivo: documento?.archivo
      });
    } catch (error) {
      toast(errorMessage(error, 'No se pudo descargar el archivo.'), 'error');
    } finally {
      setBusy(button, false);
    }
  });

  // Si se agrego un documento y el modal quedo abierto, permite cerrarlo con los botones.
  $('#docsModal')?.addEventListener('click', (event) => {
    if ((event.target as HTMLElement).matches('[data-close-modal]')) closeModal('docsModal');
  });
}

async function loadClientesDatalist(): Promise<void> {
  const datalist = $('#clientesList');
  if (!datalist) return;
  try {
    const response = await api.clientes('', 0, 100);
    clientesPorRut = new Map(
      response.content.flatMap((cliente) => [
        [cliente.numDoc, cliente] as [string, Cliente],
        [cliente.numDoc.replace(/\./g, ''), cliente] as [string, Cliente]
      ])
    );
    datalist.innerHTML = response.content
      .map(
        (cliente) =>
          `<option value="${escapeHtml(cliente.nombre)} | ${escapeHtml(cliente.numDoc)}"></option>`
      )
      .join('');
  } catch {
    // Si falla la carga, el usuario aun puede escribir el RUT manualmente.
  }
}

async function loadPresupuestosDatalist(): Promise<void> {
  const datalist = $('#presupuestosList');
  if (!datalist) return;
  try {
    const presupuestos = await api.presupuestos();
    const aprobados = presupuestos.filter(
      (presupuesto) => (presupuesto.estado || '').toUpperCase() === 'APROBADO'
    );
    presupuestosPorId = new Map(aprobados.map((presupuesto) => [presupuesto.id, presupuesto]));
    datalist.innerHTML = aprobados
      .map((presupuesto) => {
        const cliente = clientesPorRut.get(presupuesto.rutCliente);
        return `<option value="${escapeHtml(presupuesto.id)} | ${escapeHtml(
          cliente?.nombre || presupuesto.rutCliente
        )}"></option>`;
      })
      .join('');
  } catch {
    // El vinculo con el presupuesto es opcional.
  }
}

export async function initOperacionForm(): Promise<void> {
  const form = $<HTMLFormElement>('#operacionForm');
  if (!form) return;
  initCharacterCounters(form);
  await loadClientesDatalist();
  await loadPresupuestosDatalist();

  const submitButton = $<HTMLButtonElement>('#operacionSubmit');
  const fecha = $<HTMLInputElement>('#fechaInicio');
  if (fecha && !fecha.value) fecha.value = today();

  const validation = initValidation(
    form,
    {
      cliente: {
        label: 'El cliente',
        required: true,
        custom: (value) => {
          const parsed = parseClienteValue(value);
          if (!parsed.rut) return 'Selecciona un cliente de la lista o escribe su RUT.';
          return null;
        }
      },
      tipoOperacion: { label: 'El tipo de operación', required: true },
      viaTransporte: { label: 'La vía de transporte', required: true },
      presupuestoOrigen: {
        label: 'El presupuesto',
        maxLength: 160,
        custom: (value) => {
          if (!value) return null;
          const id = value.includes('|') ? value.split('|')[0].trim() : value.trim();
          if (presupuestosPorId.size > 0 && !presupuestosPorId.has(id)) {
            return 'Selecciona un presupuesto de la lista o deja el campo vacío.';
          }
          return null;
        }
      },
      referencia: { label: 'La referencia', maxLength: 60 },
      observaciones: { label: 'Las observaciones', maxLength: 500 }
    },
    {
      onValid: async () => {
        setBusy(submitButton, true, 'Creando…');
        try {
          const parsed = parseClienteValue(fieldValue('cliente'));
          const presupuestoRaw = fieldValue('presupuestoOrigen');
          const presupuestoId = presupuestoRaw.includes('|')
            ? presupuestoRaw.split('|')[0]?.trim()
            : presupuestoRaw;
          const payload: OperacionPayload = {
            rutCliente: parsed.rut,
            idPresupuestoOrigen: presupuestoId || undefined,
            tipoOperacion: fieldValue('tipoOperacion').toUpperCase(),
            viaTransporte: fieldValue('viaTransporte').toUpperCase(),
            referencia: fieldValue('referencia') || undefined,
            observaciones: fieldValue('observaciones') || undefined,
            fechaInicio: fieldValue('fechaInicio') || undefined
          };
          const operacion = await api.crearOperacion(payload);
          flash(`Operación ${operacion.id} creada correctamente.`, 'success');
          location.href = 'operaciones.html';
        } catch (error) {
          if (error instanceof ApiError && error.details) {
            applyServerDetails(validation, error.details);
          }
          showFormAlert(form, errorMessage(error, 'No se pudo crear la operación.'));
        } finally {
          setBusy(submitButton, false);
        }
      }
    }
  );
}
