(async () => {
  const me = await App.guard('staff');
  if (!me) return;
  const $ = (id) => document.getElementById(id);
  let scanner = null;
  let locked = false;

  function say(text, ok) {
    $('result').textContent = text;
    $('result').className = 'msg' + (ok ? ' ok' : '');
  }

  async function loadRooms() {
    const data = await App.api('/api/attendance?status=ACTIVE');
    const list = data.data || [];
    $('rooms').innerHTML = list.length
      ? '<table><thead><tr><th>Room</th><th>Teacher</th><th>Since</th></tr></thead><tbody>' +
        list.map((a) => `<tr><td>${App.esc(a.room_name)}</td><td>${App.esc(a.teacher_name)}</td><td>${App.fmt(a.time_in)}</td></tr>`).join('') +
        '</tbody></table>'
      : '<div class="empty"><i class="bi bi-door-closed"></i>No rooms are in use right now.</div>';
  }

  const loadHistory = App.reports();
  const refresh = () => Promise.all([loadRooms(), loadHistory()]);

  async function submit(token) {
    if (locked || !token.trim()) return;
    locked = true;
    setTimeout(() => (locked = false), 2500);
    const result = await App.api('/api/attendance', 'POST', { token });
    say(result.message, result.success);
    if (result.success) refresh();
  }

  $('scanBtn').onclick = async () => {
    if (scanner) {
      await scanner.stop().catch(() => {});
      scanner = null;
      $('reader').classList.add('hidden');
      $('scanBtn').innerHTML = '<i class="bi bi-camera"></i> Start camera';
      return;
    }
    $('reader').classList.remove('hidden');
    scanner = new Html5Qrcode('reader');
    try {
      await scanner.start({ facingMode: 'environment' }, { fps: 10, qrbox: 220 }, (text) => submit(text));
      $('scanBtn').innerHTML = '<i class="bi bi-stop-circle"></i> Stop camera';
    } catch (error) {
      scanner = null;
      $('reader').classList.add('hidden');
      say('The camera could not start. Allow camera access or enter the code manually.');
    }
  };

  $('manualBtn').onclick = () => {
    submit($('manual').value);
    $('manual').value = '';
  };

  loadRooms();
  setInterval(refresh, 6000);
})();
