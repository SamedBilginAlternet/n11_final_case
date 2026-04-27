import { render, screen } from '@testing-library/react';
import CountdownTimer from './CountdownTimer.jsx';

describe('CountdownTimer', () => {
  it('renders three HH:MM:SS cells', () => {
    const future = new Date(Date.now() + 3_600_000).toISOString();
    render(<CountdownTimer endsAt={future} />);
    const cells = screen.getAllByText(/^\d{2}$/);
    expect(cells.length).toBeGreaterThanOrEqual(3);
  });

  it('shows zeros once the deadline has passed', () => {
    const past = new Date(Date.now() - 1000).toISOString();
    render(<CountdownTimer endsAt={past} />);
    const zeros = screen.getAllByText('00');
    expect(zeros.length).toBe(3);
  });
});
