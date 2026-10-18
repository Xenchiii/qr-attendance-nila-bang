(async () => {
  const me = await App.guard('admin');
  if (!me) return;
  const $ = (id) => document.getElementById(id);

  async function loadStats() {
    const data = await App.api('/api/stats?' + App.query({ tz: App.tz(), date: App.today() }));
    const s = data.data || {};
    const cards = [
      ['bi-person-workspace', 'Teachers', s.teachers],
      ['bi-person-badge', 'Staff', s.staff],
      ['bi-hourglass-split', 'Pending approval', s.pending],
      ['bi-door-open', 'Rooms in use', s.in_use],
      ['bi-calendar-check', 'Records today', s.today]
    ];
    $('stats').innerHTML = cards.map((c) => `<div class="card stat"><i class="bi ${c[0]}"></i><div><strong>${c[2] ?? 0}</strong><span>${c[1]}</span></div></div>`).join('');
  }

  async function loadUsers() {
    const data = await App.api('/api/users');
    const list = data.data || [];
    const actions = {
      ACTIVE: ['SUSPENDED', 'Suspend', 'warn', 'slash-circle'],
      INACTIVE: ['ACTIVE', 'Approve', 'ok', 'check-circle'],
      SUSPENDED: ['ACTIVE', 'Activate', 'ok', 'check-circle']
    };
    $('users').innerHTML = list.length
      ? '<table><thead><tr><th>Name</th><th>ID number</th><th>Email</th><th>Role</th><th>Status</th><th></th></tr></thead><tbody>' +
        list.map((u) => {
          const a = actions[u.status];
          return `<tr><td>${App.esc(u.first_name + ' ' + u.last_name)}</td><td>${App.esc(u.employee_no)}</td><td>${App.esc(u.email)}</td>` +
            `<td><span class="badge">${u.role}</span></td><td><span class="badge ${u.status}">${u.status.toLowerCase()}</span></td>` +
            `<td><button class="btn sm ${a[2]}" data-id="${u.id}" data-status="${a[0]}"><i class="bi bi-${a[3]}"></i> ${a[1]}</button></td></tr>`;
        }).join('') + '</tbody></table>'
      : '<div class="empty"><i class="bi bi-people"></i>No teacher or staff accounts yet.</div>';
    $('users').querySelectorAll('[data-id]').forEach((button) => {
      button.onclick = async () => {
        const result = await App.api('/api/users', 'PUT', { id: Number(button.dataset.id), status: button.dataset.status });
        App.toast(result.message, !result.success);
        loadUsers();
        loadStats();
      };
    });
  }

  async function loadRooms() {
    const data = await App.api('/api/rooms');
    $('rooms').innerHTML = '<table><thead><tr><th>Code</th><th>Room</th><th>Building</th><th>Floor</th><th>Capacity</th><th>Status</th></tr></thead><tbody>' +
      (data.data || []).map((r) => `<tr><td>${App.esc(r.code)}</td><td>${App.esc(r.name)}</td><td>${App.esc(r.building)}</td><td>${App.esc(r.floor)}</td><td>${r.capacity}</td><td><span class="badge ${r.status === 'AVAILABLE' ? 'COMPLETED' : 'ACTIVE'}">${r.status.toLowerCase()}</span></td></tr>`).join('') +
      '</tbody></table>';
  }

  $('roomForm').onsubmit = async (event) => {
    event.preventDefault();
    const result = await App.api('/api/rooms', 'POST', {
      code: $('rCode').value, name: $('rName').value, building: $('rBuilding').value, floor: $('rFloor').value, capacity: $('rCap').value
    });
    App.toast(result.message, !result.success);
    if (result.success) {
      event.target.reset();
      loadRooms();
    }
  };

  const loadHistory = App.reports();
  loadStats();
  loadUsers();
  loadRooms();
  setInterval(() => {
    loadStats();
    loadHistory();
  }, 8000);
})();
