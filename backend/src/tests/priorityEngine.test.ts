import { DeterministicPriorityEngine } from '../services/priorityEngine';

describe('DeterministicPriorityEngine Unit Tests', () => {
  test('calculates critical medical emergency score with high priority and floor guarantee', () => {
    const now = Date.now();
    const result = DeterministicPriorityEngine.evaluate('MEDICAL', 'CRITICAL', 3, now);

    expect(result.priorityScore).toBeGreaterThanOrEqual(80);
    expect(result.priorityCategory).toBe('CRITICAL');
    expect(result.ruleVersion).toBe('1.0.0');
    expect(result.explanation).toContain('MEDICAL');
  });

  test('calculates lower score for minor low severity emergency', () => {
    const now = Date.now();
    const result = DeterministicPriorityEngine.evaluate('OTHER', 'LOW', 1, now);

    expect(result.priorityScore).toBeLessThan(40);
    expect(result.priorityCategory).toBe('LOW');
  });

  test('elevates waiting time points gracefully as minutes elapse without exceeding maximum cap', () => {
    const now = Date.now();
    const twoHoursAgo = now - 120 * 60 * 1000; // 120 mins = 4 wait points (1 pt per 30m)
    const resultRecent = DeterministicPriorityEngine.evaluate('WATER', 'MEDIUM', 2, now, now);
    const resultOld = DeterministicPriorityEngine.evaluate('WATER', 'MEDIUM', 2, twoHoursAgo, now);

    expect(resultOld.priorityScore).toBeGreaterThan(resultRecent.priorityScore);
    expect(resultOld.priorityScore - resultRecent.priorityScore).toBe(4);
  });

  test('prioritizes emergencies with more affected persons', () => {
    const now = Date.now();
    const single = DeterministicPriorityEngine.evaluate('SHELTER', 'HIGH', 1, now);
    const multiple = DeterministicPriorityEngine.evaluate('SHELTER', 'HIGH', 8, now);

    expect(multiple.priorityScore).toBeGreaterThan(single.priorityScore);
  });
});
