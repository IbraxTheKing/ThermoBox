/*
 * Light / dark theme toggle, and the theme of the Chart.js charts (p:lineChart extender).
 * Loaded in the <head> without defer: the theme is applied before the page is painted.
 */
var tbCharts = (function () {
    var root = document.documentElement;

    function css(name) {
        return getComputedStyle(root).getPropertyValue(name).trim();
    }

    // ChartBuilder may give theme colours as custom property names ("--tb-serie-1").
    function styleDataset(ds) {
        ds.tbColor = ds.tbColor || ds.borderColor;
        var color = typeof ds.tbColor === 'string' && ds.tbColor.indexOf('--') === 0 ? css(ds.tbColor) : ds.tbColor;
        ds.borderColor = ds.backgroundColor = ds.pointBackgroundColor = color;
        ds.tbOrigine = color;   // colour of the theme, for tbComparaison.surligner
        if (ds.tbColor === '--tb-serie-consigne') {
            ds.stepped = true;          // a consigne holds until the next one
            ds.borderDash = [8, 6];
            ds.pointRadius = 0;
        } else if (ds.tbColor === '--tb-serie-moyenne') {
            ds.borderWidth = 4;
        }
    }

    function styleOptions(o) {
        var mobile = matchMedia('(max-width: 640px)').matches;
        var text = css('--md-on-surface-variant');
        var grid = css('--md-outline-variant');
        var font = { family: 'Manrope, "Segoe UI", system-ui, sans-serif', size: mobile ? 11 : 13 };

        o.responsive = true;
        o.maintainAspectRatio = false;
        o.layout = { padding: { top: 4, right: 4, bottom: 0, left: 0 } };
        o.plugins = o.plugins || {};
        o.plugins.legend = {
            position: mobile ? 'bottom' : 'top', align: 'start',
            labels: { color: text, font: font, usePointStyle: true, pointStyle: 'circle',
                boxWidth: 8, boxHeight: 8, padding: mobile ? 12 : 20 }
        };
        o.plugins.tooltip = {
            mode: 'index', intersect: false, titleFont: font, bodyFont: font, padding: 12, cornerRadius: 12,
            backgroundColor: css('--md-inverse-surface'), titleColor: css('--md-inverse-on-surface'),
            bodyColor: css('--md-inverse-on-surface'), usePointStyle: true, boxPadding: 4
        };
        o.scales = o.scales || {};
        o.scales.x = {
            grid: { display: false },
            border: { color: grid },
            ticks: {
                color: text, font: font, padding: 8, maxRotation: 0, autoSkip: true, maxTicksLimit: mobile ? 4 : 8,
                callback: function (value) {
                    return (this.getLabelForValue(value) || '').split(' '); // "29/09 10:07" on two lines
                }
            }
        };
        var unite = root.getAttribute('data-unite') || '°C';
        o.scales.y = {
            grid: { color: grid, drawTicks: false },
            border: { display: false },
            ticks: { color: text, font: font, padding: 10, maxTicksLimit: mobile ? 5 : 7,
                callback: function (v) { return Number(v).toLocaleString('fr-FR', { maximumFractionDigits: 1 }) + '°'; } }
        };
        o.plugins.tooltip.callbacks = {
            label: function (ctx) {
                if (ctx.parsed.y === null || ctx.parsed.y === undefined) return null;
                return ' ' + ctx.dataset.label + ' : ' + ctx.parsed.y.toLocaleString('fr-FR', { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + ' ' + unite;
            }
        };
        // Lines draw from the left on arrival; no animation when motion is off
        o.animation = tbAnim && tbAnim.reduced ? false : { duration: 900, easing: 'easeOutQuart' };
        o.elements = {
            point: { radius: 0, hoverRadius: 6, hitRadius: 12 },
            line: { tension: .4, borderWidth: mobile ? 2.5 : 3, borderCapStyle: 'round' }
        };
        o.interaction = { mode: 'index', intersect: false };
    }

    // Phones: many curves would make a legend taller than the chart (the table below says which is which)
    function legendeCompacte(o, n) {
        if (n > 5 && matchMedia('(max-width: 640px)').matches) o.plugins.legend.display = false;
    }

    return {
        /** PrimeFaces extender of the p:lineChart models (ChartBuilder). */
        extend: function (cfg) {
            styleOptions(cfg.config.options = cfg.config.options || {});
            var series = (cfg.config.data || {}).datasets || [];
            series.forEach(styleDataset);
            legendeCompacte(cfg.config.options, series.length);
        },
        /** Redraws the charts already on the page after a theme change. */
        refresh: function () {
            if (!window.Chart || !Chart.instances) return;
            Object.keys(Chart.instances).forEach(function (id) {
                var chart = Chart.instances[id];
                // The raw configuration: chart.options is a resolved proxy that must not be rewritten.
                styleOptions(chart.config.options);
                chart.data.datasets.forEach(styleDataset);
                legendeCompacte(chart.config.options, chart.data.datasets.length);
                chart.update('none');
            });
        }
    };
})();

var tbTheme = (function () {
    var root = document.documentElement;
    var COOKIE = 'thermobox-theme';

    function current() {
        return root.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
    }

    function chosen() {
        return document.cookie.split(/;\s*/).some(function (c) { return c.indexOf(COOKIE + '=') === 0; });
    }

    function save(mode) {
        // Read by ThemeBean, so the next pages come from the server in the right theme.
        document.cookie = COOKIE + '=' + mode + '; path=' + (root.getAttribute('data-ctx') || '/')
            + '; max-age=31536000; SameSite=Lax';
    }

    /** PrimeFaces stylesheet: saga (light) or vela (dark). Resolves when the new one is ready. */
    function swapPrimeFaces(dark) {
        var link = document.querySelector('link[href*="theme.css"][href*="primefaces-"]');
        if (!link) return Promise.resolve();
        var href = link.href.replace(/primefaces-(saga|vela)/, dark ? 'primefaces-vela' : 'primefaces-saga');
        if (href === link.href) return Promise.resolve();
        return new Promise(function (resolve) {
            var next = link.cloneNode();
            next.href = href;
            next.onload = next.onerror = function () { link.remove(); resolve(); };
            link.parentNode.insertBefore(next, link.nextSibling);
            setTimeout(resolve, 1200); // a slow network must not freeze the toggle animation
        });
    }

    function apply(mode) {
        root.setAttribute('data-theme', mode);
        var meta = document.querySelector('meta[name="theme-color"]');
        if (meta) meta.content = mode === 'dark' ? '#0f141c' : '#bccde2';
        tbCharts.refresh();
        return swapPrimeFaces(mode === 'dark');
    }

    /** The new theme spreads in a circle from the button (View Transitions, when supported). */
    function toggle(button) {
        var mode = current() === 'dark' ? 'light' : 'dark';
        save(mode);
        if (!document.startViewTransition || matchMedia('(prefers-reduced-motion: reduce)').matches
            || root.getAttribute('data-motion') === 'off') {
            apply(mode);
            return;
        }
        var box = button.getBoundingClientRect();
        var x = box.left + box.width / 2, y = box.top + box.height / 2;
        var radius = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y));
        root.style.setProperty('--tb-reveal', 'circle(' + radius + 'px at ' + x + 'px ' + y + 'px)');
        root.style.setProperty('--tb-reveal-from', 'circle(0 at ' + x + 'px ' + y + 'px)');
        root.classList.add('tb-theme-switch');
        document.startViewTransition(function () { return apply(mode); })
            .finished.finally(function () { root.classList.remove('tb-theme-switch'); });
    }

    // Until the user picks a theme, follow the system one.
    if (!chosen() && window.matchMedia && matchMedia('(prefers-color-scheme: dark)').matches) apply('dark');

    return { toggle: toggle };
})();

// Name used by ChartBuilder (LineChartModel.extender).
function tbChartExtender() {
    tbCharts.extend(this.cfg);
}

// Touch screens: PrimeFaces marks a tapped button as hovered / focused until something else is
// tapped, so several buttons (24 h and 7 j, two destinations…) would look selected at once.
if (window.matchMedia && matchMedia('(hover: none)').matches) {
    document.addEventListener('touchend', function () {
        setTimeout(function () {
            document.querySelectorAll('.ui-state-hover:not(.ui-treenode-content), .ui-button.ui-state-focus')
                .forEach(function (el) { el.classList.remove('ui-state-hover', 'ui-state-focus'); });
        }, 250);
    }, { passive: true });
}

/*
 * Motion. Everything here is skipped when the system asks for reduced motion.
 * - tbAnim: entrance of the page (CSS, under html.tb-entering), only on a real page load, so the
 *   30-second polls don't replay it.
 * - tbFlip: when an AJAX update reorders the dashboard (filter, search, favourite…), each card
 *   glides from its old place to the new one, new ones fade in.
 * - tbWatch: a value that changed during a refresh pulses once.
 * - tbScroll: back on the dashboard from a salle, the page reopens where that card was.
 */
var tbAnim = (function () {
    var root = document.documentElement;
    // System setting, or animations turned off on the Préférences page
    var reduced = !window.matchMedia || matchMedia('(prefers-reduced-motion: reduce)').matches
        || root.getAttribute('data-motion') === 'off';

    // Dashboard ↔ salle: the card morphs into the page, nothing else should pop in.
    var carte = false;
    try {
        var ref = new URL(document.referrer);
        var vers = location.pathname, depuis = ref.origin === location.origin ? ref.pathname : '';
        carte = (/\/app\/salle\.xhtml$/.test(vers) && /\/app\/dashboard\.xhtml$/.test(depuis))
             || (/\/app\/dashboard\.xhtml$/.test(vers) && /\/app\/salle\.xhtml$/.test(depuis));
    } catch (e) { /* no referrer: first visit */ }

    if (!reduced) {
        root.classList.add('tb-entering');
        setTimeout(function () { root.classList.remove('tb-entering'); }, 1600);
    }

    /*
     * Work that changes the page right after it loads (curves of the dashboard…) waits for the
     * transition between pages to end: an AJAX update during the morph replaces the card being
     * morphed, and the browser then cuts the animation short.
     */
    var revelee = !('onpagereveal' in window);   // no such event: nothing to wait for
    var transition = null;
    var enAttente = [];

    function liberer() {
        if (revelee) return;
        revelee = true;
        enAttente.splice(0).forEach(apresTransition);
    }

    function apresTransition(fn) {
        if (!revelee) enAttente.push(fn);
        else if (transition) transition.then(fn);
        else fn();
    }

    // Animations off (Préférences or system): no transition between pages at all, neither when
    // leaving this page nor when arriving on the next one (the CSS @view-transition can't depend
    // on a setting, so the transition is skipped from here).
    window.addEventListener('pageswap', function (e) {
        if (e.viewTransition && tbAnim.reduced) e.viewTransition.skipTransition();
    });

    // Fired before the first paint, only by browsers with transitions between pages: Firefox
    // (no card morph) keeps the entrance.
    window.addEventListener('pagereveal', function (e) {
        if (e.viewTransition && tbAnim.reduced) e.viewTransition.skipTransition();
        if (e.viewTransition) {
            root.classList.add('tb-vt-in');
            if (carte) {
                root.classList.remove('tb-entering');
                root.classList.add('tb-carte');
            }
            transition = e.viewTransition.finished.catch(function () { /* skipped: same as finished */ })
                .then(function () { root.classList.remove('tb-vt-in'); transition = null; });
        }
        liberer();
    });
    // Safety net: a page shown without the event (old engine, restored page) must not wait forever.
    setTimeout(liberer, 1500);

    return { reduced: reduced, apresTransition: apresTransition };
})();

var tbFlip = (function () {
    var PAR_DEFAUT = '#dashForm\\:grille [data-flip]';
    var selecteur = PAR_DEFAUT;
    var avant = null, favoris = null, fantomes = null;

    return {
        /**
         * onstart: where every element is before the update. [sel]: the elements to follow
         * (dashboard cards by default; users list, versions list…).
         */
        first: function (sel) {
            if (tbAnim.reduced) return;
            selecteur = sel || PAR_DEFAUT;
            avant = {};
            favoris = {};
            fantomes = {};
            document.querySelectorAll(selecteur).forEach(function (el) {
                var id = el.getAttribute('data-flip');
                var r = el.getBoundingClientRect();
                avant[id] = r;
                favoris[id] = !!el.querySelector('.tb-fav-on');
                // Kept to fade out the ones the update removes
                if (r.bottom > 0 && r.top < innerHeight) fantomes[id] = el.cloneNode(true);
            });
        },
        /** oncomplete: from there to where they are now. */
        play: function () {
            if (!avant) return;
            var nouveaux = 0;
            var presents = {};
            document.querySelectorAll(selecteur).forEach(function (el) {
                var id = el.getAttribute('data-flip');
                presents[id] = true;
                var old = avant[id];
                var now = el.getBoundingClientRect();
                // The star just turned on spins in
                var etoile = el.querySelector('.tb-fav-on .ui-icon');
                if (etoile && favoris[id] === false) {
                    etoile.animate([{ transform: 'scale(.2) rotate(-90deg)' }, { transform: 'none' }],
                        { duration: 550, easing: 'cubic-bezier(.3,1.5,.5,1)' });
                }
                if (old) {
                    var dx = old.left - now.left, dy = old.top - now.top;
                    if (Math.abs(dx) > 1 || Math.abs(dy) > 1) {
                        el.animate([{ transform: 'translate(' + dx + 'px,' + dy + 'px)' }, { transform: 'none' }],
                            { duration: 520, easing: 'cubic-bezier(.2,.8,.2,1)' });
                    }
                } else if (now.bottom > 0 && now.top < innerHeight) {
                    el.animate([{ opacity: 0, transform: 'translateY(14px) scale(.97)' }, { opacity: 1, transform: 'none' }],
                        { duration: 380, delay: Math.min(nouveaux++, 10) * 35, easing: 'cubic-bezier(.2,.8,.2,1)', fill: 'backwards' });
                }
            });
            // Removed elements: their copy fades out where they were
            Object.keys(fantomes).forEach(function (id) {
                if (presents[id]) return;
                var r = avant[id], f = fantomes[id];
                f.removeAttribute('id');
                f.setAttribute('aria-hidden', 'true');
                f.style.cssText += ';position:fixed;margin:0;pointer-events:none;z-index:5;view-transition-name:none;'
                    + 'left:' + r.left + 'px;top:' + r.top + 'px;width:' + r.width + 'px;height:' + r.height + 'px';
                document.body.appendChild(f);
                f.animate([{ opacity: 1, transform: 'none' }, { opacity: 0, transform: 'scale(.96)' }],
                    { duration: 260, easing: 'ease-in', fill: 'forwards' }).finished.then(function () { f.remove(); });
            });
            avant = fantomes = null;
        }
    };
})();

var tbWatch = (function () {
    var avant = null;
    return {
        before: function () {
            avant = {};
            document.querySelectorAll('[data-watch]').forEach(function (el) {
                avant[el.getAttribute('data-watch')] = el.textContent.replace(/\s+/g, '');
            });
        },
        after: function () {
            if (!avant || tbAnim.reduced) return;
            document.querySelectorAll('[data-watch]').forEach(function (el) {
                var old = avant[el.getAttribute('data-watch')];
                if (old !== undefined && old !== el.textContent.replace(/\s+/g, '')) {
                    var avantNombre = tbVie.lire(old);
                    if (avantNombre) tbVie.defiler(el, avantNombre.valeur, 700);
                    el.animate([
                        { transform: 'translateY(6px)', opacity: .2, filter: 'blur(3px)' },
                        { transform: 'none', opacity: 1, filter: 'none' }
                    ], { duration: 650, easing: 'cubic-bezier(.2,.8,.2,1)' });
                }
            });
            avant = null;
        }
    };
})();

var tbScroll = (function () {
    var KEY = 'thermobox-dashboard-card';

    // Clicking a card: remember where it was on screen.
    document.addEventListener('click', function (e) {
        var lien = e.target.closest && e.target.closest('.tb-room .tb-stretched');
        if (!lien) return;
        var carte = lien.closest('.tb-room');
        try {
            sessionStorage.setItem(KEY, JSON.stringify({ id: carte.getAttribute('data-flip'), top: carte.getBoundingClientRect().top }));
        } catch (err) { /* private browsing: the card is then centred */ }
    }, true);

    return {
        /** Called at the end of the dashboard, before its first paint. */
        restore: function () {
            var id = null, top = null;
            try {
                var ref = new URL(document.referrer);
                if (ref.origin === location.origin && /\/app\/salle\.xhtml$/.test(ref.pathname)) id = ref.searchParams.get('id');
            } catch (err) { return; }
            var carte = id && document.querySelector('.tb-room[data-flip="' + id.replace(/\D/g, '') + '"]');
            if (!carte) return;
            try {
                var saved = JSON.parse(sessionStorage.getItem(KEY) || 'null');
                if (saved && saved.id === id) top = saved.top;
            } catch (err) { /* centred */ }
            if (top === null) top = (innerHeight - carte.offsetHeight) / 2;
            var placer = function () {
                window.scrollTo(0, Math.max(0, carte.getBoundingClientRect().top + window.scrollY - top));
            };
            placer();
            carte.classList.add('tb-return');
            // The text above may still change height when the web font arrives.
            if (document.fonts && document.fonts.status !== 'loaded') document.fonts.ready.then(placer);
        }
    };
})();

/*
 * Bottom sheets (p:dialog.tb-sheet): closed by a tap outside or by a swipe down (#28).
 * PrimeFaces' dismissibleMask alone misses taps on iOS, and has no swipe.
 */
var tbSheet = (function () {
    var CLOSE_AT = 80; // px dragged down

    function opened() {
        var widgets = (window.PrimeFaces && PrimeFaces.widgets) || {};
        return Object.keys(widgets).map(function (k) { return widgets[k]; }).filter(function (w) {
            return w && w.jq && w.jq.hasClass('tb-sheet') && w.isVisible && w.isVisible();
        });
    }

    function close(sheet, widget) {
        sheet.style.transition = 'transform .2s ease-in';
        sheet.style.transform = 'translateY(100%)';
        setTimeout(function () {
            widget.hide();
            sheet.style.transition = sheet.style.transform = '';
        }, 200);
    }

    // Only the sheet's own mask ("<id>_modal"): a p:confirm opened over it keeps the sheet.
    document.addEventListener('click', function (e) {
        if (!e.target.classList || !e.target.classList.contains('ui-widget-overlay')) return;
        opened().forEach(function (w) { if (e.target.id === w.id + '_modal') w.hide(); });
    });

    var drag = null;
    document.addEventListener('touchstart', function (e) {
        var sheet = e.target.closest && e.target.closest('.ui-dialog.tb-sheet');
        var content = sheet && sheet.querySelector('.ui-dialog-content');
        // A list scrolled down scrolls back first; the swipe only starts from its top.
        if (!sheet || e.touches.length > 1 || (content && content.scrollTop > 0)) return;
        drag = { sheet: sheet, y: e.touches[0].clientY, dy: 0, t: Date.now() };
    }, { passive: true });

    document.addEventListener('touchmove', function (e) {
        if (!drag) return;
        drag.dy = Math.max(0, e.touches[0].clientY - drag.y);
        if (drag.dy < 6) return;
        e.preventDefault(); // the page behind must not scroll
        drag.sheet.style.transition = 'none';
        drag.sheet.style.transform = 'translateY(' + drag.dy + 'px)';
    }, { passive: false });

    document.addEventListener('touchend', function () {
        if (!drag) return;
        var d = drag;
        drag = null;
        var fast = d.dy > 30 && d.dy / (Date.now() - d.t) > .6; // a quick flick also closes
        var widget = opened().filter(function (w) { return w.jq[0] === d.sheet; })[0];
        if (widget && (d.dy > CLOSE_AT || fast)) {
            close(d.sheet, widget);
        } else {
            d.sheet.style.transition = 'transform .25s ease-out';
            d.sheet.style.transform = '';
        }
    });

    return { opened: opened };
})();

/* Dashboard sections folded from their title (#27). DashboardBean.replie reads the same cookie. */
var tbSections = (function () {
    var COOKIE = 'thermobox-sections';
    var root = document.documentElement;

    function replies() {
        var m = document.cookie.match(/(?:^|;\s*)thermobox-sections=([^;]*)/);
        return m && m[1] ? m[1].split('.') : [];
    }

    function save(keys) {
        document.cookie = COOKIE + '=' + keys.join('.') + '; path=' + (root.getAttribute('data-ctx') || '/')
            + '; max-age=' + (keys.length ? 31536000 : 0) + '; SameSite=Lax';
    }

    return {
        toggle: function (button) {
            var section = button.closest('[data-section]');
            var key = section.getAttribute('data-section');
            var fold = !section.classList.contains('tb-collapsed');
            section.classList.add('tb-section-anim');
            section.classList.toggle('tb-collapsed', fold);
            button.setAttribute('aria-expanded', String(!fold));
            save(replies().filter(function (k) { return k && k !== key; }).concat(fold ? [key] : []));
            clearTimeout(section.tbTimer);
            section.tbTimer = setTimeout(function () { section.classList.remove('tb-section-anim'); }, 700);
        }
    };
})();

/*
 * Updates page: when the interface redeploys itself on a new version, the poll fails for a few
 * seconds. Wait until the page answers again, then reload it (the view of the old version is gone).
 */
var tbMaj = (function () {
    var attente = false;
    return {
        attendre: function () {
            if (attente) return;
            attente = true;
            var essai = function () {
                fetch(location.href, { cache: 'no-store', credentials: 'same-origin' })
                    .then(function (r) { if (r.ok) location.reload(); else setTimeout(essai, 3000); })
                    .catch(function () { setTimeout(essai, 3000); });
            };
            setTimeout(essai, 8000);
        }
    };
})();

/* Préférences page: cookies read by PreferencesBean, applied at once without reloading. */
var tbPrefs = (function () {
    var root = document.documentElement;

    function cookie(nom, valeur) {
        document.cookie = nom + '=' + valeur + '; path=' + (root.getAttribute('data-ctx') || '/')
            + '; max-age=' + (valeur ? 31536000 : 0) + '; SameSite=Lax';
    }

    return {
        animations: function (on) {
            cookie('thermobox-animations', on ? '' : 'off');
            root.setAttribute('data-motion', on ? 'on' : 'off');
            tbAnim.reduced = !on || matchMedia('(prefers-reduced-motion: reduce)').matches;
        },
        /** Page tint following the state shown (stable salle → green…). */
        teinte: function (on) {
            cookie('thermobox-teinte', on ? '' : 'off');
            root.setAttribute('data-teinte', on ? 'on' : 'off');
        },
        /** °C or °F: the values come from the server, so the page reloads. */
        unite: function (fahrenheit) {
            cookie('thermobox-unite', fahrenheit ? 'F' : '');
            location.reload();
        },
        /** Look chosen by an ADMIN for everyone (saved on the server): applied here at once. */
        style: function (args) {
            if (!args || !args.style) return;
            var appliquer = function () { root.setAttribute('data-style', args.style); tbCharts.refresh(); };
            if (document.startViewTransition && !tbAnim.reduced) document.startViewTransition(appliquer);
            else appliquer();
        },
        /** Shows the note of [id] when the system itself asks for less motion. */
        signalerSysteme: function (id) {
            var note = document.getElementById(id);
            if (note && window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches) note.hidden = false;
        }
    };
})();

/*
 * Températures page: the chart of every salle. A colour of the table hides or shows its curve,
 * hovering a row brings its curve forward, and the points can be saved as a CSV file.
 */
var tbComparaison = (function () {
    function graphique() {
        var canvas = document.querySelector('.tb-compare-card canvas');
        return canvas && window.Chart && Chart.getChart ? Chart.getChart(canvas) : null;
    }

    // "#2160cc" → "#2160cc33": the other curves fade behind the one pointed at
    function attenuer(couleur) {
        return /^#[0-9a-f]{6}$/i.test(couleur) ? couleur + '30' : couleur;
    }

    return {
        basculer: function (bouton, i) {
            var g = graphique();
            if (!g) return;
            var visible = !g.isDatasetVisible(i);
            g.setDatasetVisibility(i, visible);
            g.update();
            bouton.classList.toggle('tb-off', !visible);
            bouton.setAttribute('aria-pressed', String(visible));
        },
        /** [i] in front, the others faded; -1: all back to normal. */
        surligner: function (i) {
            var g = graphique();
            if (!g) return;
            g.data.datasets.forEach(function (ds, j) {
                ds.tbOrigine = ds.tbOrigine || ds.borderColor;
                var couleur = i < 0 || i === j ? ds.tbOrigine : attenuer(ds.tbOrigine);
                ds.borderColor = ds.pointBackgroundColor = couleur;
                ds.borderWidth = i === j ? 4 : undefined;
            });
            g.update(tbAnim.reduced ? 'none' : undefined);
        },
        /** Points of the chart: one line per moment, one column per curve (French spreadsheet format). */
        exporter: function () {
            var g = graphique();
            if (!g) return;
            var unite = document.documentElement.getAttribute('data-unite') || '°C';
            var cellule = function (t) { return '"' + String(t).replace(/"/g, '""') + '"'; };
            var lignes = [['Moment'].concat(g.data.datasets.map(function (ds) { return ds.label + ' (' + unite + ')'; })).map(cellule).join(';')];
            g.data.labels.forEach(function (label, k) {
                lignes.push([cellule(label)].concat(g.data.datasets.map(function (ds) {
                    var v = ds.data[k];
                    return v === null || v === undefined ? '' : String(v).replace('.', ',');
                })).join(';'));
            });
            var lien = document.createElement('a');
            lien.href = URL.createObjectURL(new Blob(['\ufeff' + lignes.join('\r\n')], { type: 'text/csv;charset=utf-8' }));
            lien.download = 'thermobox-temperatures.csv';
            document.body.appendChild(lien);
            lien.click();
            setTimeout(function () { URL.revokeObjectURL(lien.href); lien.remove(); }, 1000);
        }
    };
})();

/*
 * More motion, all of it skipped when animations are off (Préférences) or the system asks for
 * less motion (tbAnim.reduced, read at the time of each effect):
 * - numbers roll up to their value on arrival, and from the old to the new one when a refresh
 *   changes them;
 * - what is below the screen rises in when scrolled to;
 * - cards follow the pointer (light and a slight tilt), presses leave a ripple;
 * - lists (users, salles, versions, comparison table) come in one item after the other;
 * - a thin bar at the top shows that the server is working (AJAX), and the button that asked spins.
 */
var tbVie = (function () {
    var root = document.documentElement;
    var NOMBRES = '.tb-temp-xl, .tb-temp-hero, .tb-temp-l';
    var REVELES = '.tb-section, .tb-card, .tb-stat, .tb-user, .tb-drill-item, .tb-box, .tb-room, .tb-compare-table, .ui-card';
    var LISTES = '.tb-users, .tb-drill-list, .tb-maj-versions, .tb-boxes, .tb-compare-table tbody, .tb-histo, .tb-stats';
    var calme = function () { return tbAnim.reduced; };
    var survol = window.matchMedia && matchMedia('(hover: hover) and (pointer: fine)').matches;

    // ---- numbers: the text node before the unit ("21,4" in <span>21,4<span class="tb-deg">°C</span></span>)
    function texteNombre(el) {
        for (var n = el.firstChild; n; n = n.nextSibling) {
            if (n.nodeType === 3 && /\d/.test(n.nodeValue)) return n;
        }
        return null;
    }

    function lire(t) {
        var m = /(-?\d+(?:,\d+)?)/.exec(t);
        return m ? { valeur: parseFloat(m[1].replace(',', '.')), decimales: (m[1].split(',')[1] || '').length, avant: t.slice(0, m.index), apres: t.slice(m.index + m[1].length) } : null;
    }

    /** Rolls the number of [el] from [depuis] to its current value. */
    function defiler(el, depuis, duree) {
        var noeud = texteNombre(el);
        var cible = noeud && lire(noeud.nodeValue);
        if (!cible || depuis === cible.valeur || calme() || sansDefilement) return;
        var debut = performance.now();
        var ecrire = function (v) {
            noeud.nodeValue = cible.avant + v.toLocaleString('fr-FR', { minimumFractionDigits: cible.decimales, maximumFractionDigits: cible.decimales }) + cible.apres;
        };
        (function pas(maintenant) {
            var t = Math.min(1, (maintenant - debut) / duree);
            var e = 1 - Math.pow(1 - t, 4);
            ecrire(depuis + (cible.valeur - depuis) * e);
            if (t < 1) requestAnimationFrame(pas); else ecrire(cible.valeur);
        })(debut);
    }

    // Not on the dashboard nor on a salle: their numbers are the main reading, they stay still
    var sansDefilement = /\/app\/(dashboard|salle)\.xhtml$/.test(location.pathname);

    function arrivee() {
        if (sansDefilement || root.classList.contains('tb-carte')) return;
        document.querySelectorAll(NOMBRES).forEach(function (el, i) {
            var noeud = texteNombre(el), v = noeud && lire(noeud.nodeValue);
            if (!v) return;
            var ecart = Math.abs(v.valeur) < 10 && v.decimales === 0 ? v.valeur : Math.min(3, Math.abs(v.valeur));
            setTimeout(function () { defiler(el, v.valeur - ecart, 900); }, 120 + Math.min(i, 12) * 45);
        });
    }

    // ---- items below the screen rise in when reached
    var observateur = 'IntersectionObserver' in window ? new IntersectionObserver(function (entrees) {
        entrees.forEach(function (e) {
            if (!e.isIntersecting) return;
            e.target.classList.add('tb-reveal-in');
            observateur.unobserve(e.target);
        });
    }, { rootMargin: '0px 0px -8% 0px' }) : null;

    function reveler(racine) {
        if (!observateur || calme()) return;
        (racine || document).querySelectorAll(REVELES).forEach(function (el) {
            if (el.classList.contains('tb-reveal') || el.closest('.ui-dialog')) return;
            var r = el.getBoundingClientRect();
            if (r.top < innerHeight) return; // already on screen: the page entrance handles it
            el.classList.add('tb-reveal');
            observateur.observe(el);
        });
    }

    // ---- lists one item after the other (CSS uses --i)
    function cascade(racine) {
        (racine || document).querySelectorAll(LISTES).forEach(function (liste) {
            Array.prototype.forEach.call(liste.children, function (el, i) { el.style.setProperty('--i', Math.min(i, 14)); });
        });
    }

    // ---- light and tilt following the pointer
    if (survol) {
        document.addEventListener('pointermove', function (e) {
            if (calme() || !e.target.closest) return;
            var carte = e.target.closest('.tb-room, .tb-box, .tb-stat, .tb-maj-version-card');
            if (!carte) return;
            var r = carte.getBoundingClientRect();
            var x = (e.clientX - r.left) / r.width, y = (e.clientY - r.top) / r.height;
            carte.style.setProperty('--mx', (x * 100).toFixed(1) + '%');
            carte.style.setProperty('--my', (y * 100).toFixed(1) + '%');
            carte.classList.add('tb-suivi');
        }, { passive: true });
        // Opening a salle: the card morphs into the page, it must leave straight
        document.addEventListener('click', function (e) {
            var carte = e.target.closest && e.target.closest('.tb-suivi');
            if (carte) carte.classList.remove('tb-suivi');
        }, true);
        document.addEventListener('pointerout', function (e) {
            var carte = e.target.closest && e.target.closest('.tb-suivi');
            if (carte && !carte.contains(e.relatedTarget)) carte.classList.remove('tb-suivi');
        }, { passive: true });
    }

    // ---- ripple where a button is pressed
    document.addEventListener('pointerdown', function (e) {
        if (calme() || e.button !== 0 || !e.target.closest) return;
        var el = e.target.closest('.ui-button, .tb-dest, .tb-summary-chip, .tb-section-toggle, .tb-icon-btn, .tb-drill-item > a, .tb-raw > summary');
        if (!el || el.classList.contains('ui-state-disabled')) return;
        var r = el.getBoundingClientRect();
        var cadre = document.createElement('span');
        cadre.className = 'tb-ripple-cadre';
        var onde = document.createElement('span');
        var taille = Math.max(r.width, r.height) * 2.2;
        onde.className = 'tb-ripple';
        onde.style.cssText = 'width:' + taille + 'px;height:' + taille + 'px;left:' + (e.clientX - r.left - taille / 2) + 'px;top:' + (e.clientY - r.top - taille / 2) + 'px';
        cadre.appendChild(onde);
        if (getComputedStyle(el).position === 'static') el.style.position = 'relative';
        el.appendChild(cadre);
        onde.animate([{ transform: 'scale(0)', opacity: .35 }, { transform: 'scale(1)', opacity: 0 }],
            { duration: 650, easing: 'cubic-bezier(.2,.8,.2,1)' }).finished.then(function () { cadre.remove(); });
    }, { passive: true });

    // ---- server at work: thin bar at the top (after 150 ms, quick answers show nothing), spinning button
    var barre = null, enCours = 0, minuterie = null;
    function barreHaut() {
        if (!barre) {
            barre = document.createElement('div');
            barre.className = 'tb-busy-bar';
            barre.setAttribute('aria-hidden', 'true');
            document.body.appendChild(barre);
        }
        return barre;
    }
    if (window.jQuery) {
        jQuery(document).on('pfAjaxSend', function (e, xhr, settings) {
            enCours++;
            // PrimeFaces keeps the request's settings on the xhr (pfSettings.source: id or element)
            var cfg = (xhr && xhr.pfSettings) || settings || {};
            var source = cfg.source && (typeof cfg.source === 'string' ? document.getElementById(cfg.source) : cfg.source);
            if (source && source.classList && source.classList.contains('tb-spin-on-busy')) source.classList.add('tb-busy');
            clearTimeout(minuterie);
            minuterie = setTimeout(function () { if (enCours > 0) barreHaut().classList.add('tb-busy-on'); }, 150);
        });
        jQuery(document).on('pfAjaxComplete', function () {
            enCours = Math.max(0, enCours - 1);
            document.querySelectorAll('.tb-busy').forEach(function (b) { b.classList.remove('tb-busy'); });
            if (enCours === 0) {
                clearTimeout(minuterie);
                if (barre) barre.classList.remove('tb-busy-on');
            }
            // New content: same effects as on arrival (not the number roll: tbWatch handles changes)
            cascade();
            reveler();
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        cascade();
        if (calme()) return;
        arrivee();
        // After the entrance, so what is on screen doesn't wait for scrolling
        setTimeout(function () { reveler(); }, 50);
    });

    return { defiler: defiler, lire: lire, texteNombre: texteNombre };
})();

/*
 * <details> (raw history, release notes…): opens and closes by sliding, at the pace of the
 * dashboard sections, instead of jumping.
 */
var tbPlis = (function () {
    var DUREE = 600, COURBE = 'cubic-bezier(.2,.8,.2,1)';

    function animer(details, ouvrir) {
        var resume = details.querySelector(':scope > summary');
        var ferme = resume.offsetHeight + parseFloat(getComputedStyle(details).paddingTop) + parseFloat(getComputedStyle(details).paddingBottom)
            + parseFloat(getComputedStyle(details).borderTopWidth) + parseFloat(getComputedStyle(details).borderBottomWidth);
        if (details.tbAnim) details.tbAnim.cancel();
        if (ouvrir) details.open = true;
        var plein = details.offsetHeight;
        details.style.overflow = 'hidden';
        var a = details.animate([{ height: (ouvrir ? ferme : plein) + 'px' }, { height: (ouvrir ? plein : ferme) + 'px' }],
            { duration: DUREE, easing: COURBE });
        details.tbAnim = a;
        // The content fades in after the panel starts to open
        Array.prototype.forEach.call(details.children, function (el) {
            if (el === resume) return;
            el.animate(ouvrir ? [{ opacity: 0, transform: 'translateY(-6px)' }, { opacity: 1, transform: 'none' }]
                : [{ opacity: 1 }, { opacity: 0 }], { duration: ouvrir ? DUREE : DUREE / 2, delay: ouvrir ? 120 : 0, easing: COURBE, fill: 'both' })
                .finished.then(function (x) { x.cancel(); }, function () {});
        });
        a.finished.then(function () {
            if (!ouvrir) details.open = false;
            details.style.overflow = '';
            details.tbAnim = null;
        }, function () { /* cancelled by the next click */ });
    }

    document.addEventListener('click', function (e) {
        var resume = e.target.closest && e.target.closest('details > summary');
        if (!resume || tbAnim.reduced) return;
        var details = resume.parentElement;
        e.preventDefault();
        // State asked by the last click (a click during the animation reverses it)
        var ouvrir = !(details.tbOuvert === undefined ? details.open : details.tbOuvert);
        details.tbOuvert = ouvrir;
        animer(details, ouvrir);
    });

    return { animer: animer };
})();

/*
 * Tree of salles.xhtml: a click anywhere on a row (not on its buttons) opens or closes it, and its
 * content slides open or shut instead of appearing at once. PrimeFaces still does the toggling
 * (state, icons); for a close, it is asked once the slide is over.
 */
var tbArbre = (function () {
    var DUREE = 450, COURBE = 'cubic-bezier(.2,.8,.2,1)';
    var FERME = 'ui-icon-triangle-1-e';   // PrimeFaces' collapsed icon class

    function glisser(enfants, ouvrir) {
        var h = enfants.scrollHeight;
        enfants.style.overflow = 'hidden';
        var a = enfants.animate([{ height: (ouvrir ? 0 : h) + 'px', opacity: ouvrir ? 0 : 1 },
            { height: (ouvrir ? h : 0) + 'px', opacity: ouvrir ? 1 : 0 }], { duration: DUREE, easing: COURBE, fill: ouvrir ? 'none' : 'forwards' });
        if (ouvrir) {
            // The rows come in one after the other
            Array.prototype.forEach.call(enfants.querySelectorAll(':scope > li.ui-treenode'), function (li, i) {
                li.animate([{ opacity: 0, transform: 'translateX(-10px)' }, { opacity: 1, transform: 'none' }],
                    { duration: 380, delay: 60 + Math.min(i, 10) * 35, easing: COURBE, fill: 'backwards' });
            });
        }
        return a;
    }

    document.addEventListener('click', function (e) {
        var t = e.target;
        if (!t.closest || !t.closest('.ui-tree.tb-tree')) return;
        var bascule = t.closest('.ui-tree-toggler');
        if (!bascule) {
            // A row: its toggler does the work (buttons, links and fields keep their own click)
            var ligne = t.closest('.ui-treenode-content');
            if (!ligne || t.closest('a, button, input, .ui-button, .ui-chkbox, .tb-node-actions')) return;
            var b = ligne.querySelector(':scope > .ui-tree-toggler');
            if (b) b.click();
            return;
        }
        if (bascule.tbLaisser || tbAnim.reduced) return;
        var enfants = bascule.closest('li.ui-treenode').querySelector(':scope > .ui-treenode-children');
        if (!enfants || enfants.tbAnim) return;
        if (bascule.classList.contains(FERME)) {
            // Opening: PrimeFaces shows the content during this click, then it slides open
            setTimeout(function () {
                var a = enfants.tbAnim = glisser(enfants, true);
                a.finished.finally(function () { enfants.style.overflow = ''; enfants.tbAnim = null; });
            });
            return;
        }
        // Closing: slide shut first, then let PrimeFaces hide it
        e.stopPropagation();
        var a = enfants.tbAnim = glisser(enfants, false);
        a.finished.then(function () {
            bascule.tbLaisser = true;
            bascule.click();
            bascule.tbLaisser = false;
            a.cancel();
            enfants.style.overflow = '';
            enfants.tbAnim = null;
        });
    }, true);

    return {};
})();

/*
 * Keyboard shortcuts. Never while typing in a field, nor with Ctrl / Alt / Cmd (the browser's
 * own shortcuts stay). The list below is the only one: the "?" panel and the Préférences page are
 * built from it. Turned off from the Préférences page (cookie thermobox-clavier=off).
 */
var tbClavier = (function () {
    var root = document.documentElement;
    var COOKIE = 'thermobox-clavier';

    function actif() {
        return !/(?:^|;\s*)thermobox-clavier=off/.test(document.cookie);
    }

    function page(nom) {
        return new RegExp('/app/' + nom + '\\.xhtml$|/admin/' + nom + '\\.xhtml$').test(location.pathname);
    }

    function cliquer(sel) {
        var el = document.querySelector(sel);
        if (!el || el.disabled || el.classList.contains('ui-state-disabled')) return false;
        el.click();
        return true;
    }

    // Destination n of the navigation, as shown (Aperçu, Salles, Mesures, Gérer, Utilisateurs…)
    function destination(n) {
        var liens = document.querySelectorAll('.tb-rail .tb-dests a.tb-dest');
        if (!liens[n - 1]) return false;
        liens[n - 1].click();
        return true;
    }

    function allerA(chemin) {
        location.href = (root.getAttribute('data-ctx') || '/') + chemin;
        return true;
    }

    var RACCOURCIS = [
        { groupe: 'Partout', touches: ['1', '…', '6'], texte: 'Ouvrir la 1ʳᵉ à la 6ᵉ entrée du menu (Aperçu, Salles, Mesures…)',
          cle: /^[1-6]$/, agir: function (e) { return destination(+e.key); } },
        { groupe: 'Partout', touches: ['/'], texte: 'Aller au champ de recherche de la page',
          cle: '/', agir: function () {
              var champ = document.querySelector('.tb-search input, input[type=search]');
              if (!champ) return false;
              champ.focus();
              champ.select();
              return true;
          } },
        { groupe: 'Partout', touches: ['R'], texte: 'Actualiser les données de la page',
          cle: 'r', agir: function () { return cliquer('.tb-page-head .ui-button[title="Actualiser"]'); } },
        { groupe: 'Partout', touches: ['N'], texte: 'Action principale de la page (nouvel élément, nouvel utilisateur…)',
          cle: 'n', agir: function () { return cliquer('.tb-page-head .tb-fab'); } },
        { groupe: 'Partout', touches: ['T'], texte: 'Passer du thème clair au thème sombre',
          cle: 't', agir: function () { return cliquer('.tb-theme-btn'); } },
        { groupe: 'Partout', touches: ['P'], texte: 'Ouvrir les Préférences',
          cle: 'p', agir: function () { return page('preferences') || allerA('app/preferences.xhtml'); } },
        { groupe: 'Partout', touches: ['?'], texte: 'Afficher ou cacher cette liste',
          cle: '?', agir: function () { basculerAide(); return true; } },
        { groupe: 'Partout', touches: ['Échap'], texte: 'Fermer cette liste', cle: null },
        { groupe: 'Page d\'une salle', touches: ['Espace'], texte: 'Revenir à l\'aperçu',
          cle: ' ', si: function () { return page('salle'); },
          agir: function () {
              // Same way back as the arrow: from the dashboard, the card returns to its place
              try {
                  var ref = new URL(document.referrer);
                  if (ref.origin === location.origin && /\/app\/dashboard\.xhtml$/.test(ref.pathname)) { history.back(); return true; }
              } catch (err) { /* no referrer */ }
              return cliquer('a.tb-back, .tb-back a') || allerA('app/dashboard.xhtml');
          } },
        { groupe: 'Page d\'une salle', touches: ['Retour arrière'], texte: 'Revenir à l\'aperçu (comme Espace)',
          cle: 'Backspace', si: function () { return page('salle'); },
          agir: function () { return cliquer('a.tb-back, .tb-back a') || allerA('app/dashboard.xhtml'); } },
        { groupe: 'Page d\'une salle', touches: ['F'], texte: 'Ajouter aux favoris ou en retirer',
          cle: 'f', si: function () { return page('salle'); }, agir: function () { return cliquer('.tb-page-head .tb-fav'); } },
        { groupe: 'Aperçu', touches: ['F'], texte: 'Replier ou déplier toutes les sections sauf les favoris',
          cle: 'f', si: function () { return page('dashboard'); },
          agir: function () {
              var sections = document.querySelectorAll('.tb-section[data-section]:not([data-section="favoris"]) .tb-section-toggle');
              if (!sections.length) return false;
              sections.forEach(function (b) { b.click(); });
              return true;
          } }
    ];

    function correspond(r, e) {
        if (!r.cle || (r.si && !r.si())) return false;
        return r.cle instanceof RegExp ? r.cle.test(e.key) : r.cle === e.key.toLowerCase() || r.cle === e.key;
    }

    function enSaisie(el) {
        return el && (el.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName)
            || (el.closest && el.closest('.ui-selectonemenu-panel, .ui-selectcheckboxmenu-panel, .ui-datepicker')));
    }

    // ---- "?" panel
    var aide = null;
    function tableau() {
        var groupes = {};
        RACCOURCIS.forEach(function (r) { (groupes[r.groupe] = groupes[r.groupe] || []).push(r); });
        return Object.keys(groupes).map(function (g) {
            return '<section class="tb-raccourcis-groupe"><h3>' + g + '</h3><dl>' + groupes[g].map(function (r) {
                return '<div class="tb-raccourci"><dt>' + r.touches.map(function (t) { return t === '…' ? '…' : '<kbd>' + t + '</kbd>'; }).join(' ')
                    + '</dt><dd>' + r.texte + '</dd></div>';
            }).join('') + '</dl></section>';
        }).join('');
    }

    function basculerAide() {
        if (aide) { fermerAide(); return; }
        aide = document.createElement('div');
        aide.className = 'tb-aide-clavier';
        aide.setAttribute('role', 'dialog');
        aide.setAttribute('aria-label', 'Raccourcis clavier');
        aide.innerHTML = '<div class="tb-aide-boite"><div class="tb-aide-tete"><strong>Raccourcis clavier</strong>'
            + '<button type="button" class="tb-icon-btn" aria-label="Fermer"><i class="pi pi-times"></i></button></div>'
            + '<div class="tb-raccourcis">' + tableau() + '</div></div>';
        aide.addEventListener('click', function (e) {
            if (e.target === aide || (e.target.closest && e.target.closest('.tb-aide-tete button'))) fermerAide();
        });
        document.body.appendChild(aide);
        aide.querySelector('button').focus();
    }

    function fermerAide() {
        if (!aide) return;
        aide.remove();
        aide = null;
    }

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && aide) { fermerAide(); e.preventDefault(); return; }
        if (!actif() || e.ctrlKey || e.metaKey || e.altKey || e.defaultPrevented || enSaisie(e.target)) return;
        // A dialog of the page is open: only "?" and Échap
        var dialogue = document.querySelector('.ui-dialog[aria-hidden="false"], .ui-dialog.ui-overlay-visible');
        // A focused button or link keeps Space / Enter for itself
        if (e.key === ' ' && e.target.closest && e.target.closest('a, button, .ui-button, [role=button], [tabindex]:not(body)')) return;
        for (var i = 0; i < RACCOURCIS.length; i++) {
            var r = RACCOURCIS[i];
            if (!correspond(r, e) || (dialogue && r.cle !== '?')) continue;
            if (r.agir(e)) e.preventDefault();
            return;
        }
    });

    return {
        /** Préférences page: the list in [id]. */
        lister: function (id) {
            var el = document.getElementById(id);
            if (el) el.innerHTML = tableau();
        },
        activer: function (on) {
            document.cookie = COOKIE + '=' + (on ? '' : 'off') + '; path=' + (root.getAttribute('data-ctx') || '/')
                + '; max-age=' + (on ? 0 : 31536000) + '; SameSite=Lax';
        },
        actif: actif
    };
})();

/* Phones held sideways: the theme / preferences / profile / logout panel of the rail ("…"). */
var tbRail = (function () {
    function fermer() {
        var rail = document.querySelector('.tb-rail.tb-rail-open');
        if (!rail) return;
        rail.classList.remove('tb-rail-open');
        var b = rail.querySelector('.tb-rail-more');
        if (b) b.setAttribute('aria-expanded', 'false');
    }
    document.addEventListener('click', function (e) {
        if (!e.target.closest || !e.target.closest('.tb-rail-end')) fermer();
    });
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape') fermer(); });
    return {
        basculer: function (bouton) {
            var rail = bouton.closest('.tb-rail');
            var ouvert = rail.classList.toggle('tb-rail-open');
            bouton.setAttribute('aria-expanded', String(ouvert));
        }
    };
})();
