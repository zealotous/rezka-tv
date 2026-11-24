/*
 * Rezka TV — injected cosmetic filter for the WebView fallback.
 * Network blocking is done in WebActivity.shouldInterceptRequest(); this
 * script removes the leftovers (ad containers, age gates, popunders).
 */
(function () {
    if (window.__rztvInjected) {
        return;
    }
    window.__rztvInjected = true;

    var css = [
        'iframe[src*="vast"]',
        'iframe[src*="doubleclick"]',
        'iframe[src*="adfox"]',
        'iframe[src*="adriver"]',
        'iframe[src*="franecki"]',
        'iframe[src*="voidboost"]',
        'iframe[src*="trafficbass"]',
        '.ads', '.adsbygoogle', '.advert', '.advertisement', '.ad-container',
        '.preroll', '.midroll', '.popunder', '.interstitial', '.overlay-ad',
        '.banner-ad', '.ad-banner', '[id^="ad-"]', '[id^="ads-"]',
        '[class^="ad-"]', '[class*=" ad-"]', '[data-ad]', '.ya-ad',
        '.modal-backdrop.show'
    ].join(',') + '{display:none!important;visibility:hidden!important;height:0!important}';

    var style = document.createElement('style');
    style.type = 'text/css';
    style.appendChild(document.createTextNode(css));
    (document.head || document.documentElement).appendChild(style);

    /* Kill popunders and target=_blank windows. */
    try {
        window.open = function () { return null; };
    } catch (e) { }
    document.addEventListener('click', function (e) {
        var a = e.target && e.target.closest ? e.target.closest('a[target="_blank"]') : null;
        if (a) {
            e.preventDefault();
            e.stopPropagation();
        }
    }, true);

    /* Dismiss the 18+ age gate automatically. */
    function dismissAgeGate() {
        var nodes = document.querySelectorAll('button, a, .btn');
        for (var i = 0; i < nodes.length; i++) {
            var text = (nodes[i].textContent || '').trim().toLowerCase();
            if (text === 'мне 18' || text === 'да, мне 18' || text === '18+' ||
                text === 'да' || text === 'подтвердить' || text === 'продолжить') {
                var box = nodes[i].closest('.modal, .fancybox, [role="dialog"]');
                if (box) {
                    nodes[i].click();
                    return;
                }
            }
        }
    }

    function sweep() {
        dismissAgeGate();
        var vids = document.querySelectorAll('video');
        for (var i = 0; i < vids.length; i++) {
            vids[i].removeAttribute('poster');
        }
    }

    sweep();
    setInterval(sweep, 1200);
})();
