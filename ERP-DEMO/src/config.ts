const storedBase = localStorage.getItem('erp.apiBase');

const host = location.hostname || 'localhost';
const defaultHost = host === 'localhost' || host === '127.0.0.1' ? 'localhost' : host;

/**
 * URL base de la API. Se puede sobreescribir sin recompilar:
 *   localStorage.setItem('erp.apiBase', 'http://mi-servidor:8080/api/v1')
 * o definiendo window.ERP_API_BASE antes de cargar main.js.
 */
export const API_BASE =
  (window as Window & { ERP_API_BASE?: string }).ERP_API_BASE ||
  storedBase ||
  `http://${defaultHost}:8080/api/v1`;

export const CURRENCY_KEY = 'erp.currency';
