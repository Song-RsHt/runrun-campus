import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import QRCode from 'qrcode'
import { replaceAdminMfa, verifyAdminMfa } from './api'
import type { AuthResponse, MfaChallenge, MfaVerification } from './types'

export function AdminMfaChallenge({ challenge, onComplete, onCancel }: {
  challenge: MfaChallenge
  onComplete: (session: AuthResponse) => void
  onCancel: () => void
}) {
  const [current, setCurrent] = useState(challenge)
  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [completed, setCompleted] = useState<MfaVerification | null>(null)
  const [saved, setSaved] = useState(false)
  const canvas = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    if (current.otpauthUri && canvas.current) {
      QRCode.toCanvas(canvas.current, current.otpauthUri, { width: 220, margin: 4 })
        .catch(() => setError('二维码生成失败，请使用下方密钥手动添加。'))
    }
  }, [current.otpauthUri])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true); setError('')
    try {
      const result = await verifyAdminMfa(current.challengeToken, code.trim())
      setCode('')
      if (result.challenge) setCurrent(result.challenge)
      else if (result.session && result.recoveryCodes.length) setCompleted(result)
      else if (result.session) onComplete(result.session)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '验证失败')
    } finally { setBusy(false) }
  }

  if (completed?.session) return <section className="login-card mfa-card" aria-label="保存恢复码">
    <div><p className="eyebrow accent">ACCOUNT SECURITY</p><h2>保存恢复码</h2>
      <p className="muted">认证器已绑定。手机丢失时，可使用密码和任意一个恢复码登录；每个恢复码只能使用一次。</p></div>
    <p className="readonly-note">恢复码只在这里显示一次，请保存在安全位置。关闭或刷新页面后，可使用认证器重新登录。</p>
    <textarea aria-label="一次性恢复码" className="mfa-recovery-codes" readOnly rows={10} value={completed.recoveryCodes.join('\n')} />
    <label className="mfa-checkbox"><input type="checkbox" checked={saved} onChange={event => setSaved(event.target.checked)} />我已安全保存恢复码</label>
    <button className="primary-button login-button" disabled={!saved} onClick={() => onComplete(completed.session!)}>进入运营后台</button>
  </section>

  const enrolling = current.step === 'ENROLL' || current.step === 'REPLACE'
  return <form className="login-card mfa-card" onSubmit={submit}>
    <div><p className="eyebrow accent">ACCOUNT SECURITY</p>
      <h2>{enrolling ? '绑定认证器' : current.step === 'RECOVER' ? '重新绑定验证' : '双因素验证'}</h2>
      <p className="muted">{enrolling ? '在支持 TOTP 的认证器中扫描二维码，然后输入当前动态码。' : current.step === 'RECOVER' ? '输入管理员在核验身份后交给你的一次性重置凭证。' : '输入认证器当前显示的动态码，或一个尚未使用的恢复码。'}</p></div>
    {enrolling && <div className="mfa-enrollment">
      <canvas ref={canvas} aria-label="认证器绑定二维码" />
      <label>手动添加密钥<input className="mfa-secret" readOnly value={current.secret ?? ''} /></label>
      <p className="muted">账号类型：基于时间；6 位数字，每 30 秒更新。请保密此密钥。</p>
    </div>}
    <label>{enrolling ? '认证器动态码' : current.step === 'RECOVER' ? '一次性重置凭证' : '动态码或恢复码'}
      <input autoComplete="one-time-code" autoFocus required maxLength={128} value={code} onChange={event => setCode(event.target.value)} />
    </label>
    <p className="security-note">本次验证 5 分钟内有效。动态码已使用时，请等待下一个动态码。</p>
    {error && <p className="form-error" role="alert">{error}</p>}
    <button className="primary-button login-button" disabled={busy} type="submit">{busy ? '正在验证…' : enrolling ? '确认绑定认证器' : '确认验证'}</button>
    <button className="secondary-button" disabled={busy} type="button" onClick={onCancel}>返回</button>
  </form>
}

export function AdminMfaSettings({ session, onComplete, onClose }: {
  session: AuthResponse
  onComplete: (session: AuthResponse) => void
  onClose: () => void
}) {
  const [password, setPassword] = useState('')
  const [code, setCode] = useState('')
  const [challenge, setChallenge] = useState<MfaChallenge | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError('')
    try {
      setChallenge(await replaceAdminMfa(session.token, password, code.trim()))
      setPassword(''); setCode('')
    } catch (reason) { setError(reason instanceof Error ? reason.message : '验证失败') }
    finally { setBusy(false) }
  }
  return <div className="drawer-layer">
    <button className="drawer-backdrop" aria-label="关闭认证器设置" onClick={onClose} />
    <aside className="drawer compact-drawer" role="dialog" aria-modal="true" aria-label="认证器设置">
      <header><h2>认证器设置</h2><button className="close-button" onClick={onClose} aria-label="关闭">×</button></header>
      <div className="drawer-content">
        {challenge ? <AdminMfaChallenge challenge={challenge} onComplete={onComplete} onCancel={onClose} />
          : <form className="login-card" onSubmit={submit}>
            <p className="readonly-note">更换认证器前，请验证密码和第二因素。新认证器确认绑定后，旧认证器、旧恢复码和其他设备会话立即失效。</p>
            <label>当前密码<input required type="password" autoComplete="current-password" maxLength={72} value={password} onChange={event => setPassword(event.target.value)} /></label>
            <label>动态码或恢复码<input required autoComplete="one-time-code" maxLength={128} value={code} onChange={event => setCode(event.target.value)} /></label>
            {error && <p className="form-error" role="alert">{error}</p>}
            <button className="primary-button login-button" disabled={busy} type="submit">{busy ? '正在验证…' : '验证并更换认证器'}</button>
          </form>}
      </div>
    </aside>
  </div>
}
