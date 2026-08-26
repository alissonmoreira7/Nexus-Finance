import './AnimatedSuccess.css'

interface AnimatedSuccessProps {
  title: string
  message?: string
  ariaLabel?: string
  fullscreen?: boolean
}

export default function AnimatedSuccess({
  title,
  message,
  ariaLabel = 'Operação realizada com sucesso',
  fullscreen = false,
}: AnimatedSuccessProps) {
  return <div
    className={`animated-success${fullscreen ? ' animated-success-fullscreen' : ''}`}
    role="status"
    aria-live="assertive"
    aria-label={ariaLabel}
  >
    <div className="animated-success-content">
      <svg className="animated-success-check" viewBox="0 0 120 120" aria-hidden="true">
        <circle className="animated-success-orbit" cx="60" cy="60" r="51" pathLength="100" />
        <circle className="animated-success-ring" cx="60" cy="60" r="41" pathLength="100" />
        <path className="animated-success-mark" d="M38 61 53 76 84 43" pathLength="100" />
      </svg>
      <h2>{title}</h2>
      {message && <p>{message}</p>}
    </div>
  </div>
}
