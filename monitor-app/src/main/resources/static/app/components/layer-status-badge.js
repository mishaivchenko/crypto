const STATUS_MAP = {
  ok:      { icon: '●', text: 'OK',           color: 'var(--freshness-ok)',      cls: 'badge-ok' },
  warn:    { icon: '▲', text: 'Внимание',      color: 'var(--freshness-stale)',   cls: 'badge-warn' },
  blocked: { icon: '✕', text: 'Заблокирован',  color: 'var(--freshness-missing)', cls: 'badge-blocked' },
  missing: { icon: '?', text: 'Нет данных',    color: '#999',                     cls: 'badge-missing' },
  stale:   { icon: '⏱', text: 'Устарело',      color: '#b7791f',                  cls: 'badge-stale' },
};

export function renderLayerStatusBadge(status) {
  const cfg = STATUS_MAP[status] || STATUS_MAP['missing'];
  return '<span class="layer-status-badge ' + cfg.cls + '" style="color:' + cfg.color + ';font-size:11px;font-weight:600;">'
    + cfg.icon + '&nbsp;' + cfg.text + '</span>';
}
