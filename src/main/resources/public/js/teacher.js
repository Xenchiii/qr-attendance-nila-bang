(async () => {
  const me = await App.guard('teacher');
  if (!me) return;
  const $ = (id) => document.getElementById(id);
  let tick = null;

  function hideQr() {
    clearInterval(tick);
    $('qrBox').classList.add('hidden');
  }

  async function loadRooms() {
    const data = await App.api('/api/rooms');
    const free = (data.data || []).filter((r) => r.status === 'AVAILABLE');
    const keep = $('room').value;
    $('room').innerHTML = free.length
      ? free.map((r) => `<option value="${r.id}">${App.esc(r.name)} - ${App.esc(r.building)}</option>`).join('')
      : '<option value="">No rooms available</option>';
    if (keep) $('room').value = keep;
  }

  async function loadHistory() {
    const data = await App.api('/api/attendance?tz=' + App.tz());
    const list = data.data || [];
    $('history').innerHTML = App.table(list, false, true);
    $('history').querySelectorAll('[data-out]').forEach((button) => {
      button.onclick = async () => {
        const result = await App.api('/api/attendance', 'PUT');
        App.toast(result.message, !result.success);
        loadHistory();
        loadRooms();
      };
    });
    if (list.some((a) => a.status === 'ACTIVE')) hideQr();
  }

  $('gen').onclick = async () => {
    const result = await App.api('/api/qr', 'POST', { room_id: $('room').value });
    if (!result.success) return App.toast(result.message, true);
    $('qr').innerHTML = '';
    new QRCode($('qr'), { text: result.data.token, width: 200, height: 200, colorDark: '#0f172a', colorLight: '#ffffff' });
    $('qrBox').classList.remove('hidden');
    const expires = new Date(result.data.expires_at).getTime();
    clearInterval(tick);
    tick = setInterval(() => {
      const left = expires - Date.now();
      if (left <= 0) {
        hideQr();
        App.toast('The QR code expired. Generate a new one.', true);
        return;
      }
      $('timer').textContent = 'Expires in ' + Math.floor(left / 60000) + ':' + String(Math.floor((left % 60000) / 1000)).padStart(2, '0');
    }, 500);
  };

  await loadRooms();
  await loadHistory();
  setInterval(() => {
    loadHistory();
    loadRooms();
  }, 5000);
})();
