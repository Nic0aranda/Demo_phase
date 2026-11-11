import { ApiError } from './api.js';
import { getCurrency } from './storage.js';

export function $<T extends Element = HTMLElement>(
  selector: string,
  root: ParentNode = document
): T | null {
  return root.querySelector<T>(selector);
}

export function $$<T extends Element = HTMLElement>(
  selector: string,
  root: ParentNode = document
): T[] {
  return Array.from(root.querySelectorAll<T>(selector));
}

export function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function initials(name: string): string {
  const parts = name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2);
  return parts.map((part) => part.charAt(0).toUpperCase()).join('') || 'US';
}

export function roleLabel(rol: string): string {
  const labels: Record<string, string> = {
    admin: 'Administrador',
    comercial: 'Área Comercial',
    operaciones: 'Área Operativa',
    contabilidad: 'Contabilidad y Finanzas'
  };
  return labels[rol] || rol;
}

export function money(value: number | string | null | undefined, currency?: string): string {
  const amount = Number(value ?? 0);
  const code = currency || getCurrency();
  try {
    return new Intl.NumberFormat('es-CL', {
      style: 'currency',
      currency: code,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(Number.isFinite(amount) ? amount : 0);
  } catch {
    return `${code} ${amount.toFixed(2)}`;
  }
}

export function number(value: number | string | null | undefined): string {
  return new Intl.NumberFormat('es-CL').format(Number(value ?? 0));
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—';
  const normalized = value.length === 10 ? `${value}T00:00:00` : value;
  const date = new Date(normalized);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('es-CL', {
    day: '2-digit',
    month: 'short',
    year: 'numeric'
  }).format(date);
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('es-CL', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date);
}

export function today(): string {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}

export function daysFromNow(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}

const STATUS_LABELS: Record<string, string> = {
  AL_DIA: 'Al día',
  POR_VENCER: 'Por vencer',
  EN_CURSO: 'En curso',
  PLANIFICADA: 'Planificada',
  FINALIZADA: 'Finalizada',
  CANCELADA: 'Cancelada',
  ATRASADO: 'Atrasado',
  CRITICO: 'Crítico',
  PENDIENTE: 'Pendiente',
  PAGADA: 'Pagada',
  VENCIDA: 'Vencida',
  ANULADA: 'Anulada',
  ANULADO: 'Anulado',
  ACTIVO: 'Activo',
  ACTIVA: 'Activa',
  INACTIVO: 'Inactivo',
  INCOMPLETA: 'Incompleta',
  CONCILIADO: 'Conciliado',
  EMITIDA: 'Emitida',
  REGISTRADO: 'Registrado',
  SIMULADO: 'Simulado',
  CUMPLIDA: 'Cumplida',
  INCUMPLIDA: 'Incumplida',
  BORRADOR: 'Borrador',
  ENVIADO: 'Enviado',
  APROBADO: 'Aprobado',
  RECHAZADO: 'Rechazado',
  IMPORTACION: 'Importación',
  EXPORTACION: 'Exportación',
  NACIONAL: 'Nacional',
  MARITIMA: 'Marítima',
  AEREA: 'Aérea',
  TERRESTRE: 'Terrestre',
  MULTIMODAL: 'Multimodal',
  TRANSFERENCIA: 'Transferencia',
  CHEQUE: 'Cheque',
  EFECTIVO: 'Efectivo',
  TARJETA: 'Tarjeta',
  OTRO: 'Otro',
  DESCUENTO: 'Descuento',
  DEVOLUCION: 'Devolución',
  ANULACION: 'Anulación'
};

function statusTone(estado: string): string {
  const normalized = estado.toUpperCase();
  if (
    ['ACTIVO', 'ACTIVA', 'AL_DIA', 'PAGADA', 'CONCILIADO', 'EMITIDA', 'CUMPLIDA', 'REGISTRADO', 'FINALIZADA', 'APROBADO'].includes(
      normalized
    )
  ) {
    return 'active';
  }
  if (['INACTIVO', 'ANULADA', 'ANULADO', 'INCUMPLIDA', 'CANCELADA'].includes(normalized)) {
    return 'inactive';
  }
  if (['VENCIDA', 'ATRASADO', 'CRITICO', 'ERROR', 'RECHAZADO'].includes(normalized)) {
    return 'error';
  }
  if (['EN_CURSO', 'CONCILIADO', 'ENVIADO'].includes(normalized)) {
    return 'process';
  }
  return 'pending';
}

export function statusLabel(estado: string | null | undefined): string {
  if (!estado) return '—';
  const key = estado.trim().toUpperCase();
  return STATUS_LABELS[key] || key.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
}

export function statusBadge(estado: string | null | undefined): string {
  if (!estado) return '<span class="status-badge inactive">—</span>';
  const normalized = estado.trim().toUpperCase();
  return `<span class="status-badge ${statusTone(normalized)}">${escapeHtml(statusLabel(normalized))}</span>`;
}

export function debounce<T extends (...args: never[]) => void>(fn: T, wait = 300): T {
  let timer: number | undefined;
  return ((...args: Parameters<T>) => {
    if (timer) window.clearTimeout(timer);
    timer = window.setTimeout(() => fn(...args), wait);
  }) as T;
}

export function loadingRow(colspan: number, message = 'Cargando información…'): string {
  return `<tr><td colspan="${colspan}" class="table-message">${escapeHtml(message)}</td></tr>`;
}

export function emptyRow(colspan: number, message = 'No hay registros para mostrar.'): string {
  return `<tr><td colspan="${colspan}" class="table-message table-empty">${escapeHtml(message)}</td></tr>`;
}

export function initCharacterCounters(root: ParentNode = document): void {
  $$<HTMLTextAreaElement>('textarea[maxlength]', root).forEach((textarea) => {
    const max = Number(textarea.getAttribute('maxlength') || 0);
    if (!max) return;
    let counter = textarea.parentElement?.querySelector<HTMLElement>('.char-count') || null;
    if (!counter) {
      counter = document.createElement('span');
      counter.className = 'char-count';
      textarea.insertAdjacentElement('afterend', counter);
    }
    const update = () => {
      const remaining = max - textarea.value.length;
      counter!.textContent = `${textarea.value.length} / ${max} caracteres (quedan ${remaining})`;
      counter!.classList.toggle('char-count-limit', remaining < 0);
    };
    textarea.addEventListener('input', update);
    update();
  });
}

export function setText(selector: string, value: string, root: ParentNode = document): void {
  const element = $(selector, root);
  if (element) element.textContent = value;
}

/**
 * Lee y normaliza el valor de un campo por su id. Centraliza el patrón que
 * usan todos los formularios para construir sus payloads.
 */
export function fieldValue(id: string, root: ParentNode = document): string {
  const element = root.querySelector<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>(
    `#${id}`
  );
  return (element?.value || '').trim();
}

export function setHtml(selector: string, value: string, root: ParentNode = document): void {
  const element = $(selector, root);
  if (element) element.innerHTML = value;
}

export function setBusy(button: HTMLButtonElement | null, busy: boolean, busyText = 'Procesando…'): void {
  if (!button) return;
  if (busy) {
    if (!button.dataset.originalText) button.dataset.originalText = button.textContent || '';
    button.disabled = true;
    button.classList.add('is-busy');
    button.textContent = busyText;
  } else {
    button.disabled = false;
    button.classList.remove('is-busy');
    if (button.dataset.originalText) {
      button.textContent = button.dataset.originalText;
      delete button.dataset.originalText;
    }
  }
}

export function showFormAlert(
  form: HTMLFormElement,
  message: string,
  type: 'error' | 'success' | 'info' = 'error'
): HTMLElement {
  let alert = form.querySelector<HTMLElement>('[data-form-alert]');
  if (!alert) {
    alert = document.createElement('div');
    alert.dataset.formAlert = 'true';
    alert.className = 'form-alert';
    const actions = form.querySelector('.form-actions');
    if (actions) form.insertBefore(alert, actions);
    else form.appendChild(alert);
  }
  alert.className = `form-alert form-alert-${type}`;
  alert.textContent = message;
  alert.hidden = false;
  return alert;
}

export function clearFormAlert(form: HTMLFormElement): void {
  form.querySelectorAll<HTMLElement>('[data-form-alert]').forEach((alert) => {
    alert.hidden = true;
    alert.textContent = '';
  });
}

export function toast(message: string, type: 'success' | 'error' | 'info' = 'success'): void {
  let container = $<HTMLElement>('.toast-container');
  if (!container) {
    container = document.createElement('div');
    container.className = 'toast-container';
    container.setAttribute('aria-live', 'polite');
    document.body.appendChild(container);
  }
  const item = document.createElement('div');
  item.className = `toast toast-${type}`;
  item.setAttribute('role', 'status');
  item.textContent = message;
  container.appendChild(item);
  window.setTimeout(() => {
    item.classList.add('toast-out');
    window.setTimeout(() => item.remove(), 250);
  }, 4500);
}

export function flash(message: string, type: 'success' | 'error' | 'info' = 'success'): void {
  sessionStorage.setItem('erp.flash', JSON.stringify({ message, type }));
}

export function consumeFlash(): void {
  const raw = sessionStorage.getItem('erp.flash');
  if (!raw) return;
  sessionStorage.removeItem('erp.flash');
  try {
    const data = JSON.parse(raw) as { message: string; type: 'success' | 'error' | 'info' };
    toast(data.message, data.type);
  } catch {
    // Ignorar un flash corrupto.
  }
}

export function openModal(id: string): void {
  const modal = document.getElementById(id);
  if (!modal) return;
  modal.classList.add('active');
  modal.setAttribute('aria-hidden', 'false');
  const focusable = modal.querySelector<HTMLElement>(
    'input:not([type="hidden"]), select, textarea, button'
  );
  window.setTimeout(() => focusable?.focus(), 50);
}

export function closeModal(id: string): void {
  const modal = document.getElementById(id);
  if (!modal) return;
  modal.classList.remove('active');
  modal.setAttribute('aria-hidden', 'true');
}

export function initModalDismiss(id: string): void {
  const modal = document.getElementById(id);
  if (!modal) return;
  modal.querySelectorAll<HTMLElement>('[data-close-modal]').forEach((button) => {
    button.addEventListener('click', () => closeModal(id));
  });
  modal.addEventListener('click', (event) => {
    if (event.target === modal) closeModal(id);
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && modal.classList.contains('active')) closeModal(id);
  });
}

export function parseClienteValue(value: string): { nombre: string; rut: string } {
  const raw = value.trim();
  const separator = raw.indexOf('|');
  if (separator >= 0) {
    return {
      nombre: raw.slice(0, separator).trim(),
      rut: raw.slice(separator + 1).trim()
    };
  }
  const looksLikeRut = /^[\d.]+-[\dkK]$/.test(raw) || /^[\d.]+$/.test(raw);
  return {
    nombre: looksLikeRut ? '' : raw,
    rut: raw
  };
}

export function confirmAction(message: string): boolean {
  return window.confirm(message);
}

export function errorMessage(error: unknown, fallback = 'Ocurrió un error inesperado.'): string {
  if (error instanceof ApiError) {
    if (error.status === 0) return error.message;
    if (error.status === 403) return 'No tienes permisos para realizar esta acción.';
    if (error.status === 429) {
      const minutes = error.retryAfter ? Math.max(1, Math.ceil(error.retryAfter / 60)) : 15;
      return `${error.message} Intenta nuevamente en ${minutes} minuto${minutes === 1 ? '' : 's'}.`;
    }
    const detail = error.details ? Object.values(error.details)[0] : undefined;
    return detail || error.message || fallback;
  }
  return error instanceof Error ? error.message : fallback;
}

export function promptModal(
  title: string,
  label: string,
  defaultValue = '',
  placeholder = ''
): Promise<string | null> {
  return new Promise((resolve) => {
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay active';
    overlay.innerHTML = `
      <div class="modal-content modal-small" role="dialog" aria-modal="true">
        <div class="modal-header">
          <h2>${escapeHtml(title)}</h2>
          <button type="button" class="close-modal" data-cancel>&times;</button>
        </div>
        <form class="modal-body" novalidate>
          <div class="form-group">
            <label for="promptInput">${escapeHtml(label)}</label>
            <input id="promptInput" type="text" value="${escapeHtml(defaultValue)}" placeholder="${escapeHtml(placeholder)}">
            <span class="field-error" data-prompt-error hidden></span>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn-secondary" data-cancel>Cancelar</button>
            <button type="submit" class="btn-primary">Aceptar</button>
          </div>
        </form>
      </div>
    `;
    document.body.appendChild(overlay);

    const input = overlay.querySelector<HTMLInputElement>('#promptInput');
    const error = overlay.querySelector<HTMLElement>('[data-prompt-error]');
    const form = overlay.querySelector<HTMLFormElement>('form');
    const cleanup = (value: string | null) => {
      overlay.remove();
      resolve(value);
    };

    overlay.querySelectorAll<HTMLElement>('[data-cancel]').forEach((button) => {
      button.addEventListener('click', () => cleanup(null));
    });
    overlay.addEventListener('click', (event) => {
      if (event.target === overlay) cleanup(null);
    });
    document.addEventListener(
      'keydown',
      (event) => {
        if (event.key === 'Escape' && overlay.isConnected) cleanup(null);
      },
      { once: true }
    );

    form?.addEventListener('submit', (event) => {
      event.preventDefault();
      const value = input?.value.trim() || '';
      if (!value) {
        if (error) {
          error.textContent = 'Este campo es obligatorio.';
          error.hidden = false;
        }
        input?.focus();
        return;
      }
      cleanup(value);
    });

    window.setTimeout(() => input?.focus(), 60);
  });
}
