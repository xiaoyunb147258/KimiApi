(function () {
  'use strict';

  var N = window.KimiNative;
  var call = window.__call;
  var toast = window.__toast;

  function $(id) { return document.getElementById(id); }

  function loadSettings() {
    var r = call(function () { return N.getStatus(); });
    if (!r.ok) return;
    var d = r.data;
    $('in-port').value = d.configPort;
    $('in-key').value = d.apiKey;
    $('in-model').value = d.model;
    $('sw-auto').checked = !!d.autoStart;
    $('sw-float').checked = !!d.showFloat;
    $('sw-search').checked = !!d.useSearch;
  }
  window.loadSettings = loadSettings;

  $('btn-save').addEventListener('click', function () {
    var port = parseInt($('in-port').value, 10);
    if (isNaN(port) || port < 1 || port > 65535) { toast('端口不合法'); return; }
    var key = $('in-key').value.trim();
    var model = $('in-model').value.trim() || 'kimi';
    var r = call(function () { return N.saveSettings(port, key, model); });
    if (r.ok) toast('已保存，重启网关生效');
    else toast(r.error);
  });

  function bindSwitch(id, key) {
    $(id).addEventListener('change', function () {
      var val = $(id).checked;
      call(function () { return N.setSwitch(key, val); });
      toast('已' + (val ? '开启' : '关闭'));
      if (key === 'showFloat') {
        call(function () { return N.restartService(); });
        setTimeout(function () { window.__refreshStatus && window.__refreshStatus(); }, 1000);
      }
    });
  }

  bindSwitch('sw-auto', 'autoStart');
  bindSwitch('sw-float', 'showFloat');
  bindSwitch('sw-search', 'useSearch');

  $('btn-overlay').addEventListener('click', function () {
    call(function () { return N.requestOverlay(); });
    toast('请在弹出的页面授予权限');
  });

  $('btn-battery').addEventListener('click', function () {
    call(function () { return N.requestBattery(); });
    toast('请在弹出的页面允许忽略优化');
  });
})();
