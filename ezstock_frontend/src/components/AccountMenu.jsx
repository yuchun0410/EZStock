import { Link, useLocation, useNavigate } from 'react-router'
import { logout } from '../api/authApi'
import { useAuth } from '../hooks/useAuth'

/** 頁首右上角：沒登入顯示「登入」，登入後顯示頭像、名稱、登出 */
function AccountMenu() {
  const { status, member } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  if (status === 'checking') {
    return <span className="account" aria-busy="true" />
  }

  if (status !== 'authenticated') {
    // 登入、註冊頁本身就不用再顯示登入按鈕
    if (location.pathname === '/login' || location.pathname === '/register') {
      return <span className="account" />
    }
    return (
      <span className="account">
        <Link to="/login" state={{ from: location.pathname + location.search }} className="header-button">
          登入
        </Link>
      </span>
    )
  }

  async function handleLogout() {
    await logout()
    navigate('/')
  }

  return (
    <span className="account">
      {member.avatarUrl ? (
        <img className="avatar" src={member.avatarUrl} alt="" referrerPolicy="no-referrer" />
      ) : (
        <span className="avatar" aria-hidden="true">
          {member.displayName.slice(0, 1)}
        </span>
      )}
      <span className="account-name" title={member.email}>
        {member.displayName}
      </span>
      <button type="button" className="header-button" onClick={handleLogout}>
        登出
      </button>
    </span>
  )
}

export default AccountMenu
