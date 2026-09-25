(function () {
  'use strict';

  var N = window.KimiNative;
  function $(id) { return document.getElementById(id); }

  function call(fn) {
    try {
      var raw = fn();
      return raw ? JSON.parse(raw) : { ok: false, error: '空响应' };
    } catch (e) {
      return { ok: false, error: String(e) };
    }
  }

  function toast(msg) {
    var t = $('toast');
    t.textContent = msg;
    t.classList.add('show');
    clearTimeout(t._timer);
    t._timer = setTimeout(function () { t.classList.remove('show'); }, 1800);
  }

  // ---------------- 页面切换 ----------------

  var pages = ['account', 'status', 'settings'];

  function switchPage(name) {
    pages.forEach(function (p) {
      $('page-' + p).classList.toggle('hidden', p !== name);
    });
    document.querySelectorAll('.tab').forEach(function (tab) {
      tab.classList.toggle('active', tab.dataset.page === name);
    });
    $('btn-add').classList.toggle('hidden', name !== 'account');
    if (name === 'status') loadLogs();
    if (name === 'settings' && window.loadSettings) window.loadSettings();
  }

  document.querySelectorAll('.tab').forEach(function (tab) {
    tab.addEventListener('click', function () { switchPage(tab.dataset.page); });
  });

  // ---------------- 状态 ----------------

  function refreshStatus() {
    var r = call(function () { return N.getStatus(); });
    if (!r.ok) return;
    var d = r.data;
    var running = d.running;
    $('dot').classList.toggle('on', running);
    $('status-text').textContent = running ? '运行中' : '未运行';
    $('addr').textContent = 'http://' + d.ip + ':' + d.port + '/v1';
    $('btn-toggle').textContent = running ? '停止网关' : '启动网关';
    $('stat-acc').textContent = d.accountCount;
    $('stat-req').textContent = d.requestCount;
    $('stat-port').textContent = d.port;
  }

  $('btn-copy-url').addEventListener('click', function () {
    var r = call(function () { return N.getStatus(); });
    if (!r.ok) return;
    var url = 'http://' + r.data.ip + ':' + r.data.port + '/v1';
    var c = call(function () { return N.copyToClipboard(url); });
    toast(c.ok ? '已复制：' + url : '复制失败');
  });

  // ---------------- 账号 ----------------

  function loadAccounts() {
    var r = call(function () { return N.getAccounts(); });
    if (!r.ok) return;
    var list = r.data || [];
    var box = $('account-list');
    box.innerHTML = '';
    $('account-empty').classList.toggle('hidden', list.length > 0);

    list.forEach(function (a, idx) {
      var row = document.createElement('div');
      row.className = 'acc';

      var left = document.createElement('div');
      left.className = 'acc-l';

      var name = document.createElement('div');
      name.className = 'acc-name';
      name.textContent = a.remark || ('账号 ' + (idx + 1));

      var token = document.createElement('div');
      token.className = 'acc-token';
      token.textContent = a.refreshToken;

      var st = document.createElement('div');
      st.className = 'acc-status';
      st.textContent = a.status;
      if (a.status === '可用' || a.status === '使用中') st.classList.add('ok');
      if (a.status.indexOf('异常') === 0) st.classList.add('err');

      left.appendChild(name);
      left.appendChild(token);
      left.appendChild(st);

      var actions = document.createElement('div');
      actions.className = 'acc-actions';

      var test = document.createElement('button');
      test.className = 'acc-test';
      test.textContent = '测试';
      test.addEventListener('click', function () {
        test.disabled = true;
        test.textContent = '…';
        var r2 = call(function () { return N.testAccount(idx); });
        test.disabled = false;
        test.textContent = '测试';
        if (r2.ok) { toast('账号可用'); loadAccounts(); }
        else toast(r2.error);
      });

      var del = document.createElement('button');
      del.className = 'acc-del';
      del.textContent = '删除';
      del.addEventListener('click', function () {
        var r3 = call(function () { return N.deleteAccount(idx); });
        if (r3.ok) { toast('已删除'); loadAccounts(); refreshStatus(); }
        else toast(r3.error);
      });

      actions.appendChild(test);
      actions.appendChild(del);

      row.appendChild(left);
      row.appendChild(actions);
      box.appendChild(row);
    });
  }
  window.reloadAccounts = loadAccounts;

  // ---------------- 弹窗 ----------------

  function closeModal() { $('modal').classList.add('hidden'); }

  $('modal-cancel').addEventListener('click', closeModal);
  document.querySelector('.modal-mask').addEventListener('click', closeModal);

  $('btn-login').addEventListener('click', function () {
    var r = call(function () { return N.openLogin(); });
    if (r.ok) toast('请在弹出的页面登录 Kimi');
    else toast(r.error);
  });

  $('btn-add').addEventListener('click', function () {
    $('in-token').value = '';
    $('in-remark').value = '';
    $('modal').classList.remove('hidden');
    $('modal-ok').onclick = function () {
      var token = $('in-token').value.trim();
      var remark = $('in-remark').value.trim();
      if (!token) { toast('请填写 refresh_token'); return; }
      var r = call(function () { return N.addAccount(token, remark); });
      if (r.ok) { toast('已添加'); closeModal(); loadAccounts(); refreshStatus(); }
      else toast(r.error);
    };
  });

  // ---------------- 日志 ----------------

  function loadLogs() {
    var r = call(function () { return N.getLogs(); });
    if (!r.ok) return;
    var box = $('log');
    box.innerHTML = '';
    (r.data || []).forEach(function (e) {
      var line = document.createElement('div');
      line.textContent = '[' + e.time + '] ' + e.text;
      box.appendChild(line);
    });
    box.scrollTop = box.scrollHeight;
  }

  window.onNativeLog = function (time, text) {
    var box = $('log');
    if (box.children.length > 300) box.removeChild(box.firstChild);
    var line = document.createElement('div');
    line.textContent = '[' + time + '] ' + text;
    box.appendChild(line);
    box.scrollTop = box.scrollHeight;
  };

  // 内置登录回调：拿到 refresh_token 直接加账号
  window.onLoginToken = function (token) {
    if (!token) return;
    var r = call(function () { return N.addAccount(token, '登录账号'); });
    if (r.ok) {
      toast('已通过登录添加账号');
      loadAccounts();
      refreshStatus();
    } else {
      toast(r.error);
    }
  };

  $('btn-clear-log').addEventListener('click', function () {
    call(function () { return N.clearLogs(); });
    loadLogs();
  });

  // ---------------- 服务开关 ----------------

  $('btn-toggle').addEventListener('click', function () {
    var r = call(function () { return N.getStatus(); });
    var running = r.ok && r.data.running;
    call(function () { return running ? N.stopService() : N.startService(); });
    toast(running ? '正在停止…' : '正在启动…');
    setTimeout(refreshStatus, 900);
  });

  $('btn-restart').addEventListener('click', function () {
    call(function () { return N.restartService(); });
    toast('正在重启…');
    setTimeout(refreshStatus, 1100);
  });

  // ---------------- 初始化 ----------------

  window.__refreshStatus = refreshStatus;
  window.__toast = toast;
  window.__call = call;

  document.addEventListener('DOMContentLoaded', function () {
    refreshStatus();
    loadAccounts();
    if (window.loadSettings) window.loadSettings();
    setInterval(refreshStatus, 3000);
  });
})();
