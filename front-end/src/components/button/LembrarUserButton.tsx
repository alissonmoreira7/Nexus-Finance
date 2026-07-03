interface LembrarDeMimProps {
  checked: boolean;
  onChange: (value: boolean) => void;
}

export default function LembrarDeMim({ checked, onChange }: LembrarDeMimProps) {
  return (
    <div className="lembrar-user">
        <label className="lembrar-de-mim">
        <input
            type="checkbox"
            checked={checked}
            onChange={(e) => onChange(e.target.checked)}
        />
        <span>Lembrar de mim</span>
        </label>
    </div>
  );
}