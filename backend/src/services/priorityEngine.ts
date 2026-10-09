import { EmergencyCategory, PriorityEvaluation, SeverityLevel } from '../types';

/**
 * Deterministic Rule-Based Emergency Priority Engine.
 * Note: This is an explicit heuristic evaluation system, not an AI model or diagnostic system.
 */
export class DeterministicPriorityEngine {
  private static readonly RULE_VERSION = '1.0.0';

  public static evaluate(
    category: EmergencyCategory,
    severity: SeverityLevel,
    affectedCount: number,
    createdAtTimestampMs: number,
    currentTimestampMs: number = Date.now()
  ): PriorityEvaluation {
    // 1. Base Severity Score (Max 40 points)
    let severityScore = 20;
    switch (severity) {
      case 'CRITICAL':
        severityScore = 40;
        break;
      case 'HIGH':
        severityScore = 30;
        break;
      case 'MEDIUM':
        severityScore = 20;
        break;
      case 'LOW':
        severityScore = 10;
        break;
    }

    // 2. Category Urgency (Max 35 points)
    let categoryScore = 10;
    switch (category) {
      case 'MEDICAL':
        categoryScore = 35; // Direct threat to life
        break;
      case 'RESCUE':
        categoryScore = 30; // Entrapment / imminent structural danger
        break;
      case 'WATER':
        categoryScore = 18; // Critical for survival past 48h
        break;
      case 'SHELTER':
        categoryScore = 15; // Exposure to elements
        break;
      case 'FOOD':
        categoryScore = 12; // Vital relief
        break;
      case 'OTHER':
      default:
        categoryScore = 8;
        break;
    }

    // 3. Affected Count Scale (Max 15 points)
    // 1 person = 3 pts, 2-3 = 7 pts, 4-6 = 11 pts, 7+ = 15 pts
    const clampedCount = Math.max(1, affectedCount);
    let affectedScore = 3;
    if (clampedCount >= 7) {
      affectedScore = 15;
    } else if (clampedCount >= 4) {
      affectedScore = 11;
    } else if (clampedCount >= 2) {
      affectedScore = 7;
    }

    // 4. Waiting Time Factor (Max 10 points)
    // 1 point per 30 minutes of elapsed time since request creation
    const elapsedMinutes = Math.max(0, (currentTimestampMs - createdAtTimestampMs) / (1000 * 60));
    const waitingScore = Math.min(10, Math.floor(elapsedMinutes / 30));

    // Calculate Raw Total (Max 100 points)
    let totalScore = severityScore + categoryScore + affectedScore + waitingScore;

    // Safety constraint: Critical medical emergencies have a guaranteed floor of 80 points
    if (category === 'MEDICAL' && severity === 'CRITICAL') {
      totalScore = Math.max(80, totalScore);
    }
    // Rescue emergencies with high severity have a guaranteed floor of 70 points
    if (category === 'RESCUE' && (severity === 'CRITICAL' || severity === 'HIGH')) {
      totalScore = Math.max(70, totalScore);
    }

    // Clamp score between 0 and 100
    totalScore = Math.min(100, Math.max(0, totalScore));

    // Determine Priority Category
    let priorityCategory: SeverityLevel = 'LOW';
    if (totalScore >= 80) {
      priorityCategory = 'CRITICAL';
    } else if (totalScore >= 60) {
      priorityCategory = 'HIGH';
    } else if (totalScore >= 40) {
      priorityCategory = 'MEDIUM';
    } else {
      priorityCategory = 'LOW';
    }

    const explanation = `Score ${totalScore.toFixed(1)}/100: [Severity: ${severity} (${severityScore}pts)] + [Category: ${category} (${categoryScore}pts)] + [Victims: ${clampedCount} (${affectedScore}pts)] + [Wait: ${elapsedMinutes.toFixed(0)}m (${waitingScore}pts)]`;

    return {
      priorityScore: Number(totalScore.toFixed(1)),
      priorityCategory,
      explanation,
      ruleVersion: this.RULE_VERSION
    };
  }
}
