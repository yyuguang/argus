const AUTH_KEY = 'argus_admin_session'

export function getSession() {
  const raw = localStorage.getItem(AUTH_KEY)
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw)
  } catch {
    localStorage.removeItem(AUTH_KEY)
    return null
  }
}

export function isAuthenticated() {
  return Boolean(getSession()?.username)
}

export function login(username, password) {
  if (username !== 'admin' || password !== 'Argus@123') {
    throw new Error('账号或密码错误，请使用初始化管理员账号登录')
  }

  const session = {
    username,
    displayName: '平台管理员',
    loginAt: new Date().toISOString(),
  }
  localStorage.setItem(AUTH_KEY, JSON.stringify(session))
  return session
}

export function logout() {
  localStorage.removeItem(AUTH_KEY)
}
