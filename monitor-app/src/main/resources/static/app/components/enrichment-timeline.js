const _STATUS_ICON  = { ok:'●', warn:'▲', blocked:'✕', missing:'?' };
const _STATUS_COLOR = { ok:'#38a169', warn:'#d69e2e', blocked:'#e53e3e', missing:'#999' };

var _tlCounter = 0;

export function renderEnrichmentTimeline(layers) {
  if (!layers || layers.length === 0) {
    return '<div class="enrichment-timeline enrichment-timeline--empty" style="color:#999;font-style:italic;padding:8px">История обогащения пуста</div>';
  }

  var rows = layers.map(function(layer, idx) {
    var id = 'etl-' + (++_tlCounter);
    var icon  = _STATUS_ICON[layer.status]  || '?';
    var color = _STATUS_COLOR[layer.status] || '#999';
    var tsFormatted = layer.timestamp ? _fmtTs(layer.timestamp) : '—';
    var isLast = idx === layers.length - 1;
    var connector = isLast ? '' : '<div style="width:1px;height:14px;background:#ddd;margin-left:8px"></div>';
    var decoratorHtml = layer.decorator
      ? '<span class="layer-label" style="margin:0 4px">' + _esc(layer.decorator) + '</span>'
      : '';

    return '<div>'
      + '<div class="etl-row" style="display:flex;align-items:center;gap:8px;cursor:pointer;padding:4px 0;"'
      + ' onclick="var d=document.getElementById(\'' + id + '\');d.style.display=d.style.display===\'\'?\'none\':\'\'">'
      + '<span style="color:' + color + ';font-size:14px;width:16px;text-align:center">' + icon + '</span>'
      + '<span style="font-weight:600;font-size:12px;flex:1">' + _esc(layer.name) + '</span>'
      + decoratorHtml
      + '<span style="color:#999;font-size:11px">' + _esc(tsFormatted) + '</span>'
      + '</div>'
      + '<div id="' + id + '" style="display:none;padding:4px 4px 4px 24px;font-size:11px;color:#555">'
      + (layer.details || '')
      + '</div>'
      + connector
      + '</div>';
  });

  return '<div class="enrichment-timeline">' + rows.join('') + '</div>';
}

function _fmtTs(iso) {
  try {
    var d = new Date(iso);
    return d.toLocaleDateString('ru-RU') + ' ' + d.toLocaleTimeString('ru-RU');
  } catch (e) {
    return String(iso);
  }
}

function _esc(s) {
  return String(s || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
