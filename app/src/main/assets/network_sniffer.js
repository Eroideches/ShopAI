// ShopAI: intercetta le risposte JSON che la pagina scarica (fetch e XHR) e le passa all'app.
// Iniettato a inizio documento: i dati dei risultati arrivano anche se la grafica del sito cambia.
(function () {
  if (window.__shopaiSniffer) return;
  window.__shopaiSniffer = true;
  var MAX_BODY = 3000000, MAX_COUNT = 40, sent = 0;
  function looksJson(t) { if (!t) return false; var c = t.charAt(0); return c === '{' || c === '['; }
  function deliver(url, text) {
    try {
      if (sent >= MAX_COUNT || !text || text.length > MAX_BODY) return;
      var t = text.trim();
      if (!looksJson(t)) return;
      if (t.length < 200) return;
      if (!/(price|prezzo|amount|goods|product|item)/i.test(t.slice(0, 200000))) return;
      sent++;
      ShopAIBridge.net(String(url), t);
    } catch (e) {}
  }
  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = function () {
      var args = arguments;
      return origFetch.apply(this, args).then(function (resp) {
        try {
          var url = (args[0] && args[0].url) ? args[0].url : String(args[0]);
          var ct = resp.headers && resp.headers.get ? (resp.headers.get('content-type') || '') : '';
          if (ct.indexOf('json') >= 0 || ct.indexOf('javascript') >= 0 || ct.indexOf('text') >= 0) {
            resp.clone().text().then(function (t) { deliver(url, t); }).catch(function () {});
          }
        } catch (e) {}
        return resp;
      });
    };
  }
  var XO = XMLHttpRequest.prototype.open, XS = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (m, u) { this.__shopaiUrl = u; return XO.apply(this, arguments); };
  XMLHttpRequest.prototype.send = function () {
    var x = this;
    x.addEventListener('load', function () {
      try {
        if (x.responseType === '' || x.responseType === 'text') deliver(x.__shopaiUrl, x.responseText);
        else if (x.responseType === 'json' && x.response) deliver(x.__shopaiUrl, JSON.stringify(x.response));
      } catch (e) {}
    });
    return XS.apply(this, arguments);
  };
})();
