/*
 * Noir Books — maquetación por páginas dentro del WebView.
 * Cada capítulo se reparte en columnas del ancho de la pantalla; pasar página es desplazar una pantalla.
 * Además: posición exacta (elemento + letra), subrayados sin tocar el libro (CSS Custom Highlight),
 * selección de texto y búsqueda. Habla con la app mediante el objeto "Android".
 */
(function () {
  'use strict';
  var A = window.Android;
  var S = null;           // ajustes actuales
  var page = 0, count = 1;
  var ready = false;
  var styleEl = null;
  var HL = { 0: 'rgba(232,197,71,0.42)', 1: 'rgba(111,168,220,0.40)', 2: 'rgba(123,198,126,0.40)', 3: 'rgba(232,138,168,0.40)' };
  var cfg = null;          // configuración del capítulo (ajustes, destino, marcas de página)
  var highlights = [];    // [{id, color, range}]

  var offset = 0;         // desplazamiento actual en px (página * ancho)
  // Medidas reales en píxeles. Nada de vh/vw: el WebView de Android calcula esas unidades al crearse,
  // y si en ese momento mide 0, cada página se queda en una sola línea.
  function vw() { return window.innerWidth || document.documentElement.clientWidth; }
  function vh() { return window.innerHeight || document.documentElement.clientHeight; }
  // Se desplaza con transform en lugar de scrollLeft: el navegador limita el scroll a la última
  // columna completa y la última página quedaba descolocada.
  function setOffset(x) {
    offset = x;
    document.documentElement.style.setProperty('transform', 'translateX(' + (-x) + 'px)', 'important');
  }

  function cssFor(s) {
    var m = s.margin;
    var mt = s.mtop || 22, mb = s.mbottom || 14;
    var pub = !!s.publisher;
    var W = vw(), H = vh();
    var cols = (s.twoPages && W > 880 && W > H) ? 2 : 1;
    var c = [];
    c.push('html{height:' + H + 'px!important;width:' + W + 'px!important;max-width:none!important;margin:0!important;' +
      'padding:' + mt + 'px ' + m + 'px ' + mb + 'px ' + m + 'px!important;box-sizing:border-box!important;' +
      'column-count:' + cols + '!important;column-gap:' + (2 * m) + 'px!important;column-fill:auto!important;' +
      'overflow:hidden!important;background:' + s.bg + '!important;color:' + s.fg + '!important;' +
      'font-size:' + s.size + 'px!important;-webkit-text-size-adjust:100%!important;}');
    c.push('body{margin:0!important;padding:0!important;width:auto!important;max-width:none!important;' +
      'height:auto!important;background:transparent!important;color:inherit!important;font-size:1rem!important;' +
      (pub ? '' : 'line-height:' + s.lh + '!important;') + 'overflow-wrap:break-word;widows:2;orphans:2;}');
    if (!pub) c.push('p,li,blockquote,dd,dt,td,th{line-height:inherit!important;}');
    c.push('p,li,blockquote,dd,dt{font-size:inherit!important;}');
    c.push('body *{color:inherit!important;background-color:transparent!important;border-color:currentColor;}');
    c.push('body *:not(img):not(svg):not(table):not(td):not(th){max-width:100%;}');
    c.push('img,svg,video{max-width:100%!important;max-height:' + Math.max(80, H - 44) + 'px!important;height:auto;' +
      'object-fit:contain;break-inside:avoid;-webkit-column-break-inside:avoid;}');
    c.push('h1,h2,h3,h4,h5,h6{break-after:avoid;-webkit-column-break-after:avoid;line-height:1.25!important;}');
    c.push('pre{white-space:pre-wrap!important;}');
    c.push('a{color:inherit!important;text-decoration-thickness:1px;text-underline-offset:2px;}');
    c.push('::selection{background:' + (s.dark ? 'rgba(255,255,255,0.25)' : 'rgba(0,0,0,0.18)') + ';}');
    if (s.font && !pub) {
      c.push('body,body *:not(code):not(pre):not(kbd):not(samp):not(tt){font-family:' + s.font + '!important;}');
    }
    if (s.justify && !pub) {
      c.push('p{text-align:justify!important;}');
    }
    if (!pub && s.para >= 0) {
      c.push('p{margin-top:0!important;margin-bottom:' + s.para + 'em!important;}');
    }
    if (!pub && s.indent >= 0) {
      c.push('p{text-indent:' + s.indent + 'em!important;}');
      c.push('h1+p,h2+p,h3+p,h4+p,h5+p,h6+p,hr+p{text-indent:0!important;}');
    }
    if (pub) {
      // Diseño del libro: solo colores, tamaño y márgenes.
    } else if (s.hyphens) {
      c.push('p,li,blockquote{-webkit-hyphens:auto!important;hyphens:auto!important;}');
    } else {
      c.push('p,li,blockquote{-webkit-hyphens:manual!important;hyphens:manual!important;}');
    }
    if (s.dark) {
      c.push('img{filter:brightness(0.9);}');
    }
    Object.keys(HL).forEach(function (k) { c.push('::highlight(nb-' + k + '){background-color:' + HL[k] + ';color:inherit;}'); });
    c.push('::highlight(nb-tts){background-color:' + (s.dark ? 'rgba(255,255,255,0.16)' : 'rgba(0,0,0,0.10)') + ';}');
    c.push('::highlight(nb-search){background-color:' + (s.dark ? 'rgba(255,255,255,0.35)' : 'rgba(0,0,0,0.22)') + ';}');
    c.push('#__noir_end{display:block!important;height:0!important;margin:0!important;padding:0!important;border:0!important;}');
    return c.join('\n');
  }

  function applyStyle() {
    if (!styleEl) {
      styleEl = document.createElement('style');
      styleEl.id = '__noir_style';
      (document.head || document.documentElement).appendChild(styleEl);
    }
    styleEl.textContent = cssFor(S);
    if (!document.getElementById('__noir_end') && document.body) {
      var end = document.createElement('div');
      end.id = '__noir_end';
      document.body.appendChild(end);
    }
  }

  function measure() {
    var end = document.getElementById('__noir_end');
    var w = vw();
    if (!end || w <= 0) { count = 1; return; }
    var x = end.getBoundingClientRect().left + offset;
    count = Math.max(1, Math.floor((x + 1) / w) + 1);
  }

  // ---- Posición exacta: ruta de nodos desde <body> + letra ----

  function pathOf(node) {
    var p = [];
    var body = document.body;
    while (node && node !== body) {
      var parent = node.parentNode;
      if (!parent) return null;
      p.unshift(Array.prototype.indexOf.call(parent.childNodes, node));
      node = parent;
    }
    return node === body ? p : null;
  }

  function nodeAt(p) {
    var n = document.body;
    for (var i = 0; i < p.length; i++) {
      if (!n || !n.childNodes || p[i] < 0 || p[i] >= n.childNodes.length) return null;
      n = n.childNodes[p[i]];
    }
    return n;
  }

  function nodeLen(n) { return n.nodeType === 3 ? n.length : n.childNodes.length; }

  function toRange(o) {
    try {
      var sn = nodeAt(o.s), en = nodeAt(o.e);
      if (!sn || !en) return null;
      var r = document.createRange();
      r.setStart(sn, Math.min(o.so, nodeLen(sn)));
      r.setEnd(en, Math.min(o.eo, nodeLen(en)));
      return r.collapsed && (o.s.join() !== o.e.join() || o.so !== o.eo) ? null : r;
    } catch (e) { return null; }
  }

  function fromRange(r) {
    var s = pathOf(r.startContainer), e = pathOf(r.endContainer);
    if (!s || !e) return null;
    return { s: s, so: r.startOffset, e: e, eo: r.endOffset };
  }

  function pageOfRect(rect) {
    return Math.max(0, Math.min(count - 1, Math.floor((rect.left + offset + 1) / vw())));
  }

  function pageOfRange(r) {
    var rects = r.getClientRects();
    var rect = rects.length ? rects[0] : r.getBoundingClientRect();
    return pageOfRect(rect);
  }

  /** Posición del principio de la página visible. */
  function anchorNow() {
    try {
      var m = S ? S.margin : 20;
      var r = document.caretRangeFromPoint(m + 2, (S && S.mtop ? S.mtop : 22) + 4);
      if (!r || !r.startContainer || !document.body.contains(r.startContainer)) return '';
      var p = pathOf(r.startContainer);
      return p ? JSON.stringify({ p: p, o: r.startOffset }) : '';
    } catch (e) { return ''; }
  }

  function pageOfAnchor(a) {
    try {
      var o = typeof a === 'string' ? JSON.parse(a) : a;
      var n = nodeAt(o.p);
      if (!n) return -1;
      var r = document.createRange();
      r.setStart(n, Math.min(o.o, nodeLen(n)));
      r.collapse(true);
      var rect = r.getBoundingClientRect();
      if (!rect || (rect.left === 0 && rect.top === 0 && rect.width === 0)) {
        var el = n.nodeType === 1 ? n : n.parentElement;
        if (!el) return -1;
        rect = el.getBoundingClientRect();
      }
      return pageOfRect(rect);
    } catch (e) { return -1; }
  }

  function show(p, notify) {
    page = Math.max(0, Math.min(count - 1, p));
    setOffset(page * vw());
    if (notify !== false) { A.onPage(page, count, anchorNow()); reportPrinted(); }
  }

  /** Página de la edición en papel: la última marca de página que empieza en esta página o antes. */
  function reportPrinted() {
    try {
      if (!cfg || !cfg.pageMarks || !cfg.pageMarks.length || !A.onPrinted) return;
      var label = cfg.pagePrev || '';
      for (var i = 0; i < cfg.pageMarks.length; i++) {
        var m = cfg.pageMarks[i];
        var el = document.getElementById(m.id);
        if (!el) { var byName = document.getElementsByName(m.id); el = byName && byName[0]; }
        if (!el) continue;
        if (pageOfRect(el.getBoundingClientRect()) <= page) label = m.label; else break;
      }
      A.onPrinted(label);
    } catch (e) {}
  }

  function anchorPage(id) {
    if (!id) return 0;
    var el = document.getElementById(id);
    if (!el) { var byName = document.getElementsByName(id); el = byName && byName[0]; }
    if (!el) return 0;
    return pageOfRect(el.getBoundingClientRect());
  }

  // ---- Búsqueda (sin distinguir mayúsculas ni tildes) ----

  function fold(c) {
    var f = c.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
    return f.length ? f.charAt(0) : c;
  }

  function findRange(query, nth) {
    var q = '';
    for (var i = 0; i < query.length; i++) q += fold(query.charAt(i));
    if (!q) return null;
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    var nodes = [], starts = [], text = '', n;
    while ((n = walker.nextNode())) {
      nodes.push(n); starts.push(text.length);
      var t = n.data, f = '';
      for (var j = 0; j < t.length; j++) f += fold(t.charAt(j));
      text += f;
    }
    var idx = -1, from = 0;
    for (var k = 0; k <= (nth || 0); k++) {
      idx = text.indexOf(q, from);
      if (idx < 0) return null;
      from = idx + 1;
    }
    function locate(pos) {
      var lo = 0;
      for (var x = 0; x < starts.length; x++) if (starts[x] <= pos) lo = x; else break;
      return [nodes[lo], pos - starts[lo]];
    }
    var a = locate(idx), b = locate(idx + q.length);
    var r = document.createRange();
    r.setStart(a[0], a[1]);
    r.setEnd(b[0], Math.min(b[1], b[0].length));
    return r;
  }

  // ---- Lectura en voz alta: frases del capítulo con su posición ----

  var TTS_BLOCKS = 'p,li,h1,h2,h3,h4,h5,h6,blockquote,dd,dt,figcaption,td,th,pre';

  /** Frases del capítulo (desde la página visible si [fromHere]): [{t: texto, r: rango}]. */
  function ttsSentences(fromHere) {
    var out = [];
    var blocks = Array.prototype.filter.call(document.body.querySelectorAll(TTS_BLOCKS), function (b) {
      return !b.querySelector(TTS_BLOCKS);
    });
    if (!blocks.length) blocks = [document.body];
    var re = /[^.!?…]+(?:[.!?…]+["»”’)\]]*|$)/g;
    blocks.forEach(function (b) {
      var nodes = [], starts = [], text = '';
      var w = document.createTreeWalker(b, NodeFilter.SHOW_TEXT, null);
      var n;
      while ((n = w.nextNode())) {
        if (!n.nodeValue) continue;
        var pe = n.parentElement;
        if (pe && (pe.closest('script,style,rt'))) continue;
        nodes.push(n); starts.push(text.length); text += n.nodeValue;
      }
      if (!text.trim()) return;
      function locate(pos) {
        for (var i = nodes.length - 1; i >= 0; i--) if (starts[i] <= pos) return [nodes[i], Math.min(pos - starts[i], nodes[i].length)];
        return [nodes[0], 0];
      }
      var m;
      re.lastIndex = 0;
      while ((m = re.exec(text))) {
        if (!m[0]) { re.lastIndex++; continue; }
        var raw = m[0];
        var t = raw.replace(/\s+/g, ' ').trim();
        if (!t || !/[\wÀ-ÿ]/.test(t)) continue;
        var lead = raw.length - raw.replace(/^\s+/, '').length;
        var a = locate(m.index + lead), z = locate(m.index + raw.replace(/\s+$/, '').length);
        try {
          var r = document.createRange();
          r.setStart(a[0], a[1]); r.setEnd(z[0], z[1]);
          if (fromHere && pageOfRange(r) < page) continue;
          var j = fromRange(r);
          if (j) out.push({ t: t, r: j });
        } catch (e) {}
      }
    });
    return out;
  }

  function markSearch(r) {
    if (!window.CSS || !CSS.highlights || typeof Highlight === 'undefined') return;
    CSS.highlights.set('nb-search', new Highlight(r));
    setTimeout(function () { CSS.highlights.delete('nb-search'); }, 4000);
  }

  // ---- Subrayados (no se toca el libro: CSS Custom Highlight API) ----

  function paintHighlights() {
    if (!window.CSS || !CSS.highlights || typeof Highlight === 'undefined') return;
    [0, 1, 2, 3].forEach(function (c) { CSS.highlights.delete('nb-' + c); });
    var groups = { 0: [], 1: [], 2: [], 3: [] };
    highlights.forEach(function (h) {
      if (!h._r) h._r = toRange(h.range);
      if (h._r && groups[h.color]) groups[h.color].push(h._r);
    });
    Object.keys(groups).forEach(function (c) {
      if (!groups[c].length) return;
      var hl = new Highlight();
      groups[c].forEach(function (r) { hl.add(r); });
      CSS.highlights.set('nb-' + c, hl);
    });
  }

  function highlightAt(x, y) {
    try {
      var p = document.caretRangeFromPoint(x, y);
      if (!p) return null;
      for (var i = highlights.length - 1; i >= 0; i--) {
        var r = highlights[i]._r;
        if (r && r.isPointInRange(p.startContainer, p.startOffset)) return highlights[i];
      }
    } catch (e) {}
    return null;
  }

  // ---- Selección de texto: la app muestra su propia barra (subrayar, nota, diccionario…) ----

  function sentenceAround(r) {
    var el = r.startContainer.nodeType === 1 ? r.startContainer : r.startContainer.parentElement;
    while (el && el !== document.body && getComputedStyle(el).display === 'inline') el = el.parentElement;
    var whole = (el && el.textContent) || '';
    var sel = r.toString();
    var at = whole.indexOf(sel);
    if (at < 0) return sel;
    var a = Math.max(whole.lastIndexOf('.', at), whole.lastIndexOf('!', at), whole.lastIndexOf('?', at), whole.lastIndexOf('…', at));
    var endCandidates = ['.', '!', '?', '…'].map(function (c) { var k = whole.indexOf(c, at + sel.length); return k < 0 ? whole.length : k + 1; });
    var b = Math.min.apply(null, endCandidates);
    return whole.substring(a + 1, b).replace(/\s+/g, ' ').trim().substring(0, 400);
  }

  var selTimer = null, lastSel = '';
  document.addEventListener('selectionchange', function () {
    clearTimeout(selTimer);
    selTimer = setTimeout(function () {
      var sel = window.getSelection();
      if (!sel || sel.rangeCount === 0 || sel.isCollapsed || !String(sel).trim()) {
        if (lastSel) { lastSel = ''; A.onSelection(''); }
        return;
      }
      var r = sel.getRangeAt(0);
      var data = fromRange(r);
      if (!data) return;
      var rect = r.getBoundingClientRect();
      var payload = JSON.stringify({
        range: data, text: String(sel).replace(/\s+/g, ' ').trim().substring(0, 4000),
        sentence: sentenceAround(r), fraction: count > 0 ? pageOfRange(r) / count : 0,
        top: rect.top / vh(), bottom: rect.bottom / vh()
      });
      if (payload !== lastSel) { lastSel = payload; A.onSelection(payload); }
    }, 250);
  });

  // ---- Gestos: deslizar para pasar página, tocar para que la app decida ----
  var sx = 0, sy = 0, st = 0, multi = false;
  document.addEventListener('touchstart', function (e) {
    multi = e.touches.length > 1;
    var t = e.touches[0]; sx = t.clientX; sy = t.clientY; st = Date.now();
  }, { passive: true });
  document.addEventListener('touchend', function (e) {
    if (multi) return;
    var t = e.changedTouches[0];
    var dx = t.clientX - sx, dy = t.clientY - sy;
    if (Math.abs(dx) > 45 && Math.abs(dx) > Math.abs(dy) * 1.4 && Date.now() - st < 900) {
      var sel = window.getSelection && window.getSelection();
      if (sel && String(sel).length > 0) return;
      if (dx < 0) api.next(); else api.prev();
    }
  }, { passive: true });

  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest ? e.target.closest('a[href]') : null;
    if (a) {
      e.preventDefault();
      var href = a.getAttribute('href') || '';
      if (href.charAt(0) === '#') { show(anchorPage(decodeURIComponent(href.substring(1)))); return; }
      var abs = a.href;
      if (/^https:\/\/book\.local\//.test(abs)) {
        var here = location.href.split('#')[0];
        if (abs.split('#')[0] === here) { show(anchorPage(decodeURIComponent(abs.split('#')[1] || ''))); return; }
        A.onLink(abs);
      } else {
        A.onExternalLink(abs);
      }
      return;
    }
    var sel = window.getSelection && window.getSelection();
    if (sel && String(sel).length > 0) { sel.removeAllRanges(); return; }
    var h = highlightAt(e.clientX, e.clientY);
    if (h) { A.onHighlightTap(String(h.id)); return; }
    A.onTap(e.clientX / vw(), e.clientY / vh());
  }, true);

  var resizeTimer = null;
  var lastSize = '';
  window.addEventListener('resize', function () {
    if (!ready) return;
    var sz = vw() + 'x' + vh();
    if (sz === lastSize) return;
    lastSize = sz;
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function () { relayout(); }, 180);
  });

  function whenStable(cb) {
    var done = false;
    function go() { if (done) return; done = true; requestAnimationFrame(function () { requestAnimationFrame(cb); }); }
    void document.documentElement.offsetHeight;
    var waits = [];
    if (document.fonts && document.fonts.ready) waits.push(document.fonts.ready);
    Array.prototype.forEach.call(document.images || [], function (img) {
      if (!img.complete) waits.push(new Promise(function (res) { img.addEventListener('load', res); img.addEventListener('error', res); }));
    });
    Promise.all(waits).then(go, go);
    setTimeout(go, 2500);
  }

  /** Vuelve a maquetar conservando la posición exacta (no la fracción). */
  function relayout() {
    var a = anchorNow();
    var f = count > 0 ? page / count : 0;
    applyStyle();
    whenStable(function () {
      measure();
      var p = a ? pageOfAnchor(a) : -1;
      show(p >= 0 ? p : Math.round(f * count));
    });
  }

  function place(t) {
    if (!t || t.type === 'start') return 0;
    if (t.type === 'end') return count - 1;
    if (t.type === 'pos') { var p = pageOfAnchor(t.value); if (p >= 0) return p; return Math.round((t.fraction || 0) * count); }
    if (t.type === 'fraction') return Math.round(t.value * count);
    if (t.type === 'anchor') return anchorPage(t.value);
    if (t.type === 'range') { var r = toRange(t.value); return r ? pageOfRange(r) : 0; }
    if (t.type === 'search') {
      var sr = findRange(t.value, t.n || 0);
      if (sr) { markSearch(sr); return pageOfRange(sr); }
      return 0;
    }
    return 0;
  }

  var api = {
    next: function () { if (page < count - 1) show(page + 1); else A.onEnd(); },
    prev: function () { if (page > 0) show(page - 1); else A.onStart(); },
    goFraction: function (f) { show(Math.round(f * count)); },
    goPage: function (p) { show(p); },
    goAnchor: function (id) { show(anchorPage(id)); },
    apply: function (json) { S = typeof json === 'string' ? JSON.parse(json) : json; relayout(); },
    setHighlights: function (json) {
      highlights = (typeof json === 'string' ? JSON.parse(json) : json) || [];
      paintHighlights();
    },
    clearSelection: function () { var s = window.getSelection(); if (s) s.removeAllRanges(); lastSel = ''; },
    ttsSentences: function (fromHere) { try { return JSON.stringify(ttsSentences(!!fromHere)); } catch (e) { return '[]'; } },
    ttsMark: function (json) {
      try {
        CSS.highlights.delete('nb-tts');
        var r = toRange(typeof json === 'string' ? JSON.parse(json) : json);
        if (!r) return;
        CSS.highlights.set('nb-tts', new Highlight(r));
        var p = pageOfRange(r);
        if (p !== page) show(p);
      } catch (e) {}
    },
    ttsClear: function () { try { CSS.highlights.delete('nb-tts'); } catch (e) {} },
    pageOfRangeJson: function (json) { var r = toRange(JSON.parse(json)); return r ? pageOfRange(r) : -1; },
    /** Primeras palabras de la página visible (para los marcadores). */
    snippet: function () {
      try {
        var m = S ? S.margin : 20;
        var r = document.caretRangeFromPoint(m + 4, (S && S.mtop ? S.mtop : 22) + 8);
        if (!r || !r.startContainer) return '';
        var node = r.startContainer;
        var text = (node.textContent || '').substring(r.startOffset);
        var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
        walker.currentNode = node;
        while (text.replace(/\s+/g, ' ').trim().length < 120) {
          var n = walker.nextNode();
          if (!n) break;
          text += ' ' + n.textContent;
        }
        text = text.replace(/\s+/g, ' ').trim();
        return text.length > 110 ? text.substring(0, 110).replace(/\s\S*$/, '') + '…' : text;
      } catch (err) { return ''; }
    }
  };
  window.noir = api;

  function init() {
    try { cfg = JSON.parse(A.init()); } catch (e) { cfg = {}; }
    S = cfg.settings || { bg: '#1C1C1E', fg: '#D6D6D6', size: 19, lh: 1.5, margin: 24, justify: true, hyphens: true, twoPages: true };
    highlights = cfg.highlights || [];
    if (cfg.lang && !document.documentElement.getAttribute('lang')) {
      document.documentElement.setAttribute('lang', cfg.lang);
    }
    waitForSize(function () {
    applyStyle();
    whenStable(function () {
      measure();
      paintHighlights();
      show(place(cfg.target), false);
      document.documentElement.style.setProperty('visibility', 'visible', 'important');
      ready = true;
      A.onReady(page, count, anchorNow());
      reportPrinted();
    });
    });
  }

  /** Espera a que la ventana tenga tamaño (hasta 3 s) antes de maquetar. */
  function waitForSize(cb) {
    var t0 = Date.now();
    (function check() {
      if ((vw() > 100 && vh() > 100) || Date.now() - t0 > 3000) cb();
      else requestAnimationFrame(check);
    })();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
