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
