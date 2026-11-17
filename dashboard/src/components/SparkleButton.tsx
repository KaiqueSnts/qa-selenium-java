import type { ReactNode } from 'react'
import './SparkleButton.css'

type SparkleButtonProps = {
  children: ReactNode
  onClick?: () => void
  size?: 'sm' | 'md'
  disabled?: boolean
  className?: string
}

export default function SparkleButton({ children, onClick, size = 'md', disabled, className = '' }: SparkleButtonProps) {
  return <button type="button" className={`sparkle-button ${className}`} data-size={size} disabled={disabled} onClick={onClick}>
    <svg className="sparkle-button-icon" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="m9 3 1.8 5.2L16 10l-5.2 1.8L9 17l-1.8-5.2L2 10l5.2-1.8Z"/><path d="m18 13 1 3 3 1-3 1-1 3-1-3-3-1 3-1Z"/></svg>
    <span>{children}</span>
    <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"><path d="M5 12h14m-6-6 6 6-6 6"/></svg>
  </button>
}
