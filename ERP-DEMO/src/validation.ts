import { escapeHtml } from './ui.js';

export interface FieldRules {
  label?: string;
  required?: boolean | string;
  email?: boolean | string;
  minLength?: number;
  maxLength?: number;
  pattern?: { regex: RegExp; message: string };
  rut?: boolean;
  document?: boolean;
  number?: { min?: number; max?: number; message?: string };
  date?: boolean;
  matches?: { field: string; message?: string };
  custom?: (value: string, form: HTMLFormElement) => string | null;
}

export type ValidationRules = Record<string, FieldRules>;

type FieldElement = HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;

export interface FormValidation {
  validate(): boolean;
  clear(): void;
  showErrors(details: Record<string, string>): void;
  setError(field: string, message: string): void;
}

function escapeSelector(value: string): string {
  if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') return CSS.escape(value);
  return value.replace(/[^a-zA-Z0-9_-]/g, '\\$&');
}

function getField(form: HTMLFormElement, field: string): FieldElement | null {
  const selector = escapeSelector(field);
  return (
    form.querySelector<FieldElement>(`[name="${selector}"]`) ||
    form.querySelector<FieldElement>(`[id="${selector}"]`)
  );
}

function labelFor(field: string, element: FieldElement, rules: FieldRules): string {
  if (rules.label) return rules.label;
  const id = element.id || field;
  const label = document.querySelector<HTMLLabelElement>(`label[for="${escapeSelector(id)}"]`);
  return label?.textContent?.replace(/\*/g, '').replace(/\s+/g, ' ').trim() || field;
}

function errorElement(form: HTMLFormElement, field: string): HTMLElement {
  const selector = escapeSelector(field);
  let error = form.querySelector<HTMLElement>(`[data-error-for="${selector}"]`);
  if (error) return error;

  const element = getField(form, field);
  error = document.createElement('span');
  error.className = 'field-error';
  error.dataset.errorFor = field;
  error.hidden = true;
  element?.insertAdjacentElement('afterend', error);
  return error;
}

function setFieldError(form: HTMLFormElement, field: string, message: string | null): void {
  const element = getField(form, field);
  const error = errorElement(form, field);
  if (message) {
    element?.classList.add('input-invalid');
    element?.setAttribute('aria-invalid', 'true');
    error.textContent = message;
    error.hidden = false;
  } else {
    element?.classList.remove('input-invalid');
    element?.removeAttribute('aria-invalid');
    error.textContent = '';
    error.hidden = true;
  }
}

function validateField(
  form: HTMLFormElement,
  field: string,
  rules: FieldRules
): string | null {
  const element = getField(form, field);
  if (!element || element.disabled) return null;

  const value = element.value.trim();
  const label = labelFor(field, element, rules);

  if (rules.required && !value) {
    return typeof rules.required === 'string' ? rules.required : `${label} es obligatorio.`;
  }
  if (!value) return null;

  if (rules.email) {
    const valid = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value);
    if (!valid) return typeof rules.email === 'string' ? rules.email : `${label} no tiene un formato válido.`;
  }

  if (rules.minLength !== undefined && value.length < rules.minLength) {
    return `${label} debe tener al menos ${rules.minLength} caracteres.`;
  }

  if (rules.maxLength !== undefined && value.length > rules.maxLength) {
    return `${label} no puede superar ${rules.maxLength} caracteres.`;
  }

  if (rules.pattern && !rules.pattern.regex.test(value)) {
    return rules.pattern.message;
  }

  if (rules.rut) {
    const normalized = value.replace(/\./g, '').replace(/\s/g, '').toUpperCase();
    if (!/^\d{7,9}-[\dK]$/.test(normalized)) {
      return `${label} debe tener el formato 12345678-9.`;
    }
  }

  if (rules.document) {
    const normalized = value.replace(/\s/g, '').toUpperCase();
    if (!/^[A-Z0-9-]{3,20}$/.test(normalized)) {
      return `${label} debe tener entre 3 y 20 caracteres alfanuméricos.`;
    }
  }

  if (rules.number) {
    const numeric = Number(value.replace(',', '.'));
    if (!Number.isFinite(numeric)) {
      return rules.number.message || `${label} debe ser un número válido.`;
    }
    if (rules.number.min !== undefined && numeric < rules.number.min) {
      return rules.number.message || `${label} debe ser mayor o igual a ${rules.number.min}.`;
    }
    if (rules.number.max !== undefined && numeric > rules.number.max) {
      return rules.number.message || `${label} debe ser menor o igual a ${rules.number.max}.`;
    }
  }

  if (rules.date) {
    if (Number.isNaN(new Date(`${value}T00:00:00`).getTime())) {
      return `${label} no es una fecha válida.`;
    }
  }

  if (rules.matches) {
    const other = getField(form, rules.matches.field);
    if (!other || other.value !== value) {
      return rules.matches.message || `${label} no coincide.`;
    }
  }

  if (rules.custom) {
    return rules.custom(value, form);
  }

  return null;
}

export function initValidation(
  form: HTMLFormElement,
  rules: ValidationRules,
  options: { onValid?: () => void | Promise<void> } = {}
): FormValidation {
  form.setAttribute('novalidate', 'novalidate');
  const touched = new Set<string>();

  const validateFieldNow = (field: string): boolean => {
    const fieldRules = rules[field];
    if (!fieldRules) return true;
    const error = validateField(form, field, fieldRules);
    setFieldError(form, field, error);
    return !error;
  };

  const validate = (): boolean => {
    let firstInvalid: FieldElement | null = null;
    let valid = true;
    for (const field of Object.keys(rules)) {
      const error = validateField(form, field, rules[field]);
      setFieldError(form, field, error);
      if (error) {
        valid = false;
        if (!firstInvalid) firstInvalid = getField(form, field);
      }
    }
    firstInvalid?.focus();
    return valid;
  };

  const clear = (): void => {
    touched.clear();
    for (const field of Object.keys(rules)) setFieldError(form, field, null);
  };

  const showErrors = (details: Record<string, string>): void => {
    for (const [field, message] of Object.entries(details || {})) {
      if (getField(form, field)) {
        setFieldError(form, field, message);
        touched.add(field);
      }
    }
  };

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    if (form.dataset.submitting === 'true') return;
    if (!validate()) return;
    if (!options.onValid) return;

    form.dataset.submitting = 'true';
    Promise.resolve(options.onValid())
      .catch(() => {
        // Los errores se manejan en cada pagina.
      })
      .finally(() => {
        delete form.dataset.submitting;
      });
  });

  for (const field of Object.keys(rules)) {
    const element = getField(form, field);
    if (!element) continue;
    element.addEventListener('blur', () => {
      touched.add(field);
      validateFieldNow(field);
    });
    element.addEventListener('input', () => {
      if (touched.has(field)) validateFieldNow(field);
    });
    element.addEventListener('change', () => {
      if (touched.has(field)) validateFieldNow(field);
    });
  }

  return { validate, clear, showErrors, setError: (field, message) => setFieldError(form, field, message) };
}

export function applyServerDetails(
  validation: FormValidation,
  details: Record<string, string> | undefined
): boolean {
  if (!details || Object.keys(details).length === 0) return false;
  validation.showErrors(details);
  return true;
}

export function buildFieldError(field: string, message: string): string {
  return `<span class="field-error" data-error-for="${escapeHtml(field)}">${escapeHtml(message)}</span>`;
}
