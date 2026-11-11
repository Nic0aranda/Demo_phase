import { api, ApiError } from './api.js';
import {
  $,
  errorMessage,
  escapeHtml,
  initModalDismiss,
  money,
  setBusy,
  showFormAlert,
  toast,
  today
} from './ui.js';
import { applyServerDetails, initValidation } from './validation.js';

export interface PaymentModalOptions {
  facturaId: string;
  cliente?: string;
  montoSugerido?: number;
  onSuccess?: () => void | Promise<void>;
}

export function openPaymentModal(options: PaymentModalOptions): void {
  const existing = $('#paymentModal');
  if (existing) existing.remove();

  const overlay = document.createElement('div');
  overlay.id = 'paymentModal';
  overlay.className = 'modal-overlay active';
  overlay.setAttribute('aria-hidden', 'false');
  overlay.innerHTML = `
    <div class="modal-content modal-small" role="dialog" aria-modal="true" aria-labelledby="paymentTitle">
      <div class="modal-header">
        <h2 id="paymentTitle">Registrar pago</h2>
        <button type="button" class="close-modal" data-close-modal>&times;</button>
      </div>
      <form id="paymentForm" class="modal-body" novalidate>
        <p class="modal-subtitle">
          Factura <strong>${escapeHtml(options.facturaId)}</strong>
          ${options.cliente ? ` · ${escapeHtml(options.cliente)}` : ''}
          ${options.montoSugerido ? ` · Saldo: <strong>${escapeHtml(money(options.montoSugerido))}</strong>` : ''}
        </p>
        <div class="grid-2-cols">
          <div class="form-group">
            <label for="paymentMonto">Monto <span class="required">*</span></label>
            <input id="paymentMonto" name="paymentMonto" type="number" min="0.01" step="0.01"
              value="${options.montoSugerido ? Number(options.montoSugerido).toFixed(2) : ''}" required>
          </div>
          <div class="form-group">
            <label for="paymentMedio">Medio de pago <span class="required">*</span></label>
            <select id="paymentMedio" name="paymentMedio" required>
              <option value="TRANSFERENCIA">Transferencia</option>
              <option value="CHEQUE">Cheque</option>
              <option value="EFECTIVO">Efectivo</option>
              <option value="TARJETA">Tarjeta</option>
              <option value="OTRO">Otro</option>
            </select>
          </div>
          <div class="form-group">
            <label for="paymentFecha">Fecha <span class="required">*</span></label>
            <input id="paymentFecha" name="paymentFecha" type="date" value="${today()}" required>
          </div>
          <div class="form-group">
            <label for="paymentReferencia">Referencia</label>
            <input id="paymentReferencia" name="paymentReferencia" type="text" maxlength="80" placeholder="Ej. TRF-88231">
          </div>
        </div>
        <div class="form-group">
          <label for="paymentObservacion">Observación</label>
          <textarea id="paymentObservacion" name="paymentObservacion" rows="2" maxlength="200" placeholder="Detalle opcional del pago"></textarea>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn-secondary" data-close-modal>Cancelar</button>
          <button type="submit" class="btn-primary" id="paymentSubmit">Registrar pago</button>
        </div>
      </form>
    </div>
  `;
  document.body.appendChild(overlay);

  const close = () => overlay.remove();
  overlay.querySelectorAll<HTMLElement>('[data-close-modal]').forEach((button) => {
    button.addEventListener('click', close);
  });
  overlay.addEventListener('click', (event) => {
    if (event.target === overlay) close();
  });
  document.addEventListener(
    'keydown',
    (event) => {
      if (event.key === 'Escape' && overlay.isConnected) close();
    },
    { once: true }
  );

  const form = $<HTMLFormElement>('#paymentForm', overlay);
  const submitButton = $<HTMLButtonElement>('#paymentSubmit', overlay);
  if (!form) return;

  const validation = initValidation(
    form,
    {
      paymentMonto: {
        label: 'El monto',
        required: true,
        number: { min: 0.01, message: 'El monto debe ser mayor a cero.' }
      },
      paymentMedio: { label: 'El medio de pago', required: true },
      paymentFecha: { label: 'La fecha', required: true, date: true },
      paymentReferencia: { label: 'La referencia', maxLength: 80 },
      paymentObservacion: { label: 'La observación', maxLength: 200 }
    },
    {
      onValid: async () => {
        setBusy(submitButton, true, 'Registrando…');
        try {
          await api.registrarPago({
            facturaId: options.facturaId,
            monto: Number(($<HTMLInputElement>('#paymentMonto', form)?.value || '0').replace(',', '.')),
            medioPago: ($<HTMLSelectElement>('#paymentMedio', form)?.value || 'TRANSFERENCIA').toUpperCase(),
            fecha: $<HTMLInputElement>('#paymentFecha', form)?.value || today(),
            referencia: ($<HTMLInputElement>('#paymentReferencia', form)?.value || '').trim() || undefined,
            observacion: ($<HTMLTextAreaElement>('#paymentObservacion', form)?.value || '').trim() || undefined
          });
          close();
          toast('Pago registrado correctamente.');
          await options.onSuccess?.();
        } catch (error) {
          if (error instanceof ApiError && error.details) {
            applyServerDetails(validation, error.details);
          }
          showFormAlert(form, errorMessage(error, 'No se pudo registrar el pago.'));
        } finally {
          setBusy(submitButton, false);
        }
      }
    }
  );
}
