/* Lectora AO3 — script que se inyecta en las páginas de AO3.
 * - collect(): separa la obra en fragmentos traducibles y devuelve JSON con contexto.
 * - apply(map): reemplaza fragmentos por su traducción (HTML saneado).
 * - show('tr'|'orig'): alterna toda la página. Doble toque en un párrafo = ver su original.
 * - setup(opts): modo oscuro + traducción de la interfaz de AO3.
 */
(function () {
  if (window.AO3T) return;

  var BLOCK = /^(P|H[1-6]|LI|TD|TH|DD|DT|BLOCKQUOTE|DIV|UL|OL|DL|TABLE|TBODY|THEAD|TFOOT|TR|CENTER|PRE|HR|SECTION|ARTICLE|ASIDE|HEADER|FOOTER|FIGURE|FIGCAPTION|DETAILS|SUMMARY|ADDRESS|FIELDSET|FORM|MAIN|NAV)$/;
  var SKIP = /^(SCRIPT|STYLE|NOSCRIPT|TEXTAREA|SELECT|OPTION|IFRAME|SVG|CANVAS|VIDEO|AUDIO|HR)$/;
  var LETTER = /\p{L}/u;

  var segs = [];
  var byId = {};
  var collected = null;
  var mode = 'tr';
  var opts = { dark: false, ui: true };

  function q(s) { return document.querySelector(s); }
  function qa(s) { return Array.prototype.slice.call(document.querySelectorAll(s)); }
  function clean(t) { return (t || '').replace(/\s+/g, ' ').trim(); }
  function textOf(el) { return el ? clean(el.textContent) : ''; }

  function hasBlockChild(el) {
    for (var i = 0; i < el.children.length; i++) {
      if (BLOCK.test(el.children[i].tagName)) return true;
    }
    return false;
  }

  function addSeg(el, kind) {
    if (!el || el.getAttribute('data-ao3t')) return;
    if (el.closest('.landmark')) return;
    var t = textOf(el);
    if (!t || !LETTER.test(t)) return;
    var id = 's' + segs.length;
    el.setAttribute('data-ao3t', id);
    var s = { id: id, el: el, orig: el.innerHTML, tr: null, kind: kind, local: false };
    segs.push(s);
    byId[id] = s;
  }

  /* Recorre un bloque de texto de la obra y crea un fragmento por cada párrafo "hoja".
   * Si hay texto suelto mezclado con bloques, lo envuelve en un <span> para no perderlo. */
  function walk(el, kind) {
    if (SKIP.test(el.tagName) || el.classList.contains('landmark')) return;
    if (!hasBlockChild(el)) { addSeg(el, kind); return; }
    var run = [];
    function flush() {
      if (!run.length) return;
      var txt = run.map(function (n) { return n.textContent || ''; }).join('');
      if (clean(txt) && LETTER.test(txt)) {
        var span = document.createElement('span');
        span.className = 'ao3t-run';
        el.insertBefore(span, run[0]);
        run.forEach(function (n) { span.appendChild(n); });
        addSeg(span, kind);
      }
      run = [];
    }
    Array.prototype.slice.call(el.childNodes).forEach(function (n) {
      if (n.nodeType === 1 && BLOCK.test(n.tagName)) { flush(); walk(n, kind); }
      else if (n.nodeType === 1 && SKIP.test(n.tagName)) { flush(); }
      else if (n.nodeType === 1 || n.nodeType === 3) { run.push(n); }
    });
    flush();
  }

  function listText(sel) { return qa(sel).map(textOf).filter(Boolean); }

  function collect() {
    if (collected) return collected;
    var path = location.pathname;
    var wm = path.match(/\/works\/(\d+)/);
    var cm = path.match(/\/chapters\/(\d+)/);
    var page = (wm && q('#workskin')) ? 'work' : 'list';
    var meta = {};

    if (page === 'work') {
      meta = {
        title: textOf(q('#workskin .preface h2.title')),
        author: textOf(q('#workskin .preface h3.byline')),
        fandoms: listText('dd.fandom.tags a.tag'),
        relationships: listText('dd.relationship.tags a.tag'),
        characters: listText('dd.character.tags a.tag'),
        tags: listText('dd.freeform.tags a.tag').slice(0, 30),
        rating: listText('dd.rating.tags a.tag').join(', '),
        language: textOf(q('dd.language')),
        summary: textOf(q('#workskin .preface .summary .userstuff')).slice(0, 1500)
      };
      qa('dd.freeform.tags a.tag').forEach(function (a) { addSeg(a, 'tag'); });
      qa('#workskin h2.title, #workskin h3.title, #workskin .userstuff').forEach(function (el) {
        if (el.closest('[data-ao3t]')) return;
        if (el.matches('.userstuff')) walk(el, 'text'); else addSeg(el, 'title');
      });
    } else {
      qa('.userstuff').forEach(function (el) {
        if (el.closest('[data-ao3t]') || el.closest('.comment')) return;
        walk(el, 'text');
      });
      qa('li.freeforms a.tag').forEach(function (a) { addSeg(a, 'tag'); });
    }

    collected = JSON.stringify({
      page: page,
      workId: wm ? wm[1] : null,
      chapterId: cm ? cm[1] : null,
      url: location.href,
      meta: meta,
      segs: segs.map(function (s) { return { id: s.id, html: s.orig, kind: s.kind }; })
    });
    return collected;
  }

  /* ---------- Sanitizado: solo etiquetas en línea seguras ---------- */
  var ALLOWED = { EM: 1, I: 1, STRONG: 1, B: 1, U: 1, S: 1, DEL: 1, STRIKE: 1, INS: 1, BR: 1, SPAN: 1, A: 1,
    SUP: 1, SUB: 1, SMALL: 1, BIG: 1, CODE: 1, ABBR: 1, CITE: 1, Q: 1, MARK: 1, FONT: 1, IMG: 1, KBD: 1,
    VAR: 1, TT: 1, DFN: 1, SAMP: 1, RUBY: 1, RT: 1, RP: 1 };

  var DROP = /^(SCRIPT|STYLE|IFRAME|OBJECT|EMBED|NOSCRIPT|TEMPLATE|LINK|META|BASE|FORM|INPUT|BUTTON|TEXTAREA|SELECT|SVG|MATH)$/i;

  function cleanNode(node) {
    Array.prototype.slice.call(node.childNodes).forEach(function (c) {
      if (c.nodeType === 1) {
        if (DROP.test(c.tagName)) { node.removeChild(c); return; }
        if (!ALLOWED[c.tagName]) {
          cleanNode(c);
          while (c.firstChild) node.insertBefore(c.firstChild, c);
          node.removeChild(c);
          return;
        }
        Array.prototype.slice.call(c.attributes).forEach(function (a) {
          var n = a.name.toLowerCase();
          if (n.indexOf('on') === 0 ||
              ((n === 'href' || n === 'src') && /^\s*(javascript|data):/i.test(a.value))) {
            c.removeAttribute(a.name);
          }
        });
        cleanNode(c);
      } else if (c.nodeType !== 3) {
        node.removeChild(c);
      }
    });
  }

  function sanitize(html) {
    var doc = new DOMParser().parseFromString('<div id="r">' + html + '</div>', 'text/html');
    var root = doc.getElementById('r');
    if (!root) return '';
    cleanNode(root);
    return root.innerHTML;
  }

  function render(s) {
    var showTr = (mode === 'tr') !== s.local;
    s.el.innerHTML = (showTr && s.tr) ? s.tr : s.orig;
  }

  function apply(map) {
    Object.keys(map).forEach(function (id) {
      var s = byId[id];
      if (!s) return;
      s.tr = sanitize(map[id]);
      s.el.classList.add('ao3t-done');
      render(s);
    });
  }

  function show(m) {
    mode = m;
    segs.forEach(function (s) { s.local = false; render(s); });
  }

  function clear() {
    segs.forEach(function (s) { s.tr = null; s.local = false; s.el.innerHTML = s.orig; s.el.classList.remove('ao3t-done'); });
  }

  document.addEventListener('dblclick', function (e) {
    var el = e.target && e.target.closest ? e.target.closest('[data-ao3t]') : null;
    if (!el) return;
    var s = byId[el.getAttribute('data-ao3t')];
    if (!s || !s.tr) return;
    s.local = !s.local;
    render(s);
    if (window.getSelection) window.getSelection().removeAllRanges();
  });

  /* ---------- Interfaz de AO3 en español (diccionario local, sin IA) ---------- */
  var UI = {
    'Skip header': 'Saltar encabezado', 'Fandoms': 'Fandoms', 'Browse': 'Explorar', 'Search': 'Buscar',
    'About': 'Acerca de', 'Log In': 'Iniciar sesión', 'Log in': 'Iniciar sesión', 'Sign Up': 'Registrarse',
    'Log Out': 'Cerrar sesión', 'Post': 'Publicar', 'Works': 'Obras', 'Bookmarks': 'Marcadores', 'Tags': 'Etiquetas',
    'Collections': 'Colecciones', 'People': 'Personas', 'Home': 'Inicio', 'News': 'Noticias', 'FAQ': 'Preguntas frecuentes',
    'Anime & Manga': 'Anime y manga', 'Books & Literature': 'Libros y literatura',
    'Cartoons & Comics & Graphic Novels': 'Caricaturas, cómics y novelas gráficas',
    'Celebrities & Real People': 'Celebridades y personas reales', 'Movies': 'Películas',
    'Music & Bands': 'Música y bandas', 'Other Media': 'Otros medios', 'Theater': 'Teatro',
    'TV Shows': 'Series de TV', 'Video Games': 'Videojuegos', 'Uncategorized Fandoms': 'Fandoms sin categoría',
    'All Fandoms': 'Todos los fandoms', 'Rating:': 'Clasificación:', 'Archive Warning:': 'Advertencia:',
    'Archive Warnings:': 'Advertencias:', 'Category:': 'Categoría:', 'Categories:': 'Categorías:',
    'Fandom:': 'Fandom:', 'Fandoms:': 'Fandoms:', 'Relationship:': 'Relación:', 'Relationships:': 'Relaciones:',
    'Character:': 'Personaje:', 'Characters:': 'Personajes:', 'Additional Tags:': 'Etiquetas adicionales:',
    'Language:': 'Idioma:', 'Series:': 'Serie:', 'Collections:': 'Colecciones:', 'Stats:': 'Estadísticas:',
    'Published:': 'Publicado:', 'Updated:': 'Actualizado:', 'Completed:': 'Completado:', 'Words:': 'Palabras:',
    'Chapters:': 'Capítulos:', 'Comments:': 'Comentarios:', 'Kudos:': 'Kudos:', 'Bookmarks:': 'Marcadores:',
    'Hits:': 'Visitas:', 'General Audiences': 'Público general', 'Teen And Up Audiences': 'Adolescentes y mayores',
    'Mature': 'Maduro', 'Explicit': 'Explícito', 'Not Rated': 'Sin clasificar',
    'No Archive Warnings Apply': 'No aplican advertencias',
    'Creator Chose Not To Use Archive Warnings': 'El autor eligió no usar advertencias',
    'Graphic Depictions Of Violence': 'Descripciones gráficas de violencia',
    'Major Character Death': 'Muerte de un personaje principal', 'Rape/Non-Con': 'Violación/Sin consentimiento',
    'Underage': 'Menores de edad', 'Underage Sex': 'Sexo con menores de edad',
    'Entire Work': 'Obra completa', 'Chapter by Chapter': 'Capítulo por capítulo',
    '← Previous Chapter': '← Capítulo anterior', 'Next Chapter →': 'Capítulo siguiente →',
    'Chapter Index': 'Índice de capítulos', 'Full-page index': 'Índice completo', 'Top of Work': 'Inicio de la obra',
    '↑ Top': '↑ Arriba', 'Comments': 'Comentarios', 'Bookmark': 'Guardar en marcadores',
    'Mark for Later': 'Leer más tarde', 'Mark as Read': 'Marcar como leído', 'Subscribe': 'Suscribirse',
    'Unsubscribe': 'Cancelar suscripción', 'Download': 'Descargar', 'Share': 'Compartir', 'Edit': 'Editar',
    'Hide Comments': 'Ocultar comentarios', 'Summary:': 'Resumen:', 'Notes:': 'Notas:',
    'Chapter Notes': 'Notas del capítulo', 'Chapter Text': 'Texto del capítulo', 'Work Text:': 'Texto:',
    'Chapter Summary': 'Resumen del capítulo', 'Chapter End Notes': 'Notas finales del capítulo',
    'See the end of the work for more notes.': 'Más notas al final de la obra.',
    'Actions': 'Acciones', 'Work Header': 'Encabezado', 'Proceed': 'Continuar', 'Go Back': 'Volver',
    'Yes, Continue': 'Sí, continuar', 'No, Go Back': 'No, volver',
    'This work could have adult content. If you continue, you have agreed that you are willing to see such content.':
      'Esta obra podría tener contenido adulto. Si continúas, aceptas que estás dispuesta a ver ese contenido.',
    'Adult Content Warning': 'Aviso de contenido adulto',
    'Sort and Filter': 'Ordenar y filtrar', 'Sort by': 'Ordenar por', 'Include': 'Incluir', 'Exclude': 'Excluir',
    'More Options': 'Más opciones', 'Clear Filters': 'Borrar filtros', 'Ratings': 'Clasificaciones',
    'Warnings': 'Advertencias', 'Categories': 'Categorías', 'Characters': 'Personajes',
    'Relationships': 'Relaciones', 'Additional Tags': 'Etiquetas adicionales',
    'Other tags to include': 'Otras etiquetas a incluir', 'Other tags to exclude': 'Otras etiquetas a excluir',
    'Crossovers': 'Crossovers', 'Completion status': 'Estado', 'All works': 'Todas las obras',
    'Complete works only': 'Solo completas', 'Works in progress only': 'Solo en progreso',
    'Word Count': 'Cantidad de palabras', 'Date Updated': 'Fecha de actualización', 'Date Posted': 'Fecha de publicación',
    'Language': 'Idioma', 'Search within results': 'Buscar en los resultados', 'Complete Work': 'Obra completa',
    'Work in Progress': 'En progreso', 'Previous': 'Anterior', 'Next': 'Siguiente', '← Previous': '← Anterior',
    'Next →': 'Siguiente →', 'Profile': 'Perfil', 'Dashboard': 'Panel', 'Preferences': 'Preferencias',
    'History': 'Historial', 'Subscriptions': 'Suscripciones', 'Inbox': 'Bandeja de entrada',
    'Statistics': 'Estadísticas', 'Series': 'Series', 'Gifts': 'Regalos', 'Reply': 'Responder', 'Thread': 'Hilo',
    'Parent Thread': 'Hilo principal', 'Delete': 'Eliminar', 'Guest': 'Invitado', 'Recent works': 'Obras recientes',
    'Recent bookmarks': 'Marcadores recientes', 'Recent series': 'Series recientes', 'by': 'por', 'and': 'y',
    'Kudos': 'Kudos', 'Hits': 'Visitas', 'Words': 'Palabras', 'Chapters': 'Capítulos', 'Author': 'Autor',
    'Title': 'Título', 'Creator': 'Autor', 'Date Bookmarked': 'Fecha en marcadores', 'Filters': 'Filtros',
    'Expand Fandoms List': 'Ver lista de fandoms', 'Collapse Fandoms List': 'Ocultar lista de fandoms',
    'Bookmarker\'s Notes': 'Notas de quien guardó', 'Bookmarker\'s Tags': 'Etiquetas de quien guardó',
    'Rec': 'Recomendado', 'Private Bookmark': 'Marcador privado', 'Works in Series': 'Obras de la serie',
    'Series Begun:': 'Serie iniciada:', 'Series Updated:': 'Serie actualizada:', 'Description:': 'Descripción:',
    'Complete:': 'Completa:', 'Yes': 'Sí', 'No': 'No'
  };
  var UI_RX = [
    [/^Chapter (\d+)$/, 'Capítulo $1'],
    [/^Comments \((\d+)\)$/, 'Comentarios ($1)'],
    [/^Hide Comments \((\d+)\)$/, 'Ocultar comentarios ($1)'],
    [/^Bookmarks \((\d+)\)$/, 'Marcadores ($1)'],
    [/^Works \((\d+)\)$/, 'Obras ($1)'],
    [/^Part (\d+) of$/, 'Parte $1 de'],
    [/^Chapter (\d+) of (\d+)$/, 'Capítulo $1 de $2'],
    [/^(\d+) of (\d+)$/, '$1 de $2']
  ];

  function trText(t) {
    var k = t.trim();
    if (!k) return null;
    if (Object.prototype.hasOwnProperty.call(UI, k)) return t.replace(k, function () { return UI[k]; });
    for (var i = 0; i < UI_RX.length; i++) {
      if (UI_RX[i][0].test(k)) {
        var nk = k.replace(UI_RX[i][0], UI_RX[i][1]);
        return t.replace(k, function () { return nk; });
      }
    }
    return null;
  }

  var UI_SKIP = '.userstuff, [data-ao3t], script, style, textarea, h4.heading a, h5.heading a, h2.title, h3.byline, .ao3t-keep';

  function translateUi(root) {
    if (!opts.ui || !root) return;
    var w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
    var nodes = [], n;
    while ((n = w.nextNode())) nodes.push(n);
    nodes.forEach(function (node) {
      var p = node.parentElement;
      if (!p || p.closest(UI_SKIP)) return;
      var r = trText(node.nodeValue);
      if (r !== null && r !== node.nodeValue) node.nodeValue = r;
    });
  }

  var moTimer = null, moStarted = false;
  var mo = new MutationObserver(function () {
    if (!opts.ui) return;
    clearTimeout(moTimer);
    moTimer = setTimeout(function () { translateUi(document.body); }, 400);
  });

  /* ---------- Modo oscuro ---------- */
  var DARK_CSS =
    'html,body,#outer,#inner,#main,#header,#footer,.wrapper,.region,.module,.group,dl.meta,dl.work,.listbox,' +
    'li.blurb,.blurb,fieldset,form,.index,.comment,.thread,li.comment,#workskin,#chapters,.chapter,.preface,' +
    '.userstuff,.notes,.summary,.splash,.actions,ul.navigation,.heading,.byline,blockquote,dd,dt,table,td,th,' +
    '#header .primary,.dropdown-menu,.menu,.secondary{background-color:#1b1620 !important;color:#eee3f0 !important;' +
    'border-color:#3d3044 !important;box-shadow:none !important;background-image:none !important}' +
    'a,a:visited,a:link{color:#f2a7d0 !important}' +
    'input,textarea,select,button,.actions a,.actions input{background:#2a2030 !important;color:#eee3f0 !important;' +
    'border-color:#4a3a52 !important;background-image:none !important}' +
    '.tag{border-color:#4a3a52 !important}';

  function setDark(on) {
    var st = document.getElementById('ao3t-dark');
    if (on && !st) {
      st = document.createElement('style');
      st.id = 'ao3t-dark';
      st.textContent = DARK_CSS;
      (document.head || document.documentElement).appendChild(st);
    } else if (!on && st) {
      st.parentNode.removeChild(st);
    }
  }

  function setup(o) {
    for (var k in (o || {})) opts[k] = o[k];
    setDark(!!opts.dark);
    if (opts.ui && document.body) {
      translateUi(document.body);
      if (!moStarted) { mo.observe(document.body, { childList: true, subtree: true }); moStarted = true; }
    }
  }

  var st = document.createElement('style');
  st.textContent = '.ao3t-run{display:inline}';
  (document.head || document.documentElement).appendChild(st);

  window.AO3T = { collect: collect, apply: apply, show: show, clear: clear, setup: setup };
})();
