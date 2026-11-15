import { expect, test } from 'bun:test';
import { InvestigationBudget } from '../src/budget';

test('all calls reserve against the same ceiling, including failed attempts', () => {
  const budget = new InvestigationBudget();
  for (let i = 0; i < 10; i++) budget.reserve(0.5);
  expect(budget.reservedAud).toBe(5);
  expect(() => budget.reserve(0.000001)).toThrow('limit');
});
test('unknown/free/negative/nonfinite prices and agent-raised ceilings fail closed', () => {
  for (const value of [0, -1, NaN, Infinity]) expect(() => new InvestigationBudget().reserve(value)).toThrow();
  expect(() => new InvestigationBudget(5_000_001)).toThrow();
});
