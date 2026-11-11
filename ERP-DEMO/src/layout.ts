import { api, ApiError } from './api.js';
import { clearSession, fullName, getToken, getUser, setUser } from './storage.js';
import {
  $,
  $$,
  closeModal,
  consumeFlash,
  errorMessage,
  escapeHtml,
  fieldValue,
  flash,
  initModalDismiss,
  initials,
  openModal,
  roleLabel,
  setBusy,
  showFormAlert,
  toast
} from './ui.js';
import { applyServerDetails, initValidation } from './validation.js';
import type { Rol, Usuario } from './types.js';

interface MenuItem {
  key: string;
  label: string;
  file: string;
  section: 'dashboard' | 'admin' | 'compartidas';
  roles?: Rol[];
}

const MENU: MenuItem[] = [
  { key: 'dashboard', label: 'Panel principal', file: 'dashboard.html', section: 'dashboard' },
  { key: 'clientes', label: 'Clientes', file: 'clientes.html', section: 'compartidas' },
  {
    key: 'presupuestos',
    label: 'Presupuestos',
    file: 'presupuestos.html',
    section: 'compartidas'
  },
  { key: 'operaciones', label: 'Operaciones', file: 'operaciones.html', section: 'compartidas' },
  { key: 'facturacion', label: 'Facturación', file: 'facturacion.html', section: 'compartidas' },
  {
    key: 'cartera',
    label: 'Cuentas por cobrar',
    file: 'cuentas-por-cobrar.html',
    section: 'compartidas'
  },
  {
    key: 'conciliacion',
    label: 'Conciliación bancaria',
    file: 'conciliacion-bancaria.html',
    section: 'compartidas'
  },
  { key: 'notas', label: 'Notas de crédito', file: 'notas-credito.html', section: 'compartidas' },
  {
    key: 'usuarios',
    label: 'Gestión de usuarios',
    file: 'users.html',
    section: 'admin',
    roles: ['admin']
  }
];

const PAGE_TO_MENU: Record<string, string> = {
  dashboard: 'dashboard',
  clientes: 'clientes',
  'crear-cliente': 'clientes',
  presupuestos: 'presupuestos',
  'crear-presupuesto': 'presupuestos',
  operaciones: 'operaciones',
  'crear-operacion': 'operaciones',
  facturacion: 'facturacion',
  'crear-factura': 'facturacion',
  cartera: 'cartera',
  cobranza: 'cartera',
  conciliacion: 'conciliacion',
  notas: 'notas',
  'crear-nota': 'notas',
  usuarios: 'usuarios',
  'crear-usuario': 'usuarios'
};

function currentSection(): string | null {
  const path = location.pathname.replace(/\\/g, '/');
  const match = /\/views\/([^/]+)\//.exec(path);
  return match?.[1] || null;
}

function viewHref(section: string, file: string): string {
  const current = currentSection();
  if (!current) return `views/${section}/${file}`;
  if (current === section) return file;
  return `../${section}/${file}`;
}

function roleSection(rol: string): string {
  return ['admin', 'comercial', 'operaciones', 'contabilidad'].includes(rol) ? rol : 'admin';
}

function dashboardHref(rol: string): string {
  return viewHref(roleSection(rol), 'dashboard.html');
}

function loginHref(): string {
  return currentSection() ? '../../Index.html' : 'Index.html';
}

function activeMenuKey(): string {
  if (document.body.dataset.menu) return document.body.dataset.menu;
  const page = document.body.dataset.page || '';
  return PAGE_TO_MENU[page] || '';
}

function menuHref(item: MenuItem, user: Usuario): string {
  if (item.section === 'dashboard') return dashboardHref(user.rol);
  return viewHref(item.section, item.file);
}

function renderSidebar(user: Usuario): void {
  const sidebar = $<HTMLElement>('.sidebar');
  if (!sidebar) return;

  const activeKey = activeMenuKey();
  const visible = MENU.filter((item) => !item.roles || item.roles.includes(user.rol));
  const name = fullName(user);

  sidebar.innerHTML = `
    <div class="sidebar-header">
      <div class="logo">
        <span class="logo-icon">N</span>
        <div class="logo-text">
          <strong>ERP EMPRESARIAL</strong>
          <span>Portal de empresa</span>
        </div>
      </div>
    </div>

    <div class="user-profile" title="${escapeHtml(user.email)}">
      <div class="avatar">${escapeHtml(initials(name))}</div>
      <div class="user-info">
        <strong>${escapeHtml(name)}</strong>
        <span>${escapeHtml(roleLabel(user.rol))}</span>
      </div>
    </div>

    <nav class="sidebar-menu" aria-label="Menú principal">
      <p class="menu-label">GENERAL</p>
      <ul>
        ${visible
          .map((item) => {
            const active = item.key === activeKey;
            return `<li${active ? ' class="active"' : ''}>
              <a href="${menuHref(item, user)}"${active ? ' aria-current="page"' : ''}>${escapeHtml(item.label)}</a>
            </li>`;
          })
          .join('')}
      </ul>
    </nav>

    <div class="sidebar-footer">
      <button id="changePasswordBtn" class="btn-sidebar-action" type="button">Cambiar contraseña</button>
      <button id="logoutBtn" class="btn-logout" type="button">Cerrar sesión</button>
    </div>
  `;

  $<HTMLButtonElement>('#logoutBtn', sidebar)?.addEventListener('click', () => {
    clearSession();
    location.href = loginHref();
  });
}

/**
 * Modal compartido por todas las paginas privadas para que cada usuario pueda
 * cambiar su propia contrasena sin pasar por Swagger.
 */
function initPasswordModal(): void {
  if ($('#passwordModal')) return;

  const overlay = document.createElement('div');
  overlay.className = 'modal-overlay';
  overlay.id = 'passwordModal';
  overlay.setAttribute('aria-hidden', 'true');
  overlay.innerHTML = `
    <div class="modal-content modal-small" role="dialog" aria-modal="true" aria-labelledby="passwordModalTitle">
      <div class="modal-header">
        <h2 id="passwordModalTitle">Cambiar contraseña</h2>
        <button type="button" class="close-modal" data-close-modal aria-label="Cerrar">&times;</button>
      </div>
      <form id="passwordForm" class="modal-body" novalidate>
        <div class="form-group">
          <label for="currentPassword">Contraseña actual <span class="required">*</span></label>
          <input type="password" id="currentPassword" name="currentPassword" maxlength="72"
            autocomplete="current-password" required>
        </div>
        <div class="form-group">
          <label for="newPassword">Nueva contraseña <span class="required">*</span></label>
          <input type="password" id="newPassword" name="newPassword" maxlength="72"
            autocomplete="new-password" required>
          <span class="field-help">Entre 8 y 72 caracteres.</span>
        </div>
        <div class="form-group">
          <label for="confirmPassword">Confirmar nueva contraseña <span class="required">*</span></label>
          <input type="password" id="confirmPassword" name="confirmPassword" maxlength="72"
            autocomplete="new-password" required>
        </div>
        <div data-form-alert hidden></div>
        <div class="modal-footer">
          <button type="button" class="btn-secondary" data-close-modal>Cancelar</button>
          <button type="submit" id="passwordSubmit" class="btn-primary">Guardar contraseña</button>
        </div>
      </form>
    </div>
  `;
  document.body.appendChild(overlay);
  initModalDismiss('passwordModal');

  const form = $<HTMLFormElement>('#passwordForm', overlay);
  const submitButton = $<HTMLButtonElement>('#passwordSubmit', overlay);
  if (!form) return;

  const validation = initValidation(
    form,
    {
      currentPassword: { label: 'La contraseña actual', required: true },
      newPassword: {
        label: 'La nueva contraseña',
        required: true,
        minLength: 8,
        maxLength: 72
      },
      confirmPassword: {
        label: 'La confirmación de contraseña',
        required: true,
        matches: { field: 'newPassword', message: 'Las contraseñas no coinciden.' }
      }
    },
    {
      onValid: async () => {
        setBusy(submitButton, true, 'Guardando…');
        try {
          await api.cambiarPassword({
            currentPassword: fieldValue('currentPassword', form),
            newPassword: fieldValue('newPassword', form),
            confirmPassword: fieldValue('confirmPassword', form)
          });
          form.reset();
          validation.clear();
          closeModal('passwordModal');
          toast('Contraseña actualizada correctamente.');
        } catch (error) {
          if (error instanceof ApiError && error.details) {
            applyServerDetails(validation, error.details);
          }
          showFormAlert(form, errorMessage(error, 'No se pudo cambiar la contraseña.'));
        } finally {
          setBusy(submitButton, false);
        }
      }
    }
  );

  $('#changePasswordBtn')?.addEventListener('click', () => {
    form.reset();
    validation.clear();
    const alert = form.querySelector<HTMLElement>('[data-form-alert]');
    if (alert) {
      alert.hidden = true;
      alert.textContent = '';
    }
    openModal('passwordModal');
  });
}

function updateAccountBadge(user: Usuario): void {
  const badge = $<HTMLElement>('.topbar .badge');
  if (!badge) return;
  if (/TIPO DE CUENTA/i.test(badge.textContent || '')) {
    badge.textContent = `TIPO DE CUENTA: ${roleLabel(user.rol)}`;
  }
}

function applyWriteGuards(user: Usuario): void {
  $$<HTMLElement>('[data-write-roles]').forEach((element) => {
    const roles = (element.dataset.writeRoles || '')
      .split(',')
      .map((role) => role.trim())
      .filter(Boolean);
    if (roles.length > 0 && !roles.includes(user.rol)) {
      element.hidden = true;
    }
  });
}

export async function initLayout(): Promise<Usuario> {
  if (!getToken()) {
    location.replace(loginHref());
    throw new Error('Sesión requerida');
  }

  let user = getUser();
  if (!user) {
    try {
      user = await api.me();
      setUser(user);
    } catch (error) {
      clearSession();
      location.replace(loginHref());
      throw error;
    }
  }

  const requiredRoles = (document.body.dataset.requireRole || '')
    .split(',')
    .map((role) => role.trim())
    .filter(Boolean) as Rol[];
  if (requiredRoles.length > 0 && !requiredRoles.includes(user.rol)) {
    flash('No tienes permisos para acceder a esa sección.', 'error');
    location.replace(dashboardHref(user.rol));
    throw new Error('Sin permisos');
  }

  document.body.classList.add(`role-${user.rol}`);
  renderSidebar(user);
  initPasswordModal();
  updateAccountBadge(user);
  applyWriteGuards(user);
  consumeFlash();
  return user;
}

export function logout(): void {
  clearSession();
  location.href = loginHref();
}
