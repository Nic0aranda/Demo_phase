import { getUser } from './storage.js';
import type { Rol } from './types.js';

/**
 * Roles que pueden ejecutar escrituras en cada módulo. Deben coincidir con las
 * reglas @PreAuthorize de la API para que la interfaz no ofrezca acciones que
 * el backend rechazará.
 */
export const WRITE_ROLES = {
  clientes: ['admin', 'comercial'],
  presupuestos: ['admin', 'comercial'],
  operaciones: ['admin', 'operaciones'],
  finanzas: ['admin', 'contabilidad'],
  usuarios: ['admin']
} as const satisfies Record<string, readonly Rol[]>;

export function hasAnyRole(
  roles: readonly Rol[],
  user = getUser()
): boolean {
  return Boolean(user && roles.includes(user.rol));
}

export function isAuditor(user = getUser()): boolean {
  return Boolean(user && user.rol === 'auditor');
}

export function canAudit(user = getUser()): boolean {
  return Boolean(user && (user.rol === 'auditor' || user.rol === 'admin'));
}

export function auditButton(entidad: string, clave: string | number | null | undefined): string {
  if (!canAudit()) return '';
  const keyStr = String(clave ?? '').trim();
  if (!keyStr) return '';
  const href = `../auditoria/auditoria.html?entidad=${encodeURIComponent(entidad)}&clave=${encodeURIComponent(keyStr)}`;
  return `<a class="btn-action btn-action-audit" href="${href}" title="Auditar historial en PhaseDB">Auditar</a>`;
}

