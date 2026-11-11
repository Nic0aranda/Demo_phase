import type { Rol, Usuario } from './types.js';
import { CURRENCY_KEY } from './config.js';

const TOKEN_KEY = 'erp.token';
const USER_KEY = 'erp.user';
const LEGACY_ROLE_KEY = 'userRoleId';
const LEGACY_NAME_KEY = 'userName';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function getUser(): Usuario | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as Usuario;
  } catch {
    localStorage.removeItem(USER_KEY);
    return null;
  }
}

export function setSession(token: string, user: Usuario): void {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
  localStorage.removeItem(LEGACY_ROLE_KEY);
  localStorage.removeItem(LEGACY_NAME_KEY);
}

export function setUser(user: Usuario): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  localStorage.removeItem(LEGACY_ROLE_KEY);
  localStorage.removeItem(LEGACY_NAME_KEY);
}

export function isAuthenticated(): boolean {
  return Boolean(getToken());
}

export function fullName(user: Usuario): string {
  return `${user.nombres || ''} ${user.apellidos || ''}`.trim() || user.email;
}

export function roleDashboard(rol: Rol | string): string {
  const safeRole = ['admin', 'comercial', 'operaciones', 'contabilidad'].includes(String(rol))
    ? String(rol)
    : 'admin';
  return `views/${safeRole}/dashboard.html`;
}

export function getCurrency(): string {
  return localStorage.getItem(CURRENCY_KEY) || 'USD';
}

export function setCurrency(currency: string | undefined): void {
  if (currency) localStorage.setItem(CURRENCY_KEY, currency);
}
