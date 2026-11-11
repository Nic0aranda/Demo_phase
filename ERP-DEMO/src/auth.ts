import { api, ApiError } from './api.js';
import { clearSession, getToken, roleDashboard, setSession, setUser } from './storage.js';
import { $, setBusy } from './ui.js';
import { initValidation } from './validation.js';

function showLoginError(message: string): void {
  const box = $<HTMLElement>('#errorMessage');
  if (!box) return;
  box.textContent = message;
  box.style.display = 'block';
}

function clearLoginError(): void {
  const box = $<HTMLElement>('#errorMessage');
  if (!box) return;
  box.textContent = '';
  box.style.display = 'none';
}

function loginErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'LOGIN_BLOQUEADO' || error.status === 429) {
      const minutes = error.retryAfter ? Math.max(1, Math.ceil(error.retryAfter / 60)) : 15;
      return `Demasiados intentos fallidos. La cuenta está bloqueada temporalmente; intenta nuevamente en ${minutes} minuto${minutes === 1 ? '' : 's'}.`;
    }
    if (error.status === 401) {
      return 'Correo o contraseña incorrectos.';
    }
    if (error.code === 'SIN_CONEXION') {
      return error.message;
    }
    return error.message || 'No se pudo iniciar sesión.';
  }
  return 'Ocurrió un error inesperado al iniciar sesión.';
}

export async function initLogin(): Promise<void> {
  const form = $<HTMLFormElement>('#loginForm');
  if (!form) return;

  const submitButton = $<HTMLButtonElement>('#loginBtn');

  // Si ya hay una sesión válida, no obligamos al usuario a autenticarse otra vez.
  if (getToken()) {
    try {
      const user = await api.me();
      setUser(user);
      location.replace(roleDashboard(user.rol));
      return;
    } catch {
      clearSession();
    }
  }

  initValidation(
    form,
    {
      email: {
        label: 'El correo electrónico',
        required: true,
        email: true,
        maxLength: 120
      },
      password: {
        label: 'La contraseña',
        required: true,
        maxLength: 72
      }
    },
    {
      onValid: async () => {
        clearLoginError();
        setBusy(submitButton, true, 'Ingresando…');
        try {
          const email = ($<HTMLInputElement>('#email')?.value || '').trim().toLowerCase();
          const password = $<HTMLInputElement>('#password')?.value || '';
          const response = await api.login(email, password);
          setSession(response.token, response.user);
          location.href = roleDashboard(response.user.rol);
        } catch (error) {
          showLoginError(loginErrorMessage(error));
        } finally {
          setBusy(submitButton, false);
        }
      }
    }
  );

  ['email', 'password'].forEach((field) => {
    $(`#${field}`)?.addEventListener('input', clearLoginError);
  });
}
