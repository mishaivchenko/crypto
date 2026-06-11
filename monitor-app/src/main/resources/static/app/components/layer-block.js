import { renderEnrichmentTimestamp } from './enrichment-timestamp.js';
import { renderLayerStatusBadge } from './layer-status-badge.js';

var _blockCounter = 0;

// Module-level collapsed store: key is "screen:layerType" -> boolean
const _store = {};

// Expose setter as a window global so inline onclick strings can call it
if (typeof window !== 'undefined') {
    window.__setLayerCollapsed = (key, val) => { _store[key] = val; };
}

export function getLayerCollapsed(screen, layerType, defaultVal) {
    const key = screen + ':' + layerType;
    return key in _store ? _store[key] : (defaultVal !== undefined ? defaultVal : false);
}

export function clearLayerCollapsed() {
    Object.keys(_store).forEach(k => delete _store[k]);
}

export function renderLayerBlock({ layerType, layerName, decoratorName, timestamp, source, status, content, collapsed, screen }) {
  if (collapsed === undefined) collapsed = false;
  const id = 'lb-' + (++_blockCounter);

  const layerKey = (screen && layerType) ? (screen + ':' + layerType) : null;
  const isCollapsed = layerKey && (layerKey in _store) ? _store[layerKey] : (collapsed === true);
  const bodyStyle = isCollapsed ? 'display:none' : '';

  const subheaderHtml = decoratorName
    ? '<div class="layer-block__subheader" style="margin-top:2px"><span class="layer-label" style="opacity:0.7;font-size:9px">' + _esc(decoratorName) + '</span></div>'
    : '';

  const headerOnclick = layerKey
    ? 'var b=this.closest(\'.layer-block\').querySelector(\'.layer-block__body\');var show=b.style.display===\'none\';b.style.display=show?\'\':\'none\';window.__setLayerCollapsed&&window.__setLayerCollapsed(\'' + layerKey + '\',!show);'
    : 'var b=this.closest(\'.layer-block\').querySelector(\'.layer-block__body\');b.style.display=b.style.display===\'\'?\'none\':\'\';';

  return '<div class="layer-block layer-block--' + _esc(layerType || 'base') + '" id="' + id + '">'
    + '<div class="layer-block__header" style="cursor:pointer;display:flex;align-items:center;gap:6px;" onclick="' + headerOnclick + '">'
    + '<span class="layer-label">' + _esc(layerName || layerType) + '</span>'
    + renderLayerStatusBadge(status)
    + renderEnrichmentTimestamp(timestamp, source)
    + '<span style="margin-left:auto;font-size:12px;color:#999">▾</span>'
    + '</div>'
    + subheaderHtml
    + '<div class="layer-block__body" style="' + bodyStyle + '">'
    + (content || '')
    + '</div>'
    + '</div>';
}

function _esc(s) {
  return String(s || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
