import { useState } from 'react';
import Cards from 'react-credit-cards-2';
import 'react-credit-cards-2/dist/es/styles-compiled.css';

const SANDBOX_HINT = '5528 7900 0000 0008 · 12/30 · 123';

/**
 * Iyzico sandbox card collection. The library renders a flipping card preview
 * that follows the user's input — focus on the CVC input flips it. We pass
 * the raw values up via onChange; the parent serialises them straight into
 * the checkout POST body. This is *not* PCI-compliant — production would use
 * Iyzico's checkout-form / 3DS dropin so PAN never reaches our origin.
 */
export default function CardForm({ value, onChange, disabled }) {
  const [focus, setFocus] = useState('');

  function set(field, v) {
    onChange({ ...value, [field]: v });
  }

  return (
    <div className="grid gap-4 md:grid-cols-[280px_1fr] md:items-start">
      <div className="mx-auto md:mx-0">
        <Cards
          number={value.number}
          name={value.holderName}
          expiry={`${value.expireMonth}${value.expireYear.slice(-2)}`}
          cvc={value.cvc}
          focused={focus}
        />
        <p className="mt-2 text-center text-[11px] text-gray-400 md:text-left">
          Sandbox kartı: {SANDBOX_HINT}
        </p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Field className="sm:col-span-2" label="Kart Numarası">
          <input
            inputMode="numeric"
            placeholder="5528 7900 0000 0008"
            maxLength={19}
            value={formatCardNumber(value.number)}
            onChange={(e) => set('number', e.target.value.replace(/\D/g, '').slice(0, 19))}
            onFocus={() => setFocus('number')}
            disabled={disabled}
            className="input"
            autoComplete="cc-number"
          />
        </Field>

        <Field className="sm:col-span-2" label="Kart Üzerindeki İsim">
          <input
            placeholder="John Doe"
            value={value.holderName}
            onChange={(e) => set('holderName', e.target.value.toUpperCase().slice(0, 30))}
            onFocus={() => setFocus('name')}
            disabled={disabled}
            className="input"
            autoComplete="cc-name"
          />
        </Field>

        <Field label="Ay">
          <select
            value={value.expireMonth}
            onChange={(e) => set('expireMonth', e.target.value)}
            onFocus={() => setFocus('expiry')}
            disabled={disabled}
            className="input"
            autoComplete="cc-exp-month"
          >
            <option value="">Ay</option>
            {Array.from({ length: 12 }, (_, i) => String(i + 1).padStart(2, '0')).map((m) => (
              <option key={m} value={m}>{m}</option>
            ))}
          </select>
        </Field>

        <Field label="Yıl">
          <select
            value={value.expireYear}
            onChange={(e) => set('expireYear', e.target.value)}
            onFocus={() => setFocus('expiry')}
            disabled={disabled}
            className="input"
            autoComplete="cc-exp-year"
          >
            <option value="">Yıl</option>
            {yearOptions().map((y) => (
              <option key={y} value={y}>{y}</option>
            ))}
          </select>
        </Field>

        <Field label="CVC">
          <input
            inputMode="numeric"
            placeholder="123"
            maxLength={4}
            value={value.cvc}
            onChange={(e) => set('cvc', e.target.value.replace(/\D/g, '').slice(0, 4))}
            onFocus={() => setFocus('cvc')}
            disabled={disabled}
            className="input"
            autoComplete="cc-csc"
          />
        </Field>
      </div>
    </div>
  );
}

function Field({ label, children, className = '' }) {
  return (
    <label className={`block text-sm ${className}`}>
      <span className="mb-1 block text-xs font-medium text-gray-500">{label}</span>
      {children}
    </label>
  );
}

function formatCardNumber(digits) {
  return (digits || '').match(/.{1,4}/g)?.join(' ') || '';
}

function yearOptions() {
  const now = new Date().getFullYear();
  return Array.from({ length: 12 }, (_, i) => String(now + i));
}

export const EMPTY_CARD = {
  holderName: '',
  number: '',
  expireMonth: '',
  expireYear: '',
  cvc: '',
};

export function isCardComplete(card) {
  return Boolean(
    card.holderName.trim().length >= 3 &&
    card.number.length >= 12 &&
    /^(0[1-9]|1[0-2])$/.test(card.expireMonth) &&
    /^\d{4}$/.test(card.expireYear) &&
    /^\d{3,4}$/.test(card.cvc),
  );
}
