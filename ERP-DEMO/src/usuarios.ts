import { api, ApiError } from './api.js';
import {
  $$,
  $,
  debounce,
  emptyRow,
  errorMessage,
  escapeHtml,
  fieldValue,
  flash,
  loadingRow,
  roleLabel,
  setBusy,
  showFormAlert,
  statusBadge,
  toast
} from './ui.js';
import { applyServerDetails, initValidation } from './validation.js';
import type { ValidationRules } from './validation.js';
import type { EstadoUsuario, Rol, Usuario, UsuarioPayload } from './types.js';

const PERMISOS: Array<{ value: string; label: string }> = [
  { value: 'CLIENTES_VER', label: 'Ver clientes' },
  { value: 'CLIENTES_GESTIONAR', label: 'Gestionar clientes' },
  { value: 'OPERACIONES_VER', label: 'Ver operaciones' },
  { value: 'OPERACIONES_GESTIONAR', label: 'Gestionar operaciones' },
  { value: 'FACTURACION_VER', label: 'Ver facturación' },
  { value: 'FACTURACION_EMITIR', label: 'Emitir facturas' },
  { value: 'PAGOS_REGISTRAR', label: 'Registrar pagos' },
  { value: 'NOTAS_CREDITO_EMITIR', label: 'Emitir notas de crédito' },
  { value: 'CONCILIACION_GESTIONAR', label: 'Gestionar conciliación' },
  { value: 'REPORTES_VER', label: 'Ver reportes' }
];

let usuarios: Usuario[] = [];

function filteredUsuarios(): Usuario[] {
  const query = ($<HTMLInputElement>('#usuarioSearch')?.value || '').trim().toLowerCase();
  const rol = ($<HTMLSelectElement>('#usuarioRol')?.value || '').toLowerCase();
  const estado = ($<HTMLSelectElement>('#usuarioEstado')?.value || '').toLowerCase();

  return usuarios.filter((usuario) => {
    const matchQuery =
      !query ||
      [usuario.nombres, usuario.apellidos, usuario.email, ...(usuario.permisos || [])]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(query));
    const matchRol = !rol || usuario.rol === rol;
    const matchEstado = !estado || usuario.estado === estado;
    return matchQuery && matchRol && matchEstado;
  });
}

function renderResumen(filtered: Usuario[]): void {
  const container = $('#usuariosResumen');
  if (!container) return;
  const activos = filtered.filter((usuario) => usuario.estado === 'activo').length;
  const admins = filtered.filter((usuario) => usuario.rol === 'admin').length;
  container.innerHTML = `
    <div class="card kpi-card">
      <h3>Usuarios del filtro</h3>
      <div class="kpi-value">${filtered.length}</div>
      <p class="trend neutral">Total de cuentas encontradas</p>
    </div>
    <div class="card kpi-card">
      <h3>Usuarios activos</h3>
      <div class="kpi-value">${activos}</div>
      <p class="trend positive">Pueden iniciar sesión</p>
    </div>
    <div class="card kpi-card">
      <h3>Administradores</h3>
      <div class="kpi-value">${admins}</div>
      <p class="trend neutral">Con acceso total al ERP</p>
    </div>
  `;
}

function renderTabla(): void {
  const tbody = $('#usuariosTableBody');
  if (!tbody) return;
  const rows = filteredUsuarios();
  renderResumen(rows);

  if (rows.length === 0) {
    tbody.innerHTML = emptyRow(5, 'No se encontraron usuarios con ese criterio.');
    return;
  }

  tbody.innerHTML = rows
    .map(
      (usuario) => `
        <tr>
          <td>
            <strong>${escapeHtml(`${usuario.nombres} ${usuario.apellidos}`.trim())}</strong>
            <span class="sub-text">${escapeHtml(usuario.email)}</span>
          </td>
          <td>${escapeHtml(roleLabel(usuario.rol))}</td>
          <td>${statusBadge(usuario.estado === 'activo' ? 'ACTIVO' : 'INACTIVO')}</td>
          <td>
            ${
              (usuario.permisos || []).length === 0
                ? '<span class="sub-text">Sin permisos adicionales</span>'
                : (usuario.permisos || [])
                    .slice(0, 3)
                    .map((permiso) => `<span class="permiso-extra">${escapeHtml(permiso)}</span>`)
                    .join(' ') +
                  ((usuario.permisos || []).length > 3
                    ? `<span class="sub-text">+${(usuario.permisos || []).length - 3} más</span>`
                    : '')
            }
          </td>
          <td class="table-actions">
            <a class="btn-action" href="crear-usuario.html?email=${encodeURIComponent(usuario.email)}">Editar</a>
            <button
              type="button"
              class="btn-action ${usuario.estado === 'activo' ? 'btn-action-danger' : 'btn-action-ok'}"
              data-toggle-user="${escapeHtml(usuario.email)}"
              data-estado="${escapeHtml(usuario.estado)}"
            >${usuario.estado === 'activo' ? 'Desactivar' : 'Activar'}</button>
          </td>
        </tr>
      `
    )
    .join('');
}

export async function initUsuariosPage(): Promise<void> {
  const tbody = $('#usuariosTableBody');
  if (!tbody) return;
  tbody.innerHTML = loadingRow(5);

  try {
    const response = await api.usuarios('', 0, 100);
    usuarios = response.content;
    renderTabla();
  } catch (error) {
    tbody.innerHTML = emptyRow(5, errorMessage(error, 'No se pudieron cargar los usuarios.'));
    toast(errorMessage(error), 'error');
  }

  $<HTMLInputElement>('#usuarioSearch')?.addEventListener(
    'input',
    debounce(() => renderTabla(), 150)
  );
  $<HTMLSelectElement>('#usuarioRol')?.addEventListener('change', () => renderTabla());
  $<HTMLSelectElement>('#usuarioEstado')?.addEventListener('change', () => renderTabla());

  $('#recalcularBtn')?.addEventListener('click', async () => {
    const button = $<HTMLButtonElement>('#recalcularBtn');
    if (
      !window.confirm(
        '¿Recalcular el estado de todas las facturas? Se corrigen los estados desalineados con pagos y notas.'
      )
    ) {
      return;
    }
    setBusy(button, true, 'Recalculando…');
    try {
      const resultado = await api.recalcular();
      toast(
        `Se revisaron ${resultado.facturas} factura(s) y se corrigieron ${resultado.corregidas} estado(s).`
      );
    } catch (error) {
      toast(errorMessage(error, 'No se pudo ejecutar el recálculo.'), 'error');
    } finally {
      setBusy(button, false);
    }
  });

  tbody.addEventListener('click', async (event) => {
    const button = (event.target as HTMLElement).closest<HTMLButtonElement>('[data-toggle-user]');
    if (!button) return;
    const email = button.dataset.toggleUser || '';
    const estado = (button.dataset.estado || '').toLowerCase();
    const usuario = usuarios.find((item) => item.email === email);
    if (!usuario) return;
    const nextEstado: EstadoUsuario = estado === 'activo' ? 'inactivo' : 'activo';
    if (!window.confirm(`¿Confirmas ${nextEstado === 'activo' ? 'activar' : 'desactivar'} a ${usuario.nombres} ${usuario.apellidos}?`)) {
      return;
    }

    setBusy(button, true, 'Guardando…');
    try {
      await api.cambiarEstadoUsuario(email, nextEstado);
      toast(`Usuario ${nextEstado === 'activo' ? 'activado' : 'desactivado'} correctamente.`);
      const response = await api.usuarios('', 0, 100);
      usuarios = response.content;
      renderTabla();
    } catch (error) {
      toast(errorMessage(error), 'error');
      setBusy(button, false);
    }
  });
}

/* ------------------------------------------------------------------ */
/* Formulario de creación / edición de usuario                         */
/* ------------------------------------------------------------------ */

export function renderPermisos(container: HTMLElement): void {
  container.innerHTML = PERMISOS.map(
    (permiso) => `
      <label class="checkbox-option">
        <input type="checkbox" name="permisos" value="${escapeHtml(permiso.value)}">
        <span>${escapeHtml(permiso.label)}</span>
      </label>
    `
  ).join('');
}

function fillUsuario(usuario: Usuario): void {
  const values: Record<string, string> = {
    email: usuario.email,
    nombres: usuario.nombres,
    apellidos: usuario.apellidos,
    rol: usuario.rol,
    estado: usuario.estado
  };
  for (const [id, value] of Object.entries(values)) {
    const element = $<HTMLInputElement | HTMLSelectElement>(`#${id}`);
    if (element) element.value = value;
  }
  const seleccionados = new Set(usuario.permisos || []);
  $$<HTMLInputElement>('input[name="permisos"]').forEach((checkbox) => {
    checkbox.checked = seleccionados.has(checkbox.value);
  });
}

export async function initUsuarioForm(): Promise<void> {
  const form = $<HTMLFormElement>('#usuarioForm');
  if (!form) return;

  // Los permisos se generan desde una sola fuente de datos para no repetir HTML.
  const permisosContainer = $('#permisosContainer');
  if (permisosContainer) renderPermisos(permisosContainer);

  const params = new URLSearchParams(location.search);
  const editEmail = (params.get('email') || '').trim().toLowerCase();
  const isEdit = Boolean(editEmail);
  const title = $('#usuarioFormTitle');
  const subtitle = $('#usuarioFormSubtitle');
  const submitButton = $<HTMLButtonElement>('#usuarioSubmit');
  const passwordHelp = $('#passwordHelp');
  let current: Usuario | null = null;

  if (isEdit) {
    if (title) title.textContent = 'Editar usuario';
    if (subtitle) subtitle.textContent = 'Actualiza el rol, estado y permisos de la cuenta.';
    if (submitButton) submitButton.textContent = '✓ Guardar cambios';
    if (passwordHelp) passwordHelp.textContent = 'Deja las contraseñas vacías para conservar la actual.';
    const email = $<HTMLInputElement>('#email');
    if (email) {
      email.readOnly = true;
      email.classList.add('input-readonly');
    }
    try {
      current = await api.usuario(editEmail);
      fillUsuario(current);
    } catch (error) {
      toast(errorMessage(error, 'No se pudo cargar el usuario.'), 'error');
      location.replace('users.html');
      return;
    }
  } else if (passwordHelp) {
    passwordHelp.textContent = 'La contraseña debe tener entre 8 y 72 caracteres.';
  }

  const rules: ValidationRules = {
    email: { label: 'El correo electrónico', required: true, email: true, maxLength: 120 },
    nombres: { label: 'Los nombres', required: true, maxLength: 80 },
    apellidos: { label: 'Los apellidos', required: true, maxLength: 80 },
    rol: { label: 'El rol', required: true },
    estado: { label: 'El estado', required: true },
    password: isEdit
      ? { label: 'La contraseña', minLength: 8, maxLength: 72 }
      : { label: 'La contraseña', required: true, minLength: 8, maxLength: 72 },
    passwordConfirm: {
      label: 'La confirmación de contraseña',
      custom: (value, formElement) => {
        const password = $<HTMLInputElement>('#password', formElement)?.value || '';
        if (!password && !value) return null;
        if (!value) return 'Debes confirmar la contraseña.';
        if (value !== password) return 'Las contraseñas no coinciden.';
        return null;
      }
    }
  };

  const validation = initValidation(form, rules, {
    onValid: async () => {
      setBusy(submitButton, true, isEdit ? 'Guardando…' : 'Creando…');
      try {
        const payload: UsuarioPayload = {
          email: fieldValue('email').toLowerCase(),
          nombres: fieldValue('nombres'),
          apellidos: fieldValue('apellidos'),
          rol: fieldValue('rol').toLowerCase() as Rol,
          estado: fieldValue('estado').toLowerCase() as EstadoUsuario,
          permisos: $$<HTMLInputElement>('input[name="permisos"]:checked', form).map(
            (checkbox) => checkbox.value
          )
        };
        const password = $<HTMLInputElement>('#password')?.value || '';
        if (password) {
          payload.password = password;
          payload.passwordConfirm = $<HTMLInputElement>('#passwordConfirm')?.value || '';
        }

        if (isEdit && current) {
          await api.actualizarUsuario(editEmail, payload);
          flash('Usuario actualizado correctamente.', 'success');
        } else {
          await api.crearUsuario(payload);
          flash('Usuario creado correctamente.', 'success');
        }
        location.href = 'users.html';
      } catch (error) {
        if (error instanceof ApiError && error.details) {
          applyServerDetails(validation, error.details);
        }
        showFormAlert(form, errorMessage(error, 'No se pudo guardar el usuario.'));
      } finally {
        setBusy(submitButton, false);
      }
    }
  });
}
