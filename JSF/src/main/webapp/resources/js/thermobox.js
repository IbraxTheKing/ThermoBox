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
        o.scales.y = {
            grid: { color: grid, drawTicks: false },
            border: { display: false },
            ticks: { color: text, font: font, padding: 10, maxTicksLimit: mobile ? 5 : 7 }
        };
        o.elements = {
            point: { radius: 0, hoverRadius: 6, hitRadius: 12 },
            line: { tension: .4, borderWidth: mobile ? 2.5 : 3, borderCapStyle: 'round' }
        };
        o.interaction = { mode: 'index', intersect: false };
    }

    return {
        /** PrimeFaces extender of the p:lineChart models (ChartBuilder). */
        extend: function (cfg) {
            styleOptions(cfg.config.options = cfg.config.options || {});
            ((cfg.config.data || {}).datasets || []).forEach(styleDataset);
        },
        /** Redraws the charts already on the page after a theme change. */
        refresh: function () {
            if (!window.Chart || !Chart.instances) return;
            Object.keys(Chart.instances).forEach(function (id) {
                var chart = Chart.instances[id];
                // The raw configuration: chart.options is a resolved proxy that must not be rewritten.
                styleOptions(chart.config.options);
                chart.data.datasets.forEach(styleDataset);
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
        if (!document.startViewTransition || matchMedia('(prefers-reduced-motion: reduce)').matches) {
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
    var reduced = !window.matchMedia || matchMedia('(prefers-reduced-motion: reduce)').matches;

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

    // Fired before the first paint, only by browsers with transitions between pages: Firefox
    // (no card morph) keeps the entrance.
    window.addEventListener('pagereveal', function (e) {
        if (!e.viewTransition) return;
        root.classList.add('tb-vt-in');
        if (carte) {
            root.classList.remove('tb-entering');
            root.classList.add('tb-carte');
        }
        e.viewTransition.finished.finally(function () { root.classList.remove('tb-vt-in'); });
    });

    return { reduced: reduced };
})();

var tbFlip = (function () {
    var SELECTOR = '#dashForm\\:grille [data-flip]';
    var avant = null, favoris = null;

    return {
        /** onstart: where every card is before the update. */
        first: function () {
            if (tbAnim.reduced) return;
            avant = {};
            favoris = {};
            document.querySelectorAll(SELECTOR).forEach(function (el) {
                var id = el.getAttribute('data-flip');
                avant[id] = el.getBoundingClientRect();
                favoris[id] = !!el.querySelector('.tb-fav-on');
            });
        },
        /** oncomplete: from there to where they are now. */
        play: function () {
            if (!avant) return;
            var nouveaux = 0;
            document.querySelectorAll(SELECTOR).forEach(function (el) {
                var id = el.getAttribute('data-flip');
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
            avant = null;
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
            section.tbTimer = setTimeout(function () { section.classList.remove('tb-section-anim'); }, 400);
        }
    };
})();
