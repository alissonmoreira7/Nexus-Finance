import '../../index.css'
import {useState} from 'react';
import {Lock, Eye, EyeOff} from 'lucide-react';

interface PasswordInputProps{
    value: string;
    onChange: (value: string) => void;
    placeholder?: string;
    required?: boolean;
    label?: string;
    autoComplete?: string;
}

export default function PasswordInput({
  value,
  onChange,
  placeholder = "••••••••",
  required = true,
  label = "Senha",
  autoComplete = "current-password"
}: PasswordInputProps) {
    const [mostrarSenha, setMostrarSenha] = useState(false);

    return (
        <div className="input_senha">
            <label className="input_senha_titulo">{label}</label>
        
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
                    <Lock size={18} style={{ color: 'var(--color-gray-500, #9ca3af)' }} />
                </div>

                 <input
                    type={mostrarSenha ? 'text' : 'password'}
                    value={value}
                    onChange={(e) => onChange(e.target.value)}
                    placeholder={placeholder}
                    required={required}
                    minLength={6}
                    autoComplete={autoComplete}
                    style={{
                        paddingLeft: '40px',
                        paddingRight: '40px', 
                        width: '100%',
                        boxSizing: 'border-box'
                    }}
                />

                <button
                    type="button" 
                    onClick={() => setMostrarSenha((prev) => !prev)}
                    style={{
                        position: 'absolute',
                        top: '40%',
                        right: '12px',
                        transform: 'translateY(-50%)',
                        display: 'flex',
                        alignItems: 'center',
                        background: 'none',
                        border: 'none',
                        cursor: 'pointer',
                        padding: 0,
                        zIndex: 10
                    }}
                    aria-label={mostrarSenha ? 'Esconder senha' : 'Mostrar senha'}
                    >
                    {mostrarSenha ? (
                        <EyeOff size={18} style={{ color: 'var(--color-gray-500, #9ca3af)' }} />
                    ) : (
                        <Eye size={18} style={{ color: 'var(--color-gray-500, #9ca3af)' }} />
                    )}
                </button>
            </div>
        </div>
    );
}
