import { renderEnrichmentTimestamp } from './enrichment-timestamp.js';
import { renderLayerStatusBadge } from './layer-status-badge.js';

var _blockCounter = 0;

export function renderLayerBlock({ layerType, layerName, decoratorName, timestamp, source, status, content, collapsed }) {
  if (collapsed === undefined) collapsed = false;
  const id = 'lb-' + (++_blockCounter);
  const bodyStyle = collapsed ? 'display:none' : '';

  const subheaderHtml = decoratorName
    ? '<div class="layer-block__subheader" style="margin-top:2px"><span class="layer-label" style="opacity:0.7;font-size:9px">' + _esc(decoratorName) + '</span></div>'
    : '';

  const headerOnclick = "var b=this.closest('.layer-block').querySelector('.layer-block__body');b.style.display=b.style.display===''?'none':'';";

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
