(async () => {
  const home = { teacher: 'teacher.html', staff: 'staff.html', admin: 'admin.html' };
  const $ = (id) => document.getElementById(id);
  const current = await App.api('/api/auth/me');
  if (current.success) {
    location.href = home[current.user.role];
    return;
  }

  let mode = 'login';
  const show = (text, ok) => {
    $('msg').textContent = text;
    $('msg').className = 'msg' + (ok ? ' ok' : '');
  };

  function setMode(next) {
    mode = next;
    $('tabLogin').classList.toggle('on', next === 'login');
    $('tabSignup').classList.toggle('on', next === 'signup');
    $('signupFields').classList.toggle('hidden', next === 'login');
    $('adminOpt').hidden = next === 'signup';
    if (next === 'signup' && $('role').value === 'admin') $('role').value = 'teacher';
    $('submit').innerHTML = next === 'login'
      ? '<i class="bi bi-box-arrow-in-right"></i> Log in'
      : '<i class="bi bi-person-plus"></i> Create account';
    $('msg').className = 'msg hidden';
  }

  $('tabLogin').onclick = () => setMode('login');
  $('tabSignup').onclick = () => setMode('signup');

  $('authForm').onsubmit = async (event) => {
    event.preventDefault();
    const role = $('role').value;
    const email = $('email').value.trim();
    const password = $('password').value;
    if (!email || !password) return show('Enter your email and password.');
    $('submit').disabled = true;
    if (mode === 'login') {
      const result = await App.api('/api/auth/login', 'POST', { email, password, role });
      $('submit').disabled = false;
      if (!result.success) return show(result.message);
      location.href = home[role];
      return;
    }
    const result = await App.api('/api/auth/register', 'POST', {
      role,
      email,
      password,
      employee_no: $('employee').value,
      first_name: $('first').value,
      last_name: $('last').value
    });
    $('submit').disabled = false;
    if (!result.success) return show(result.message);
    setMode('login');
    show(result.message, true);
  };
})();
