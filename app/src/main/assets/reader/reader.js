/*
 * Noir Books — maquetación por páginas dentro del WebView.
 * Cada capítulo se reparte en columnas del ancho de la pantalla; pasar página es desplazar una pantalla.
 * Se comunica con la app mediante el objeto "Android" (ver ReaderBridge en Kotlin).
 */
(function () {
  'use strict';
  var A = window.Android;
  var S = null;           // ajustes actuales
  var page = 0, count = 1;
  var ready = false;
  var styleEl = null;

  var offset = 0;         // desplazamiento actual en px (página * ancho)
  function vw() { return window.innerWidth || document.documentElement.clientWidth; }
  // Se desplaza con transform en lugar de scrollLeft: el navegador limita el scroll a la última
  // columna completa y la última página quedaba descolocada.
  function setOffset(x) {
    offset = x;
    document.documentElement.style.setProperty('transform', 'translateX(' + (-x) + 'px)', 'important');
  }

  function cssFor(s) {
    var m = s.margin;
    var cols = (s.twoPages && vw() > 880 && vw() > window.innerHeight) ? 2 : 1;
    var c = [];
    c.push('html{height:100vh!important;width:100vw!important;max-width:none!important;margin:0!important;' +
      'padding:22px ' + m + 'px 14px ' + m + 'px!important;box-sizing:border-box!important;' +
      'column-count:' + cols + '!important;column-gap:' + (2 * m) + 'px!important;column-fill:auto!important;' +
      'overflow:hidden!important;background:' + s.bg + '!important;color:' + s.fg + '!important;' +
      'font-size:' + s.size + 'px!important;-webkit-text-size-adjust:100%!important;}');
    c.push('body{margin:0!important;padding:0!important;width:auto!important;max-width:none!important;' +
      'height:auto!important;background:transparent!important;color:inherit!important;font-size:1rem!important;' +
      'line-height:' + s.lh + '!important;overflow-wrap:break-word;widows:2;orphans:2;}');
    c.push('p,li,blockquote,dd,dt,td,th{line-height:inherit!important;}');
    c.push('p,li,blockquote,dd,dt{font-size:inherit!important;}');
    c.push('body *{color:inherit!important;background-color:transparent!important;border-color:currentColor;}');
    c.push('body *:not(img):not(svg):not(table):not(td):not(th){max-width:100%;}');
    c.push('img,svg,video{max-width:100%!important;max-height:calc(100vh - 44px)!important;height:auto;' +
      'object-fit:contain;break-inside:avoid;-webkit-column-break-inside:avoid;}');
    c.push('h1,h2,h3,h4,h5,h6{break-after:avoid;-webkit-column-break-after:avoid;line-height:1.25!important;}');
    c.push('pre{white-space:pre-wrap!important;}');
    c.push('a{color:inherit!important;text-decoration-thickness:1px;text-underline-offset:2px;}');
    c.push('::selection{background:' + (s.dark ? 'rgba(255,255,255,0.25)' : 'rgba(0,0,0,0.18)') + ';}');
    if (s.font) {
      c.push('body,body *:not(code):not(pre):not(kbd):not(samp):not(tt){font-family:' + s.font + '!important;}');
    }
    if (s.justify) {
      c.push('p{text-align:justify!important;}');
    }
    if (s.hyphens) {
      c.push('p,li,blockquote{-webkit-hyphens:auto!important;hyphens:auto!important;}');
    } else {
      c.push('p,li,blockquote{-webkit-hyphens:manual!important;hyphens:manual!important;}');
    }
    if (s.dark) {
      c.push('img{filter:brightness(0.9);}');
    }
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

  function show(p, notify) {
    page = Math.max(0, Math.min(count - 1, p));
    setOffset(page * vw());
    if (notify !== false) A.onPage(page, count);
  }

  function anchorPage(id) {
    if (!id) return 0;
    var el = document.getElementById(id);
    if (!el) { var byName = document.getElementsByName(id); el = byName && byName[0]; }
    if (!el) return 0;
    var r = el.getBoundingClientRect();
    var x = r.left + offset;
    return Math.max(0, Math.floor((x + 1) / vw()));
  }

  function place(t) {
    if (!t || t.type === 'start') return 0;
    if (t.type === 'end') return count - 1;
    if (t.type === 'fraction') return Math.round(t.value * count);
    if (t.type === 'anchor') return anchorPage(t.value);
    return 0;
  }

  function whenStable(cb) {
    var done = false;
    function go() { if (done) return; done = true; requestAnimationFrame(function () { requestAnimationFrame(cb); }); }
    // Fuerza la maquetación para que se pidan las tipografías y luego espera a fuentes e imágenes.
    void document.documentElement.offsetHeight;
    var waits = [];
    if (document.fonts && document.fonts.ready) waits.push(document.fonts.ready);
    Array.prototype.forEach.call(document.images || [], function (img) {
      if (!img.complete) waits.push(new Promise(function (res) { img.addEventListener('load', res); img.addEventListener('error', res); }));
    });
    Promise.all(waits).then(go, go);
    setTimeout(go, 2500);
  }

  function relayout(keepFraction) {
    var f = count > 0 ? page / count : 0;
    applyStyle();
    whenStable(function () {
      measure();
      show(keepFraction ? Math.round(f * count) : page);
    });
  }

  // ---- Gestos: deslizar para pasar página, tocar para que la app decida (zonas izquierda/centro/derecha) ----
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
    if (sel && String(sel).length > 0) return;
    A.onTap(e.clientX / vw(), e.clientY / window.innerHeight);
  }, true);

  var resizeTimer = null;
  window.addEventListener('resize', function () {
    if (!ready) return;
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function () { relayout(true); }, 180);
  });

  var api = {
    next: function () {
      if (page < count - 1) show(page + 1); else A.onEnd();
    },
    prev: function () {
      if (page > 0) show(page - 1); else A.onStart();
    },
    goFraction: function (f) { show(Math.round(f * count)); },
    goPage: function (p) { show(p); },
    goAnchor: function (id) { show(anchorPage(id)); },
    apply: function (json) {
      S = typeof json === 'string' ? JSON.parse(json) : json;
      relayout(true);
    },
    /** Primeras palabras de la página visible (para los marcadores). */
    snippet: function () {
      try {
        var m = S ? S.margin : 20;
        var r = document.caretRangeFromPoint(m + 4, 30);
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
    var cfg;
    try { cfg = JSON.parse(A.init()); } catch (e) { cfg = {}; }
    S = cfg.settings || { bg: '#1C1C1E', fg: '#D6D6D6', size: 19, lh: 1.5, margin: 24, justify: true, hyphens: true, twoPages: true };
    if (cfg.lang && !document.documentElement.getAttribute('lang')) {
      document.documentElement.setAttribute('lang', cfg.lang);
    }
    applyStyle();
    whenStable(function () {
      measure();
      show(place(cfg.target), false);
      document.documentElement.style.setProperty('visibility', 'visible', 'important');
      ready = true;
      A.onReady(page, count);
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
