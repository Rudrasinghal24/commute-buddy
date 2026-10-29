import { RouteStep, VehicleMode } from './types';

export interface FareRate {
  baseInr: number;
  perKmInr: number;
  maxCapInr?: number;
}

export const FARE_RATES_INR: Record<VehicleMode, FareRate> = {
  Walk: { baseInr: 0, perKmInr: 0, maxCapInr: 0 },
  Bike: { baseInr: 15, perKmInr: 3.2 },
  Bus: { baseInr: 10, perKmInr: 1.2, maxCapInr: 25 },
  Metro: { baseInr: 10, perKmInr: 2.2, maxCapInr: 60 },
  Train: { baseInr: 10, perKmInr: 1.0, maxCapInr: 30 },
  Car: { baseInr: 30, perKmInr: 7.2 },
  Mix: { baseInr: 15, perKmInr: 2.6 }
};

export function calculateModeFareInr(mode: VehicleMode, distanceKm: number): number {
  if (mode === 'Walk' || distanceKm <= 0) return 0;
  const rate = FARE_RATES_INR[mode];
  const raw = Math.round(rate.baseInr + distanceKm * rate.perKmInr);
  const capped = rate.maxCapInr !== undefined ? Math.min(raw, rate.maxCapInr) : raw;
  return Math.max(rate.baseInr, capped);
}

export function calculateRouteCostInr(
  vehicle: VehicleMode,
  steps: RouteStep[],
  totalDistanceKm: number,
  variantMultiplier = 1.0
): number {
  if (vehicle === 'Walk') return 0;
  if (vehicle !== 'Mix') {
    return Math.round(calculateModeFareInr(vehicle, totalDistanceKm) * variantMultiplier);
  }
  const activeSteps = steps.filter((s) => s.mode !== 'Walk');
  if (activeSteps.length === 0) return 0;
  const totalActiveMin = Math.max(
    1,
    activeSteps.reduce((acc, s) => acc + s.minutes, 0)
  );
  const totalInr = activeSteps.reduce((sum, step) => {
    const stepKm = totalDistanceKm * (step.minutes / totalActiveMin);
    return sum + calculateModeFareInr(step.mode, stepKm);
  }, 0);
  return Math.round(totalInr * variantMultiplier);
}
