import { API_BASE } from './config.js';
import { clearSession, getToken } from './storage.js';
import type {
  ApiErrorBody,
  CambioPasswordPayload,
  Cliente,
  ClientePayload,
  CuentaPorCobrar,
  Dashboard,
  Factura,
  FacturaPayload,
  LoginResponse,
  MovimientoConciliacion,
  NotaCredito,
  Operacion,
  OperacionPayload,
  PageResponse,
  Pago,
  Presupuesto,
  PresupuestoPayload,
  PromesaPago,
  RecalculoResultado,
  Recordatorio,
  ResumenCartera,
  ResumenConciliacion,
  Usuario,
  UsuarioPayload
} from './types.js';

interface RequestOptions extends RequestInit {
  auth?: boolean;
  redirectOn401?: boolean;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly requestId?: string;
  readonly details?: Record<string, string>;
  readonly retryAfter?: number;

  constructor(
    status: number,
    code: string,
    message: string,
    options: { requestId?: string; details?: Record<string, string>; retryAfter?: number } = {}
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.requestId = options.requestId;
    this.details = options.details;
    this.retryAfter = options.retryAfter;
  }
}

function isLoginPage(): boolean {
  return document.body?.dataset.page === 'login';
}

function redirectToLogin(): void {
  const path = location.pathname.replace(/\\/g, '/');
  const prefix = path.includes('/views/') ? '../../' : '';
  location.replace(`${prefix}Index.html`);
}

function newKey(prefix: string): string {
  const cryptoApi = globalThis.crypto;
  const uuid =
    cryptoApi && typeof cryptoApi.randomUUID === 'function'
      ? cryptoApi.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${uuid}`;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { auth = true, redirectOn401 = true, ...init } = options;
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');

  if (init.body && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  if (auth) {
    const token = getToken();
    if (token) headers.set('Authorization', `Bearer ${token}`);
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, { ...init, headers });
  } catch {
    throw new ApiError(
      0,
      'SIN_CONEXION',
      `No se pudo conectar con la API en ${API_BASE}. Verifica que el backend este iniciado.`
    );
  }

  const contentType = response.headers.get('Content-Type') || '';
  let payload: unknown = null;
  if (contentType.includes('application/json')) {
    try {
      payload = await response.json();
    } catch {
      payload = null;
    }
  }

  if (!response.ok) {
    const body = (payload || {}) as Partial<ApiErrorBody>;
    const retryHeader = response.headers.get('Retry-After');
    const error = new ApiError(
      response.status,
      body.code || 'ERROR_HTTP',
      body.message || `Error HTTP ${response.status}`,
      {
        requestId: body.requestId || response.headers.get('X-Request-Id') || undefined,
        details: body.details,
        retryAfter: retryHeader ? Number(retryHeader) : undefined
      }
    );

    if (response.status === 401 && auth && redirectOn401 && !isLoginPage()) {
      clearSession();
      redirectToLogin();
    }
    throw error;
  }

  if (response.status === 204) return undefined as T;
  if (payload !== null) return payload as T;

  const text = await response.text();
  return (text ? text : undefined) as T;
}

function query(params: Record<string, string | number | boolean | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') search.set(key, String(value));
  }
  const serialized = search.toString();
  return serialized ? `?${serialized}` : '';
}

export const api = {
  login(email: string, password: string): Promise<LoginResponse> {
    return request<LoginResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
      auth: false,
      redirectOn401: false
    });
  },

  me(): Promise<Usuario> {
    return request<Usuario>('/auth/me');
  },

  cambiarPassword(payload: CambioPasswordPayload): Promise<void> {
    return request<void>('/auth/change-password', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  dashboard(): Promise<Dashboard> {
    return request<Dashboard>('/dashboard');
  },

  clientes(q = '', page = 0, size = 100, activo?: boolean): Promise<PageResponse<Cliente>> {
    return request<PageResponse<Cliente>>(
      `/clientes${query({ q, page, size, activo })}`
    );
  },

  cliente(numDoc: string): Promise<Cliente> {
    return request<Cliente>(`/clientes/${encodeURIComponent(numDoc)}`);
  },

  crearCliente(payload: ClientePayload): Promise<Cliente> {
    return request<Cliente>('/clientes', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  actualizarCliente(numDoc: string, payload: ClientePayload): Promise<Cliente> {
    return request<Cliente>(`/clientes/${encodeURIComponent(numDoc)}`, {
      method: 'PUT',
      body: JSON.stringify(payload)
    });
  },

  cambiarEstadoCliente(numDoc: string, activo: boolean): Promise<Cliente> {
    return request<Cliente>(`/clientes/${encodeURIComponent(numDoc)}/estado`, {
      method: 'PATCH',
      body: JSON.stringify({ activo })
    });
  },

  presupuestos(): Promise<Presupuesto[]> {
    return request<Presupuesto[]>('/presupuestos');
  },

  presupuesto(id: string): Promise<Presupuesto> {
    return request<Presupuesto>(`/presupuestos/${encodeURIComponent(id)}`);
  },

  crearPresupuesto(payload: PresupuestoPayload): Promise<Presupuesto> {
    return request<Presupuesto>('/presupuestos', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  actualizarPresupuesto(id: string, payload: PresupuestoPayload): Promise<Presupuesto> {
    return request<Presupuesto>(`/presupuestos/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(payload)
    });
  },

  cambiarEstadoPresupuesto(id: string, estado: string): Promise<void> {
    return request<void>(`/presupuestos/${encodeURIComponent(id)}/estado`, {
      method: 'PATCH',
      body: JSON.stringify({ estado })
    });
  },

  operaciones(): Promise<Operacion[]> {
    return request<Operacion[]>('/operaciones');
  },

  crearOperacion(payload: OperacionPayload): Promise<Operacion> {
    return request<Operacion>('/operaciones', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  cambiarEstadoOperacion(id: string, estado: string): Promise<void> {
    return request<void>(`/operaciones/${encodeURIComponent(id)}/estado`, {
      method: 'PATCH',
      body: JSON.stringify({ estado })
    });
  },

  agregarDocumento(
    id: string,
    documento: { tipo: string; numero: string; fecha?: string; archivo?: string }
  ): Promise<Operacion> {
    return request<Operacion>(`/operaciones/${encodeURIComponent(id)}/documentos`, {
      method: 'POST',
      body: JSON.stringify(documento)
    });
  },

  subirDocumento(
    id: string,
    documento: { tipo: string; numero: string; fecha?: string; archivo: File }
  ): Promise<Operacion> {
    const form = new FormData();
    form.set('tipo', documento.tipo);
    form.set('numero', documento.numero);
    if (documento.fecha) form.set('fecha', documento.fecha);
    form.set('archivo', documento.archivo, documento.archivo.name);
    return request<Operacion>(`/operaciones/${encodeURIComponent(id)}/documentos/archivo`, {
      method: 'POST',
      body: form
    });
  },

  async descargarDocumento(
    id: string,
    documento: { archivoToken: string; archivo?: string }
  ): Promise<void> {
    const headers = new Headers();
    const token = getToken();
    if (token) headers.set('Authorization', `Bearer ${token}`);

    const response = await fetch(
      `${API_BASE}/operaciones/${encodeURIComponent(id)}/documentos/${encodeURIComponent(
        documento.archivoToken
      )}/archivo`,
      { headers }
    );
    if (!response.ok) {
      if (response.status === 401) {
        clearSession();
        redirectToLogin();
      }
      throw new ApiError(response.status, 'ARCHIVO_ERROR', 'No se pudo descargar el archivo del documento');
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = documento.archivo || 'documento';
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  },

  facturas(): Promise<Factura[]> {
    return request<Factura[]>('/facturas');
  },

  factura(id: string): Promise<Factura> {
    return request<Factura>(`/facturas/${encodeURIComponent(id)}`);
  },

  crearFactura(payload: FacturaPayload): Promise<Factura> {
    return request<Factura>('/facturas', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  cambiarEstadoFactura(id: string, estado: string): Promise<void> {
    return request<void>(`/facturas/${encodeURIComponent(id)}/estado`, {
      method: 'PATCH',
      body: JSON.stringify({ estado })
    });
  },

  enviarFactura(
    id: string,
    payload: { destinatario?: string; asunto?: string; mensaje?: string } = {}
  ): Promise<unknown> {
    return request(`/facturas/${encodeURIComponent(id)}/enviar`, {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  async descargarFacturaPdf(id: string): Promise<void> {
    const headers = new Headers();
    const token = getToken();
    if (token) headers.set('Authorization', `Bearer ${token}`);

    const response = await fetch(`${API_BASE}/facturas/${encodeURIComponent(id)}/pdf`, {
      headers
    });
    if (!response.ok) {
      if (response.status === 401) {
        clearSession();
        redirectToLogin();
      }
      throw new ApiError(response.status, 'PDF_ERROR', 'No se pudo descargar el PDF de la factura');
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `${id}.pdf`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  },

  pagos(facturaId?: string): Promise<Pago[]> {
    return request<Pago[]>(`/pagos${query({ facturaId })}`);
  },

  registrarPago(payload: Partial<Pago>): Promise<Pago> {
    return request<Pago>('/pagos', {
      method: 'POST',
      headers: { 'Idempotency-Key': newKey('pago') },
      body: JSON.stringify(payload)
    });
  },

  cartera(): Promise<CuentaPorCobrar[]> {
    return request<CuentaPorCobrar[]>('/cuentas-por-cobrar');
  },

  resumenCartera(): Promise<ResumenCartera> {
    return request<ResumenCartera>('/cuentas-por-cobrar/resumen');
  },

  notas(facturaId?: string): Promise<NotaCredito[]> {
    return request<NotaCredito[]>(`/notas-credito${query({ facturaId })}`);
  },

  crearNota(payload: Partial<NotaCredito>): Promise<NotaCredito> {
    return request<NotaCredito>('/notas-credito', {
      method: 'POST',
      headers: { 'Idempotency-Key': newKey('nota') },
      body: JSON.stringify(payload)
    });
  },

  anularNota(id: string): Promise<NotaCredito> {
    return request<NotaCredito>(`/notas-credito/${encodeURIComponent(id)}/anular`, {
      method: 'POST',
      headers: { 'Idempotency-Key': newKey('nota-anular') }
    });
  },

  usuarios(q = '', page = 0, size = 100): Promise<PageResponse<Usuario>> {
    return request<PageResponse<Usuario>>(`/usuarios${query({ q, page, size })}`);
  },

  usuario(email: string): Promise<Usuario> {
    return request<Usuario>(`/usuarios/${encodeURIComponent(email)}`);
  },

  crearUsuario(payload: UsuarioPayload): Promise<Usuario> {
    return request<Usuario>('/usuarios', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  actualizarUsuario(email: string, payload: UsuarioPayload): Promise<Usuario> {
    return request<Usuario>(`/usuarios/${encodeURIComponent(email)}`, {
      method: 'PUT',
      body: JSON.stringify(payload)
    });
  },

  cambiarEstadoUsuario(email: string, estado: string): Promise<Usuario> {
    return request<Usuario>(`/usuarios/${encodeURIComponent(email)}/estado`, {
      method: 'PATCH',
      body: JSON.stringify({ estado })
    });
  },

  cambiarPermisos(email: string, permisos: string[]): Promise<Usuario> {
    return request<Usuario>(`/usuarios/${encodeURIComponent(email)}/permisos`, {
      method: 'PATCH',
      body: JSON.stringify({ permisos })
    });
  },

  recalcular(): Promise<RecalculoResultado> {
    return request<RecalculoResultado>('/admin/recalcular', { method: 'POST' });
  },

  conciliacion(): Promise<ResumenConciliacion> {
    return request<ResumenConciliacion>('/conciliacion');
  },

  simularConciliacion(): Promise<ResumenConciliacion> {
    return request<ResumenConciliacion>('/conciliacion/simular', { method: 'POST' });
  },

  ejecutarConciliacion(): Promise<ResumenConciliacion> {
    return request<ResumenConciliacion>('/conciliacion/ejecutar', { method: 'POST' });
  },

  conciliarMovimiento(id: string, documentoErp: string): Promise<MovimientoConciliacion> {
    return request<MovimientoConciliacion>(
      `/conciliacion/movimientos/${encodeURIComponent(id)}/conciliar`,
      {
        method: 'POST',
        body: JSON.stringify({ documentoErp })
      }
    );
  },

  recordatorios(facturaId?: string): Promise<Recordatorio[]> {
    return request<Recordatorio[]>(`/cobranza/recordatorios${query({ facturaId })}`);
  },

  enviarRecordatorio(payload: Partial<Recordatorio>): Promise<Recordatorio> {
    return request<Recordatorio>('/cobranza/recordatorios', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  promesas(facturaId?: string): Promise<PromesaPago[]> {
    return request<PromesaPago[]>(`/cobranza/promesas${query({ facturaId })}`);
  },

  registrarPromesa(payload: Partial<PromesaPago>): Promise<PromesaPago> {
    return request<PromesaPago>('/cobranza/promesas', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  actualizarEstadoPromesa(id: string, estado: string): Promise<PromesaPago> {
    return request<PromesaPago>(`/cobranza/promesas/${encodeURIComponent(id)}/estado`, {
      method: 'PATCH',
      body: JSON.stringify({ estado })
    });
  }
};
