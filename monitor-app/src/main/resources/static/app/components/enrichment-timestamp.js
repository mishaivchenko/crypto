export function renderEnrichmentTimestamp(isoString, sourceLabel) {
  if (!isoString) {
    return '<span class="freshness-missing" title="Нет данных">—</span>';
  }
  const ts = +new Date(isoString);
  const nowMs = +new Date(new Date().toISOString());
  const diffMs = nowMs - ts;
  const diffSec = Math.floor(diffMs / 1000);
  const diffMin = Math.floor(diffSec / 60);

  let freshnessClass, relLabel;
  if (diffSec < 120) {
    freshnessClass = 'freshness-ok';
    relLabel = diffSec < 5 ? 'только что' : (diffSec + 'с назад');
  } else if (diffSec < 600) {
    freshnessClass = 'freshness-stale';
    const remSec = diffSec - diffMin * 60;
    relLabel = diffMin + 'м ' + remSec + 'с назад';
  } else {
    freshnessClass = 'freshness-missing';
    relLabel = diffMin + 'м назад';
  }

  const absLabel = new Date(isoString).toLocaleString('ru-RU');
  const chipHtml = sourceLabel
    ? '<span class="layer-label" style="margin-left:4px">' + _esc(sourceLabel) + '</span>'
    : '';

  return '<span class="' + freshnessClass + '" title="' + _esc(absLabel) + '">'
    + _esc(relLabel) + '</span>' + chipHtml;
}

function _esc(s) {
  return String(s || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
