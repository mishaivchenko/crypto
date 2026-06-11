import { renderLayerStatusBadge } from './layer-status-badge.js';

var _pipelineCounter = 0;

// Collapse state store: key = "screen:layerType"
var _store = {};

export function getLayerCollapsed(screen, layerType, defaultVal) {
  var key = (screen || '') + ':' + (layerType || '');
  return Object.prototype.hasOwnProperty.call(_store, key) ? _store[key] : (defaultVal !== undefined ? defaultVal : false);
}

export function clearLayerCollapsed() {
  _store = {};
}

// Guard against double-registration of the global setter (typeof check for Node.js test env)
if (typeof window !== 'undefined' && !window.__setLayerCollapsed) {
  window.__setLayerCollapsed = function(screen, layerType, collapsed) {
    var key = (screen || '') + ':' + (layerType || '');
    _store[key] = collapsed;
  };
}

function _esc(s) {
  return String(s || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function _formatAge(seconds) {
  if (seconds === null || seconds === undefined) return '—';
  if (seconds < 60) return seconds + 's ago';
  var m = Math.floor(seconds / 60);
  var s = seconds % 60;
  if (s === 0) return m + 'm ago';
  return m + 'm ' + s + 's ago';
}

function _ageColor(seconds) {
  if (seconds === null || seconds === undefined) return '';
  if (seconds < 30) return '#4eba84';
  if (seconds <= 120) return '#d4a24a';
  return '#e05a5a';
}

export function renderLayerPipeline(layers, { screen } = {}) {
  if (!layers || layers.length === 0) return '';

  var pipelineId = 'lp-' + (++_pipelineCounter);
  var now = Date.now();

  var rowsHtml = layers.map(function(layer) {
    var layerType = layer.layerType || 'base';
    var layerName = layer.layerName || layerType;
    var collapsed = layer.collapsed !== undefined ? layer.collapsed : getLayerCollapsed(screen, layerType, false);
    var bodyStyle = collapsed ? 'display:none' : '';

    // Compute age from timestamp
    var ageSeconds = null;
    if (layer.timestamp) {
      ageSeconds = Math.floor((now - +new Date(layer.timestamp)) / 1000);
      if (ageSeconds < 0) ageSeconds = 0;
    }

    var ageText = _formatAge(ageSeconds);
    var ageColor = _ageColor(ageSeconds);
    var ageStyle = ageColor ? ' style="color:' + ageColor + '"' : '';

    var metaLabel = layer.decoratorName || layer.source || '';

    var toggleChar = collapsed ? '▶' : '▾';

    var escapedLayerType = _esc(layerType);
    var escapedScreen = _esc(screen || '');

    // Header onclick: toggle body, update toggle arrow, persist state
    var headerOnclick = [
      "var c=this.closest('.layer-pipeline__card');",
      "var b=c.querySelector('.layer-pipeline__body');",
      "var t=this.querySelector('.layer-pipeline__toggle');",
      "var open=b.style.display==='none';",
      "b.style.display=open?'':'none';",
      "t.textContent=open?'▾':'▶';",
      "window.__setLayerCollapsed&&window.__setLayerCollapsed('" + escapedScreen + "','" + escapedLayerType + "',!open);"
    ].join('');

    var refreshBtn = '';

    return '<div class="layer-pipeline__row">'
      + '<div class="layer-pipeline__dot layer-pipeline__dot--' + escapedLayerType + '"></div>'
      + '<div class="layer-pipeline__card layer-pipeline__card--' + escapedLayerType + '">'
      + '<div class="layer-pipeline__header" onclick="' + headerOnclick + '">'
      + '<span class="layer-pipeline__name layer-pipeline__name--' + escapedLayerType + '">' + _esc(layerName) + '</span>'
      + renderLayerStatusBadge(layer.status)
      + '<span class="layer-pipeline__meta">' + _esc(metaLabel) + ' · <span class="lp-ticker" data-seconds="' + (ageSeconds !== null ? ageSeconds : '') + '"' + ageStyle + '>' + _esc(ageText) + '</span></span>'
      + refreshBtn
      + '<span class="layer-pipeline__toggle">' + toggleChar + '</span>'
      + '</div>'
      + '<div class="layer-pipeline__body" style="' + bodyStyle + '">'
      + (layer.content || '')
      + '</div>'
      + '</div>'
      + '</div>';
  }).join('');

  var html = '<div class="layer-pipeline" data-pipeline-id="' + _esc(pipelineId) + '">'
    + '<div class="layer-pipeline__line"></div>'
    + rowsHtml
    + '</div>';

  // Register a live ticker after the current render cycle (skipped in Node.js test env)
  if (typeof document !== 'undefined') {
    setTimeout(function() {
      var root = document.querySelector('.layer-pipeline[data-pipeline-id="' + pipelineId + '"]');
      if (!root) return;

      var intervalId = setInterval(function() {
        var tickers = root.querySelectorAll('.lp-ticker[data-seconds]');
        tickers.forEach(function(el) {
          var raw = el.getAttribute('data-seconds');
          if (raw === '' || raw === null) {
            el.textContent = '—';
            el.style.color = '';
            return;
          }
          var secs = parseInt(raw, 10) + 1;
          el.setAttribute('data-seconds', secs);
          el.textContent = _formatAge(secs);
          el.style.color = _ageColor(secs);
        });
      }, 1000);

      // Clean up when pipeline is removed from DOM
      var observer = new MutationObserver(function() {
        if (!document.body.contains(root)) {
          clearInterval(intervalId);
          observer.disconnect();
        }
      });
      observer.observe(document.body, { childList: true, subtree: true });
    }, 0);
  }

  return html;
}
