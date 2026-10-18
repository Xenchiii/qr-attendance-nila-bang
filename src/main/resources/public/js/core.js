const App = {
  async api(path, method = 'GET', body) {
    try {
      const response = await fetch(path, {
        method,
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: body ? JSON.stringify(body) : undefined
      });
      return await response.json();
    } catch (error) {
      return { success: false, message: 'Could not reach the server' };
    }
  },

  esc(text) {
    return String(text ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  },

  fmt(date) {
    return date ? new Date(date).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }) : '-';
  },

  tz() {
    return -new Date().getTimezoneOffset();
  },

  today() {
    const now = new Date();
    return new Date(now - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  },

  query(values) {
    return new URLSearchParams(Object.entries(values).filter(([, v]) => v !== '' && v != null)).toString();
  },

  toast(message, bad) {
    const el = document.createElement('div');
    el.className = 'toast' + (bad ? ' bad' : '');
    el.innerHTML = `<i class="bi bi-${bad ? 'x-circle' : 'check-circle'}"></i><span>${this.esc(message)}</span>`;
    document.body.appendChild(el);
    setTimeout(() => el.remove(), 3200);
  },

  async guard(role) {
    const data = await this.api('/api/auth/me');
    if (!data.success || data.user.role !== role) {
      location.href = 'index.html';
      return null;
    }
    const user = data.user;
    document.getElementById('topbar').innerHTML =
      '<div class="brand"><span class="logo"><i class="bi bi-qr-code-scan"></i></span>QR Attendance</div>' +
      `<div class="who"><b>${this.esc(user.first_name + ' ' + user.last_name)}</b><span class="badge">${user.role}</span>` +
      '<button class="btn ghost sm" id="logout"><i class="bi bi-box-arrow-right"></i> Log out</button></div>';
    document.getElementById('logout').onclick = async () => {
      await this.api('/api/auth/logout', 'POST');
      location.href = 'index.html';
    };
    return user;
  },

  table(rows, teacher, action) {
    if (!rows.length) return '<div class="empty"><i class="bi bi-inbox"></i>No attendance records found.</div>';
    const lines = rows.map((a) =>
      '<tr>' + (teacher ? `<td>${this.esc(a.teacher_name)}</td>` : '') +
      `<td>${this.esc(a.room_name)}</td><td>${this.fmt(a.time_in)}</td><td>${this.fmt(a.time_out)}</td>` +
      `<td>${a.duration_minutes != null ? a.duration_minutes + ' min' : '-'}</td>` +
      `<td><span class="badge ${a.status}">${a.status.toLowerCase()}</span></td>` +
      (action ? `<td class="no-print">${a.status === 'ACTIVE' ? '<button class="btn warn sm" data-out="1"><i class="bi bi-box-arrow-left"></i> Time out</button>' : ''}</td>` : '') +
      '</tr>').join('');
    return '<div class="table-wrap"><table><thead><tr>' + (teacher ? '<th>Teacher</th>' : '') +
      '<th>Room</th><th>Time in</th><th>Time out</th><th>Duration</th><th>Status</th>' + (action ? '<th class="no-print"></th>' : '') +
      `</tr></thead><tbody>${lines}</tbody></table></div>`;
  },

  reports() {
    const el = (id) => document.getElementById(id);
    const run = async () => {
      const params = this.query({ q: el('q').value.trim(), status: el('status').value, date: el('date').value, tz: this.tz() });
      const data = await this.api('/api/attendance?' + params);
      el('history').innerHTML = this.table(data.data || [], true, false);
    };
    ['q', 'status', 'date'].forEach((id) => el(id).addEventListener('input', run));
    el('print').onclick = () => window.print();
    run();
    return run;
  }
};
