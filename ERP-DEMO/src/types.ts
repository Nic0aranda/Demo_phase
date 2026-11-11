export type Rol = 'admin' | 'comercial' | 'operaciones' | 'contabilidad';
export type EstadoUsuario = 'activo' | 'inactivo';

export interface Usuario {
  email: string;
  nombres: string;
  apellidos: string;
  rol: Rol;
  estado: EstadoUsuario;
  permisos: string[];
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresIn: number;
  user: Usuario;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface Cliente {
  nombre: string;
  glosa?: string;
  tipoDoc: string;
  numDoc: string;
  pais: string;
  region: string;
  direccion: string;
  telefono?: string;
  email: string;
  activo: boolean;
}

export interface PresupuestoItem {
  descripcion: string;
  cantidad: number;
  precioUnitario: number;
  totalLinea: number;
}

export interface Presupuesto {
  id: string;
  rutCliente: string;
  fechaEmision: string;
  items: PresupuestoItem[];
  subtotal: number;
  impuestos: number;
  total: number;
  estado: string;
}

export interface DocumentoOperacion {
  tipo: string;
  numero: string;
  fecha?: string;
  archivo?: string;
  archivoToken?: string;
}

export interface Operacion {
  id: string;
  rutCliente: string;
  idPresupuestoOrigen?: string;
  tipoOperacion: string;
  viaTransporte?: string;
  referencia?: string;
  observaciones?: string;
  fechaInicio?: string;
  estado: string;
  responsableAsignado?: string;
  documentos: DocumentoOperacion[];
}

export interface FacturaItem {
  descripcion: string;
  cantidad: number;
  precioUnitario: number;
  totalLinea: number;
}

export interface Factura {
  id: string;
  rutCliente: string;
  idOperacionOrigen?: string;
  fechaEmision: string;
  fechaVencimiento: string;
  condicionPago: string;
  subtotal: number;
  impuestos: number;
  total: number;
  estado: string;
  items: FacturaItem[];
}

export interface Pago {
  id: string;
  facturaId: string;
  rutCliente?: string;
  cliente?: string;
  fecha: string;
  monto: number;
  medioPago: string;
  referencia?: string;
  observacion?: string;
  estado: string;
}

export interface NotaCredito {
  id: string;
  facturaId: string;
  rutCliente?: string;
  cliente?: string;
  tipo: string;
  montoNeto: number;
  montoTotal: number;
  motivo: string;
  fechaEmision: string;
  estado: string;
}

export interface CuentaPorCobrar {
  facturaId: string;
  rutCliente: string;
  cliente: string;
  fechaEmision: string;
  fechaVencimiento: string;
  total: number;
  pagado: number;
  creditos: number;
  montoPendiente: number;
  diasAtraso: number;
  estado: string;
}

export interface ResumenCartera {
  totalPorCobrar: number;
  totalVencido: number;
  totalPorVencer: number;
  facturasPendientes: number;
  facturasVencidas: number;
  facturasPagadas: number;
  moneda: string;
}

export interface Dashboard {
  facturadoMes: number;
  pendienteCobro: number;
  montoVencido: number;
  pagosMes: number;
  clientesActivos: number;
  operacionesActivas: number;
  presupuestosPendientes: number;
  facturasPendientes: number;
  facturasVencidas: number;
  facturasPagadas: number;
  moneda: string;
  ivaRate: number;
  cartera: ResumenCartera;
}

export interface MovimientoConciliacion {
  id: string;
  fecha: string;
  concepto: string;
  monto: number;
  documentoErp?: string;
  estado: string;
  origen?: string;
}

export interface ResumenConciliacion {
  saldoBanco: number;
  totalIngresos: number;
  totalEgresos: number;
  movimientosPendientes: number;
  movimientosConciliados: number;
  montoPendienteConciliar: number;
  moneda: string;
  movimientos: MovimientoConciliacion[];
}

export interface Recordatorio {
  id: string;
  facturaId: string;
  rutCliente?: string;
  cliente?: string;
  canal: string;
  asunto?: string;
  mensaje?: string;
  fechaEnvio?: string;
  estado: string;
}

export interface PromesaPago {
  id: string;
  facturaId: string;
  rutCliente?: string;
  cliente?: string;
  fechaPromesa: string;
  monto: number;
  nota?: string;
  fechaRegistro?: string;
  estado: string;
}

export interface ApiErrorBody {
  status: number;
  code: string;
  message: string;
  path?: string;
  requestId?: string;
  details?: Record<string, string>;
}

export interface ClientePayload {
  nombre: string;
  glosa?: string;
  tipoDoc: string;
  numDoc: string;
  pais: string;
  region: string;
  direccion: string;
  telefono?: string;
  email: string;
  activo: boolean;
}

export interface FacturaPayload {
  rutCliente: string;
  idOperacionOrigen?: string;
  fechaEmision?: string;
  condicionPago: string;
  items: Array<{
    descripcion: string;
    cantidad: number;
    precioUnitario: number;
  }>;
}

export interface OperacionPayload {
  rutCliente: string;
  idPresupuestoOrigen?: string;
  tipoOperacion: string;
  viaTransporte: string;
  referencia?: string;
  observaciones?: string;
  fechaInicio?: string;
}

export interface PresupuestoPayload {
  rutCliente: string;
  fechaEmision?: string;
  estado?: string;
  items: Array<{
    descripcion: string;
    cantidad: number;
    precioUnitario: number;
  }>;
}

export interface CambioPasswordPayload {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export interface RecalculoResultado {
  facturas: number;
  corregidas: number;
}

export interface UsuarioPayload {
  email: string;
  nombres: string;
  apellidos: string;
  rol: Rol;
  estado: EstadoUsuario;
  permisos: string[];
  password?: string;
  passwordConfirm?: string;
}
