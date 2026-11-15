import { api } from './api.js';
import { $, $$, errorMessage, escapeHtml, fieldValue, money, setBusy, showFormAlert, statusBadge, toast } from './ui.js';
import type { AuditoriaHistorial, EstadoTemporal } from './types.js';

interface FlattenedNode {
  path: string;
  parentPath: string;
  leafKey: string;
  hasParent: boolean;
  value: unknown;
  rawSegments: (string | number)[];
}

interface FieldDiff {
  key: string;
  parentPath: string;
  leafKey: string;
  hasParent: boolean;
  oldVal: unknown;
  newVal: unknown;
  type: 'added' | 'removed' | 'modified';
}

function formatFechaFull(fecha: string | null): { dateStr: string; timeStr: string } {
  if (!fecha) return { dateStr: 'Fecha no disponible', timeStr: '—' };
  const date = new Date(fecha);
  if (Number.isNaN(date.getTime())) return { dateStr: 'Fecha no disponible', timeStr: '—' };

  const dateStr = new Intl.DateTimeFormat('es-CL', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(date);

  const timeStr = `${new Intl.DateTimeFormat('es-CL', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    timeZone: 'UTC',
  }).format(date)} UTC`;

  return { dateStr, timeStr };
}

function isPlainObject(val: unknown): val is Record<string, unknown> {
  return typeof val === 'object' && val !== null && !Array.isArray(val);
}

function formatSubscriptArg(seg: string | number): string {
  if (typeof seg === 'number') {
    return String(seg);
  }
  if (/^\d+$/.test(seg)) {
    return seg;
  }
  return `"${seg.replace(/"/g, '""')}"`;
}

function createFlattenedNode(segments: (string | number)[], value: unknown): FlattenedNode {
  if (segments.length === 0) {
    return {
      path: 'valor',
      parentPath: '',
      leafKey: 'valor',
      hasParent: false,
      value,
      rawSegments: segments
    };
  }
  if (segments.length === 1) {
    const key = String(segments[0]);
    return {
      path: key,
      parentPath: '',
      leafKey: key,
      hasParent: false,
      value,
      rawSegments: segments
    };
  }

  const rootName = String(segments[0]);
  const innerArgs = segments.slice(1, -1).map(formatSubscriptArg);
  const leaf = formatSubscriptArg(segments[segments.length - 1]);

  let parentPath = '';
  if (innerArgs.length > 0) {
    parentPath = `${rootName}(${innerArgs.join(', ')}, `;
  } else {
    parentPath = `${rootName}(`;
  }

  const fullPath = `${rootName}(${segments.slice(1).map(formatSubscriptArg).join(', ')})`;

  return {
    path: fullPath,
    parentPath,
    leafKey: leaf,
    hasParent: true,
    value,
    rawSegments: segments
  };
}

function renderSubscriptHtml(node: { parentPath?: string; leafKey: string; hasParent?: boolean }): string {
  if (!node.hasParent || !node.parentPath) {
    return `<span class="subscript-chain"><strong class="subscript-leaf">${escapeHtml(node.leafKey)}</strong></span>`;
  }
  return `<span class="subscript-chain"><span class="subscript-parent">${escapeHtml(node.parentPath)}</span><strong class="subscript-leaf">${escapeHtml(node.leafKey)}</strong><span class="subscript-parent">)</span></span>`;
}

function flattenObject(val: unknown, segments: (string | number)[] = []): Map<string, FlattenedNode> {
  const result = new Map<string, FlattenedNode>();

  function recurse(current: unknown, currentSegments: (string | number)[]) {
    if (currentSegments.length > 0 && (current === null || current === undefined || typeof current !== 'object')) {
      const node = createFlattenedNode(currentSegments, current);
      result.set(node.path, node);
      return;
    }

    if (Array.isArray(current)) {
      if (current.length === 0 && currentSegments.length > 0) {
        const node = createFlattenedNode(currentSegments, []);
        result.set(node.path, node);
        return;
      }
      current.forEach((item, index) => {
        recurse(item, [...currentSegments, index]);
      });
      return;
    }

    if (isPlainObject(current)) {
      const keys = Object.keys(current);
      if (keys.length === 0 && currentSegments.length > 0) {
        const node = createFlattenedNode(currentSegments, {});
        result.set(node.path, node);
        return;
      }
      for (const k of keys) {
        recurse((current as Record<string, unknown>)[k], [...currentSegments, k]);
      }
      return;
    }

    const node = createFlattenedNode(currentSegments, current);
    result.set(node.path, node);
  }

  recurse(val, segments);
  return result;
}

function computeDiff(currentVal: unknown, olderVal: unknown): FieldDiff[] {
  if ((currentVal === null || currentVal === undefined) && (olderVal === null || olderVal === undefined)) {
    return [];
  }

  if (olderVal === null || olderVal === undefined) {
    if (currentVal === null || currentVal === undefined) return [];
    const currentFlat = flattenObject(currentVal);
    const diffs: FieldDiff[] = [];
    currentFlat.forEach((node) => {
      diffs.push({
        key: node.path,
        parentPath: node.parentPath,
        leafKey: node.leafKey,
        hasParent: node.hasParent,
        oldVal: undefined,
        newVal: node.value,
        type: 'added'
      });
    });
    return diffs;
  }

  if (currentVal === null || currentVal === undefined) {
    const olderFlat = flattenObject(olderVal);
    const diffs: FieldDiff[] = [];
    olderFlat.forEach((node) => {
      diffs.push({
        key: node.path,
        parentPath: node.parentPath,
        leafKey: node.leafKey,
        hasParent: node.hasParent,
        oldVal: node.value,
        newVal: undefined,
        type: 'removed'
      });
    });
    return diffs;
  }

  const currentFlat = flattenObject(currentVal);
  const olderFlat = flattenObject(olderVal);

  const allKeys = Array.from(new Set([...currentFlat.keys(), ...olderFlat.keys()])).sort();
  const diffs: FieldDiff[] = [];

  for (const key of allKeys) {
    const oldNode = olderFlat.get(key);
    const newNode = currentFlat.get(key);

    const oldV = oldNode ? oldNode.value : undefined;
    const newV = newNode ? newNode.value : undefined;
    const refNode = newNode || oldNode!;

    if (!oldNode && newNode) {
      diffs.push({
        key,
        parentPath: refNode.parentPath,
        leafKey: refNode.leafKey,
        hasParent: refNode.hasParent,
        oldVal: undefined,
        newVal: newV,
        type: 'added'
      });
    } else if (oldNode && !newNode) {
      diffs.push({
        key,
        parentPath: refNode.parentPath,
        leafKey: refNode.leafKey,
        hasParent: refNode.hasParent,
        oldVal: oldV,
        newVal: undefined,
        type: 'removed'
      });
    } else if (JSON.stringify(oldV) !== JSON.stringify(newV)) {
      diffs.push({
        key,
        parentPath: refNode.parentPath,
        leafKey: refNode.leafKey,
        hasParent: refNode.hasParent,
        oldVal: oldV,
        newVal: newV,
        type: 'modified'
      });
    }
  }

  return diffs;
}

function formatValueForDisplay(key: string, val: unknown): string {
  if (val === undefined || val === null) return '<span class="text-muted">—</span>';
  if (typeof val === 'boolean') {
    return val
      ? '<span class="status-badge active">Sí</span>'
      : '<span class="status-badge inactive">No</span>';
  }
  if (typeof val === 'number') {
    const cleanKey = key.replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
    if (cleanKey.includes('total') || cleanKey.includes('monto') || cleanKey.includes('precio') || cleanKey.includes('subtotal') || cleanKey.includes('impuesto')) {
      return money(val);
    }
    return new Intl.NumberFormat('es-CL').format(val);
  }
  if (typeof val === 'string') {
    const cleanKey = key.replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
    if (cleanKey === 'estado') {
      return statusBadge(val);
    }
    return escapeHtml(val);
  }
  if (Array.isArray(val) && val.length === 0) {
    return '<span class="text-muted font-mono">[ ] (vacío)</span>';
  }
  if (isPlainObject(val) && Object.keys(val).length === 0) {
    return '<span class="text-muted font-mono">{ } (vacío)</span>';
  }
  return escapeHtml(JSON.stringify(val));
}

function formatSimpleVal(val: unknown): string {
  if (val === undefined) return '—';
  if (val === null) return 'null';
  if (typeof val === 'string') {
    return val.length > 25 ? `${val.slice(0, 23)}…` : val;
  }
  if (typeof val === 'number' || typeof val === 'boolean') {
    return String(val);
  }
  if (Array.isArray(val)) {
    return val.length === 0 ? '[]' : `[${val.length} elem]`;
  }
  if (isPlainObject(val)) {
    return Object.keys(val).length === 0 ? '{ }' : JSON.stringify(val);
  }
  return String(val);
}

function syntaxHighlightJson(json: string): string {
  const escaped = escapeHtml(json);
  return escaped.replace(
    /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)/g,
    (match) => {
      let cls = 'json-number';
      if (/^"/.test(match)) {
        if (/:$/.test(match)) {
          cls = 'json-key';
        } else {
          cls = 'json-string';
        }
      } else if (/true|false/.test(match)) {
        cls = 'json-boolean';
      } else if (/null/.test(match)) {
        cls = 'json-null';
      }
      return `<span class="${cls}">${match}</span>`;
    }
  );
}

// Stores all loaded versions across pagination batches for full-text filtering
let allVersionsState: Array<{
  transaccion: number;
  fecha: string | null;
  valor: unknown;
}> = [];

function renderVersiones(history: AuditoriaHistorial, append: boolean): void {
  const panel = $<HTMLElement>('#auditoriaHistorialPanel');
  const tbody = $<HTMLTableSectionElement>('#auditoriaHistorial');
  const metadata = $<HTMLElement>('#auditoriaMetadata');
  const badgesContainer = $<HTMLElement>('#auditoriaBadges');
  if (!panel || !tbody || !metadata) return;

  if (!append) {
    tbody.replaceChildren();
    allVersionsState = [...history.versiones];
  } else {
    allVersionsState.push(...history.versiones);
  }

  // Update Badges & Metadata in toolbar
  if (badgesContainer) {
    const actualVersions = allVersionsState.filter(v => v.transaccion > 0);
    const versionCountText = `${actualVersions.length} ${actualVersions.length === 1 ? 'versión' : 'versiones'}`;
    badgesContainer.innerHTML = `
      <span class="audit-chip audit-chip-entity">${escapeHtml(history.entidad)}</span>
      <span class="audit-chip audit-chip-key">${escapeHtml(history.clave)}</span>
      <span class="audit-chip audit-chip-count">${versionCountText}</span>
      <span class="audit-chip audit-chip-tx" title="Transacción actual en PhaseDB">$TX actual: ${escapeHtml(history.transaccionActual)}</span>
    `;
  }

  metadata.textContent = `Registro consultado en el global ^${history.entidad} con clave "${history.clave}". Mostrando las versiones históricas inmutables registradas por PhaseDB.`;

  // Render rows
  const startIndex = append ? allVersionsState.length - history.versiones.length : 0;

  history.versiones.forEach((version, localIndex) => {
    const globalIndex = startIndex + localIndex;
    const olderVersion = allVersionsState[globalIndex + 1];

    const isLatest = globalIndex === 0;
    const isNullTerminator = version.transaccion === 0;
    const hasData = version.valor !== null && version.valor !== undefined;
    const hadPriorData = allVersionsState.slice(globalIndex + 1).some(v => v.valor !== null && v.valor !== undefined && v.transaccion > 0);

    const isDeleted = !hasData && hadPriorData && !isNullTerminator;
    const isPreCreation = (!hasData && !hadPriorData) || isNullTerminator;
    const isInitial = hasData && (!olderVersion || olderVersion.valor === null || olderVersion.valor === undefined);

    const { dateStr, timeStr } = isNullTerminator
      ? { dateStr: 'Estado inicial', timeStr: 'Pre-creación' }
      : formatFechaFull(version.fecha);
    const diffs = olderVersion ? computeDiff(version.valor, olderVersion.valor) : [];

    // Main Row
    const tr = document.createElement('tr');
    tr.className = 'audit-row';
    tr.dataset.tx = String(version.transaccion);

    // Event & State Badge
    let eventBadgeHtml = '';
    if (isNullTerminator) {
      eventBadgeHtml = '<span class="status-badge inactive">Inicio del registro</span>';
    } else if (isPreCreation) {
      eventBadgeHtml = '<span class="status-badge inactive">No existía aún</span>';
    } else if (isDeleted) {
      eventBadgeHtml = '<span class="status-badge error">Eliminado / Borrado manual</span>';
    } else if (isInitial) {
      eventBadgeHtml = '<span class="status-badge active">Creación</span>';
    } else {
      eventBadgeHtml = '<span class="status-badge pending">Modificación</span>';
    }

    if (isPlainObject(version.valor)) {
      const rec = version.valor;
      if (typeof rec.estado === 'string') {
        eventBadgeHtml += ` <div style="margin-top:4px;">${statusBadge(rec.estado)}</div>`;
      } else if (rec.activo === 1) {
        eventBadgeHtml += ` <div style="margin-top:4px;"><span class="status-badge active">Activo</span></div>`;
      } else if (rec.activo === 0) {
        eventBadgeHtml += ` <div style="margin-top:4px;"><span class="status-badge inactive">Inactivo</span></div>`;
      }
    }

    // Summary / Diffs column
    let summaryHtml = '';
    if (isNullTerminator) {
      summaryHtml = '<span class="text-muted" style="font-style:italic;">Fin del historial</span>';
    } else if (isPreCreation) {
      summaryHtml = '<span class="text-muted" style="font-style:italic;">Transacción previa a la creación del registro (nodo sin datos en PhaseDB)</span>';
    } else if (isDeleted) {
      summaryHtml = '<span class="audit-deleted-note"><span class="badge-wiped">[Eliminado / borrado manualmente]</span> Registro vaciado o eliminado en PhaseDB</span>';
    } else if (diffs.length > 0) {
      const visibleDiffs = diffs.slice(0, 3);
      const remaining = diffs.length - visibleDiffs.length;
      summaryHtml = `
        <div class="diff-summary-container">
          ${visibleDiffs.map(d => {
            if (d.type === 'added') {
              return `
                <span class="diff-chip diff-chip-added" title="${escapeHtml(d.key)} (campo nuevo)">
                  <span class="diff-key">${renderSubscriptHtml(d)}</span>
                  <span class="diff-new">${escapeHtml(formatSimpleVal(d.newVal))}</span>
                  <span class="diff-tag-added">nuevo</span>
                </span>
              `;
            } else if (d.type === 'removed') {
              return `
                <span class="diff-chip diff-chip-removed" title="${escapeHtml(d.key)} (borrado)">
                  <span class="diff-key">${renderSubscriptHtml(d)}</span>
                  <span class="diff-old">${escapeHtml(formatSimpleVal(d.oldVal))}</span>
                  <span class="diff-arrow">→</span>
                  <span class="diff-new diff-new-wiped">[Eliminado / borrado manualmente]</span>
                </span>
              `;
            } else {
              return `
                <span class="diff-chip" title="${escapeHtml(d.key)}">
                  <span class="diff-key">${renderSubscriptHtml(d)}</span>
                  <span class="diff-old">${escapeHtml(formatSimpleVal(d.oldVal))}</span>
                  <span class="diff-arrow">→</span>
                  <span class="diff-new">${escapeHtml(formatSimpleVal(d.newVal))}</span>
                </span>
              `;
            }
          }).join('')}
          ${remaining > 0 ? `<span class="diff-more-badge">+${remaining} más</span>` : ''}
        </div>
      `;
    } else if (version.valor !== null && version.valor !== undefined && typeof version.valor === 'object') {
      // Initial creation highlights with flattened preview
      const flatMap = flattenObject(version.valor);
      const flatList = Array.from(flatMap.values()).slice(0, 4);
      const remaining = flatMap.size - flatList.length;
      summaryHtml = `
        <div class="initial-version-preview">
          ${flatList.map(node => `
            <span class="field-preview-chip">
              ${renderSubscriptHtml(node)}: <strong>${escapeHtml(formatSimpleVal(node.value))}</strong>
            </span>
          `).join('')}
          ${remaining > 0 ? `<span class="diff-more-badge">+${remaining} más</span>` : ''}
        </div>
      `;
    } else {
      summaryHtml = `<span class="text-muted">${escapeHtml(String(version.valor))}</span>`;
    }

    const txLabel = isNullTerminator ? 'Origen' : `Tx #${version.transaccion}`;
    const txBadgeClass = isNullTerminator ? 'tx-badge tx-badge-origin' : 'tx-badge';

    tr.innerHTML = `
      <td>
        <span class="${txBadgeClass}">${txLabel}</span>
        ${isLatest && !isNullTerminator ? '<br><span class="badge-latest">Más reciente</span>' : ''}
      </td>
      <td>
        <div class="audit-datetime">
          <span class="date-main">${escapeHtml(dateStr)}</span>
          <span class="time-sub">${escapeHtml(timeStr)}</span>
        </div>
      </td>
      <td>
        ${eventBadgeHtml}
      </td>
      <td>
        ${summaryHtml}
      </td>
      <td style="text-align: right;">
        <button type="button" class="btn-inspect" aria-expanded="false">
          <span>Ver detalle</span> <span class="chevron">▾</span>
        </button>
      </td>
    `;

    // Detail Drawer Subrow
    const detailTr = document.createElement('tr');
    detailTr.className = 'audit-detail-row';
    detailTr.dataset.detailTx = String(version.transaccion);
    detailTr.style.display = 'none';

    // Build fields view
    let fieldsViewHtml = '';
    if (version.valor !== null && version.valor !== undefined && typeof version.valor === 'object') {
      const flatMap = flattenObject(version.valor);
      const modifiedKeysSet = new Set(diffs.map(d => d.key));
      const entries = Array.from(flatMap.values());
      fieldsViewHtml = `
        <div class="audit-fields-grid">
          ${entries.map(node => `
            <div class="audit-field-card ${modifiedKeysSet.has(node.path) ? 'is-modified' : ''}" title="${escapeHtml(node.path)}">
              <div class="field-label">
                <span>${renderSubscriptHtml(node)}</span>
                ${modifiedKeysSet.has(node.path) ? '<span class="field-modified-tag">Modificado</span>' : ''}
              </div>
              <div class="field-value">${formatValueForDisplay(node.leafKey, node.value)}</div>
            </div>
          `).join('')}
        </div>
      `;
    } else if (isNullTerminator) {
      fieldsViewHtml = '<div class="audit-empty-diff"><p class="sub-text">Punto de terminación en null. El registro no existía en PhaseDB antes de su creación.</p></div>';
    } else if (isDeleted) {
      fieldsViewHtml = '<div class="audit-wiped-box"><strong>[Eliminado / borrado manualmente]</strong><p class="sub-text">El registro no contiene datos en esta transacción (fue eliminado, borrado manualmente o purgado en PhaseDB).</p></div>';
    } else if (isPreCreation) {
      fieldsViewHtml = '<div class="audit-empty-diff"><p class="sub-text">En esta transacción el registro aún no existía en PhaseDB.</p></div>';
    } else {
      fieldsViewHtml = `<div class="audit-field-card"><div class="field-value">${escapeHtml(String(version.valor))}</div></div>`;
    }

    // Build diffs view
    let diffViewHtml = '';
    if (isNullTerminator) {
      diffViewHtml = `
        <div class="audit-empty-diff">
          <p class="sub-text">Punto de partida del historial inmutable (cadena terminada en null). Representa el estado previo a la creación del registro.</p>
        </div>
      `;
    } else if (isPreCreation) {
      diffViewHtml = `
        <div class="audit-empty-diff">
          <p class="sub-text">Esta transacción es previa a la creación del registro (el valor no existía aún en PhaseDB).</p>
        </div>
      `;
    } else if (diffs.length > 0 && olderVersion) {
      const olderTxLabel = olderVersion.transaccion === 0 ? 'Origen / Pre-creación' : `Tx #${olderVersion.transaccion}`;
      diffViewHtml = `
        <table class="audit-diff-table">
          <thead>
            <tr>
              <th style="min-width: 240px; white-space: nowrap;">CAMPO</th>
              <th style="min-width: 200px;">VALOR ANTERIOR (${olderTxLabel})</th>
              <th style="min-width: 200px;">VALOR EN ESTA VERSIÓN (Tx #${version.transaccion})</th>
            </tr>
          </thead>
          <tbody>
            ${diffs.map(d => {
              let formattedOld = '';
              let formattedNew = '';
              let cellClassNew = 'diff-cell-new';

              if (d.type === 'added') {
                formattedOld = '<span class="text-muted font-mono" style="font-style:italic;">[No existía aún]</span>';
                formattedNew = escapeHtml(typeof d.newVal === 'object' ? JSON.stringify(d.newVal) : String(d.newVal));
              } else if (d.type === 'removed') {
                formattedOld = escapeHtml(typeof d.oldVal === 'object' ? JSON.stringify(d.oldVal) : String(d.oldVal));
                formattedNew = '<span class="badge-wiped">[Eliminado / borrado manualmente]</span>';
                cellClassNew += ' diff-cell-wiped';
              } else {
                formattedOld = escapeHtml(typeof d.oldVal === 'object' ? JSON.stringify(d.oldVal) : String(d.oldVal));
                formattedNew = escapeHtml(typeof d.newVal === 'object' ? JSON.stringify(d.newVal) : String(d.newVal));
              }

              return `
              <tr>
                <td class="diff-cell-key">${renderSubscriptHtml(d)}</td>
                <td class="diff-cell-old">${formattedOld}</td>
                <td class="${cellClassNew}">${formattedNew}</td>
              </tr>
            `;
            }).join('')}
          </tbody>
        </table>
      `;
    } else {
      diffViewHtml = `
        <div class="audit-empty-diff">
          <p class="sub-text">${olderVersion ? `No hay diferencias de valores respecto a la versión anterior (Tx #${olderVersion.transaccion}).` : 'Esta es la versión inicial del registro (sin versión previa registrada).'}</p>
        </div>
      `;
    }

    // Build JSON raw view
    const rawJson = version.valor === null || version.valor === undefined
      ? 'null'
      : JSON.stringify(version.valor, null, 2) ?? String(version.valor);

    const flatNodeCount = (version.valor !== null && version.valor !== undefined && typeof version.valor === 'object')
      ? flattenObject(version.valor).size
      : 0;

    detailTr.innerHTML = `
      <td colspan="5">
        <div class="audit-detail-content">
          <div class="audit-detail-topbar">
            <div class="audit-tabs">
              <button type="button" class="audit-tab-btn active" data-tab="fields">Vista de campos</button>
              <button type="button" class="audit-tab-btn" data-tab="diff" title="${diffs.length === 0 ? 'Sin diferencias detectadas respecto a la versión anterior' : 'Ver comparativa de cambios'}">
                Comparativa de cambios (${diffs.length})
              </button>
              <button type="button" class="audit-tab-btn" data-tab="json">JSON PhaseDB</button>
            </div>
            <div class="audit-meta-summary">
              <span>Timestamp ISO: <code>${escapeHtml(version.fecha || '—')}</code></span>
              ${flatNodeCount > 0 ? `<span>• ${flatNodeCount} nodos PhaseDB</span>` : ''}
            </div>
          </div>

          <div class="audit-tab-pane tab-fields">
            ${fieldsViewHtml}
          </div>

          <div class="audit-tab-pane tab-diff" style="display: none; overflow-x: auto;">
            ${diffViewHtml}
          </div>

          <div class="audit-tab-pane tab-json" style="display: none;">
            <div class="audit-json-wrapper">
              <button type="button" class="btn-copy-json" data-tx="${version.transaccion}">Copiar JSON</button>
              <pre class="audit-json-pre"><code>${syntaxHighlightJson(rawJson)}</code></pre>
            </div>
          </div>
        </div>
      </td>
    `;

    // Interactivity: Click to toggle detail
    const toggleDetail = () => {
      const isCurrentlyExpanded = detailTr.style.display !== 'none';
      const inspectBtn = tr.querySelector<HTMLButtonElement>('.btn-inspect');
      if (isCurrentlyExpanded) {
        detailTr.style.display = 'none';
        tr.classList.remove('is-expanded');
        if (inspectBtn) inspectBtn.setAttribute('aria-expanded', 'false');
      } else {
        detailTr.style.display = 'table-row';
        tr.classList.add('is-expanded');
        if (inspectBtn) inspectBtn.setAttribute('aria-expanded', 'true');
      }
      updateToggleAllButtonText();
    };

    tr.addEventListener('click', (e) => {
      // Ignore if clicking on button directly (handled by inspect button listener)
      const target = e.target as HTMLElement;
      if (target.closest('.btn-inspect')) {
        toggleDetail();
      } else {
        toggleDetail();
      }
    });

    // Tab switcher in detail drawer
    detailTr.querySelectorAll<HTMLButtonElement>('.audit-tab-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const tab = btn.dataset.tab;
        if (!tab) return;
        detailTr.querySelectorAll('.audit-tab-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');

        const panes = detailTr.querySelectorAll<HTMLElement>('.audit-tab-pane');
        panes.forEach(pane => {
          pane.style.display = pane.classList.contains(`tab-${tab}`) ? 'block' : 'none';
        });
      });
    });

    // Copy JSON button
    const copyBtn = detailTr.querySelector<HTMLButtonElement>('.btn-copy-json');
    if (copyBtn) {
      copyBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        void navigator.clipboard.writeText(rawJson).then(() => {
          toast(`JSON de la Transacción #${version.transaccion} copiado al portapapeles.`, 'info');
        }).catch(() => {
          toast('No se pudo copiar el JSON automáticamente.', 'error');
        });
      });
    }

    tbody.append(tr, detailTr);
  });

  if (!append && history.versiones.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="5" class="table-message table-empty" style="padding: 40px 20px; text-align: center;">
          <strong style="display:block; font-size:15px; margin-bottom: 4px; color: var(--text-main);">Sin historial de cambios</strong>
          <p class="sub-text">No se encontraron versiones para la clave "${escapeHtml(history.clave)}" en el global ^${escapeHtml(history.entidad)}.</p>
        </td>
      </tr>
    `;
  }

  panel.hidden = false;
  updateToggleAllButtonText();
}

function updateToggleAllButtonText(): void {
  const toggleBtn = $<HTMLButtonElement>('#toggleAllSnapshotsBtn');
  if (!toggleBtn) return;
  const detailRows = $$<HTMLElement>('.audit-detail-row');
  if (detailRows.length === 0) return;

  const anyExpanded = detailRows.some(row => row.style.display !== 'none');
  const btnText = toggleBtn.querySelector('.btn-text') || toggleBtn;
  btnText.textContent = anyExpanded ? 'Colapsar todos' : 'Expandir todos';
}

function initSearchFilter(): void {
  const searchInput = $<HTMLInputElement>('#auditSearchInput');
  if (!searchInput) return;

  searchInput.addEventListener('input', () => {
    const query = searchInput.value.trim().toLowerCase();
    const rows = $$<HTMLTableRowElement>('.audit-row');

    let visibleCount = 0;
    rows.forEach(row => {
      const tx = row.dataset.tx || '';
      const text = row.innerText.toLowerCase();
      const detailRow = $<HTMLTableRowElement>(`.audit-detail-row[data-detail-tx="${tx}"]`);

      const matches = !query || tx.includes(query) || text.includes(query);
      row.style.display = matches ? 'table-row' : 'none';
      if (!matches && detailRow) {
        detailRow.style.display = 'none';
        row.classList.remove('is-expanded');
      }
      if (matches) visibleCount++;
    });

    const tbody = $<HTMLTableSectionElement>('#auditoriaHistorial');
    const existingNoMatch = $<HTMLTableRowElement>('#auditNoMatchRow');
    if (visibleCount === 0 && rows.length > 0) {
      if (!existingNoMatch && tbody) {
        const noMatchTr = document.createElement('tr');
        noMatchTr.id = 'auditNoMatchRow';
        noMatchTr.innerHTML = `
          <td colspan="5" class="table-message table-empty" style="padding: 30px; text-align: center;">
            No se encontraron versiones que coincidan con "${escapeHtml(query)}".
          </td>
        `;
        tbody.append(noMatchTr);
      }
    } else if (existingNoMatch) {
      existingNoMatch.remove();
    }
  });
}

function initToggleAllSnapshots(): void {
  const toggleBtn = $<HTMLButtonElement>('#toggleAllSnapshotsBtn');
  if (!toggleBtn) return;

  toggleBtn.addEventListener('click', () => {
    const detailRows = $$<HTMLTableRowElement>('.audit-detail-row');
    const anyExpanded = detailRows.some(row => row.style.display !== 'none');

    detailRows.forEach(detailRow => {
      const tx = detailRow.dataset.detailTx;
      const mainRow = $<HTMLTableRowElement>(`.audit-row[data-tx="${tx}"]`);
      const inspectBtn = mainRow?.querySelector<HTMLButtonElement>('.btn-inspect');

      if (anyExpanded) {
        detailRow.style.display = 'none';
        mainRow?.classList.remove('is-expanded');
        if (inspectBtn) inspectBtn.setAttribute('aria-expanded', 'false');
      } else {
        detailRow.style.display = 'table-row';
        mainRow?.classList.add('is-expanded');
        if (inspectBtn) inspectBtn.setAttribute('aria-expanded', 'true');
      }
    });

    updateToggleAllButtonText();
  });
}

const ENTITY_PLACEHOLDERS: Record<string, string> = {
  CLIENTES: 'Ej. RUT del cliente (76123456-7)',
  FACTURAS: 'Ej. ID de factura (FAC-2026-0001)',
  PAGOS: 'Ej. ID de pago (PAG-2026-0001)',
  NOTASCREDITO: 'Ej. ID de nota de crédito (NC-2026-0001)',
  PRESUPUESTOS: 'Ej. ID de presupuesto (PRE-2026-0001)',
  OPERACIONES: 'Ej. ID de operación (OP-2026-0001)',
  CONCILIACION: 'Ej. ID de movimiento bancario (MOV-2026-0001)',
  COBRANZA: 'Ej. ID de cobranza o recordatorio (REC-2026-0001)',
  PROMESAS: 'Ej. ID de promesa de pago (PROM-2026-0001)',
};

export async function initAuditoriaPage(): Promise<void> {
  const form = $<HTMLFormElement>('#auditoriaForm');
  if (!form) return;
  const auditForm = form;

  const currentTransaction = $('#transaccionActual');
  try {
    const state: EstadoTemporal = await api.transaccionAuditoria();
    if (currentTransaction) currentTransaction.textContent = state.transaccionActual;
  } catch (error) {
    toast(errorMessage(error, 'No se pudo consultar la transacción actual.'), 'error');
  }

  const entidadSelect = $<HTMLSelectElement>('#auditoriaEntidad');
  const claveInput = $<HTMLInputElement>('#auditoriaClave');

  // Support URL params for pre-selecting entity, key, and auto-recalling history
  const urlParams = new URLSearchParams(window.location.search);
  const entidadParam = urlParams.get('entidad');
  const claveParam = urlParams.get('clave');

  if (entidadSelect && entidadParam) {
    const rawUpper = entidadParam.trim().toUpperCase();
    const aliasMap: Record<string, string> = {
      NOTAS_CREDITO: 'NOTASCREDITO',
      RECORDATORIOS: 'COBRANZA',
    };
    const targetVal = aliasMap[rawUpper] || rawUpper;
    const optionExists = Array.from(entidadSelect.options).some(opt => opt.value === targetVal);
    if (optionExists) {
      entidadSelect.value = targetVal;
    }
  }

  if (claveInput && claveParam) {
    claveInput.value = claveParam.trim();
  }

  if (entidadSelect && claveInput) {
    const selected = entidadSelect.value;
    claveInput.placeholder = ENTITY_PLACEHOLDERS[selected] || 'Ej. Clave del registro';

    entidadSelect.addEventListener('change', () => {
      const currentSelected = entidadSelect.value;
      claveInput.placeholder = ENTITY_PLACEHOLDERS[currentSelected] || 'Ej. Clave del registro';
    });
  }

  const submitButton = $<HTMLButtonElement>('#auditoriaConsultar');
  const historyButton = $<HTMLButtonElement>('#auditoriaHistorialBtn');
  const historyPanel = $<HTMLElement>('#auditoriaHistorialPanel');
  let historyCursor: number | undefined;

  const resetHistory = () => {
    historyCursor = undefined;
    historyPanel?.setAttribute('hidden', '');
    historyButton?.setAttribute('hidden', '');
    $('#auditoriaHistorial')?.replaceChildren();
    allVersionsState = [];
    const searchInput = $<HTMLInputElement>('#auditSearchInput');
    if (searchInput) searchInput.value = '';
  };

  auditForm.addEventListener('change', resetHistory);
  claveInput?.addEventListener('input', resetHistory);

  initToggleAllSnapshots();
  initSearchFilter();

  async function loadHistory(before?: number, append = false): Promise<void> {
    const clave = fieldValue('auditoriaClave');
    if (!clave) {
      showFormAlert(auditForm, 'Ingresa la clave del registro cuyo historial deseas revisar.');
      return;
    }

    const button = append ? historyButton : submitButton;
    setBusy(button, true, append ? 'Consultando…' : 'Consultando historial…');
    try {
      const history = await api.auditoriaHistorial(
        fieldValue('auditoriaEntidad'),
        clave,
        before
      );
      const alert = auditForm.querySelector<HTMLElement>('[data-form-alert]');
      if (alert) {
        alert.hidden = true;
        alert.textContent = '';
      }
      renderVersiones(history, append);
      historyCursor = history.siguienteAntesDe ?? undefined;
      if (historyButton) historyButton.hidden = historyCursor === undefined;
    } catch (error) {
      showFormAlert(auditForm, errorMessage(error, 'No se pudo consultar el historial del registro.'));
    } finally {
      setBusy(button, false);
    }
  }

  auditForm.addEventListener('submit', (event) => {
    event.preventDefault();
    historyCursor = undefined;
    void loadHistory();
  });

  historyButton?.addEventListener('click', () => {
    if (historyCursor !== undefined) void loadHistory(historyCursor, true);
  });

  if (claveParam && claveParam.trim()) {
    void loadHistory();
  }
}
