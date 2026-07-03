import { Mail } from 'lucide-react';

interface EmailInputProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  required?: boolean;
}

export default function EmailInput({
  value,
  onChange,
  placeholder = "seu@email.com",
  required = true
}: EmailInputProps) {
  return (
    <div className="input_email">
      <label className="input_email_titulo">E-mail</label>
      
      <div style={{ position: 'relative', width: '100%', minHeight: '44px' }}>
        <div style={{
          position: 'absolute',
          top: '40%',
          left: '12px',
          transform: 'translateY(-50%)',
          display: 'flex',
          alignItems: 'center',
          pointerEvents: 'none',
          zIndex: 10 
        }}>
          <Mail size={18} style={{ color: 'var(--color-gray-500, #9ca3af)' }} />
        </div>

        <input
          type="email"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          required={required}
          style={{ paddingLeft: '40px', width: '100%', boxSizing: 'border-box' }}
        />
      </div>
    </div>
  );
}