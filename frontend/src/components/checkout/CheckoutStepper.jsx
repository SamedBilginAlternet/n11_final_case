import { Check } from 'lucide-react';

/**
 * Three-step indicator for the checkout flow. Steps are 0-indexed; everything
 * before `current` renders as completed, `current` is active, and everything
 * after is upcoming. Clicking a completed step calls `onJump` so users can
 * scroll back to fix something they entered earlier.
 */
export default function CheckoutStepper({ steps, current, onJump }) {
  return (
    <ol className="flex items-center gap-2 sm:gap-4">
      {steps.map((label, i) => {
        const done = i < current;
        const active = i === current;
        const clickable = done && onJump;
        return (
          <li key={label} className="flex flex-1 items-center gap-2 sm:gap-3">
            <button
              type="button"
              disabled={!clickable}
              onClick={clickable ? () => onJump(i) : undefined}
              className={`flex flex-1 items-center gap-2 sm:gap-3 ${clickable ? 'cursor-pointer' : 'cursor-default'}`}
            >
              <span
                className={`grid h-8 w-8 shrink-0 place-items-center rounded-full text-xs font-semibold transition ${
                  done
                    ? 'bg-emerald-500 text-white'
                    : active
                    ? 'bg-n11-pink text-white shadow ring-2 ring-n11-pink/20'
                    : 'bg-gray-100 text-gray-400'
                }`}
                aria-current={active ? 'step' : undefined}
              >
                {done ? <Check className="h-4 w-4" strokeWidth={3} /> : i + 1}
              </span>
              <span
                className={`hidden whitespace-nowrap text-sm font-medium sm:inline ${
                  done || active ? 'text-gray-800' : 'text-gray-400'
                }`}
              >
                {label}
              </span>
            </button>
            {i < steps.length - 1 && (
              <span
                className={`h-0.5 flex-1 rounded-full transition ${
                  done ? 'bg-emerald-500' : 'bg-gray-200'
                }`}
              />
            )}
          </li>
        );
      })}
    </ol>
  );
}
