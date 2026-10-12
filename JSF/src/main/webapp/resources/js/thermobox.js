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
        var font = { family: '"Roboto Flex", Roboto, system-ui, sans-serif', size: mobile ? 11 : 13 };

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
        if (meta) meta.content = mode === 'dark' ? '#111318' : '#f9f9ff';
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
