import { DelhiPlace } from '../data/places';
import { calculateRouteCostInr } from './fares';
import { Route, RouteLabel, RouteStep, VehicleMode } from './types';

function haversineKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const r = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return r * c;
}

function buildDelhiCorridorPath(
  start: DelhiPlace,
  end: DelhiPlace,
  label: RouteLabel
): [number, number][] {
  const isGurugramStart = start.lat < 28.52 && start.lng < 77.12;
  const isGurugramEnd = end.lat < 28.52 && end.lng < 77.12;
  const isNoidaStart = start.lng > 77.28;
  const isNoidaEnd = end.lng > 77.28;

  if ((isGurugramStart && isNoidaEnd) || (isNoidaStart && isGurugramEnd)) {
    const forward = isGurugramStart;
    const gPlace = forward ? start : end;
    const nPlace = forward ? end : start;

    let corridor: [number, number][];
    if (label === 'Fastest') {
      corridor = [
        [gPlace.lat, gPlace.lng],
        [28.4722, 77.0725],
        [28.5042, 77.0964],
        [28.5285, 77.1155],
        [28.5468, 77.1260],
        [28.5710, 77.1460],
        [28.5880, 77.1625],
        [28.5765, 77.1850],
        [28.5678, 77.2100],
        [28.5695, 77.2435],
        [28.5735, 77.2630],
        [28.5760, 77.2850],
        [28.5685, 77.3120],
        [nPlace.lat, nPlace.lng]
      ];
    } else if (label === 'Least walking') {
      corridor = [
        [gPlace.lat, gPlace.lng],
        [28.4722, 77.0725],
        [28.5042, 77.0964],
        [28.5468, 77.1260],
        [28.5550, 77.1630],
        [28.5450, 77.1950],
        [28.5495, 77.2260],
        [28.5515, 77.2517],
        [28.5620, 77.2690],
        [28.5720, 77.2880],
        [28.5675, 77.3150],
        [nPlace.lat, nPlace.lng]
      ];
    } else {
      corridor = [
        [gPlace.lat, gPlace.lng],
        [28.4780, 77.0940],
        [28.4980, 77.1420],
        [28.5245, 77.1880],
        [28.5260, 77.2190],
        [28.5365, 77.2580],
        [28.5430, 77.2850],
        [28.5465, 77.3090],
        [28.5565, 77.3240],
        [nPlace.lat, nPlace.lng]
      ];
    }
    return forward ? corridor : corridor.reverse();
  }

  const dLat = end.lat - start.lat;
  const dLng = end.lng - start.lng;
  const perpLat = -dLng;
  const perpLng = dLat;
  const norm = Math.max(0.001, Math.sqrt(perpLat * perpLat + perpLng * perpLng));
  const uLat = perpLat / norm;
  const uLng = perpLng / norm;
  const distDeg = Math.sqrt(dLat * dLat + dLng * dLng);
  const maxDeflection = Math.min(0.032, Math.max(0.004, distDeg * 0.16));
  const sign = label === 'Fastest' ? 1.0 : label === 'Least walking' ? 0.45 : -0.75;
  const fractions = [0.0, 0.12, 0.25, 0.38, 0.52, 0.66, 0.79, 0.90, 1.0];

  return fractions.map((t, idx) => {
    if (idx === 0) return [start.lat, start.lng];
    if (idx === fractions.length - 1) return [end.lat, end.lng];
    const arch = Math.sin(t * Math.PI) * maxDeflection * sign;
    const ripple = Math.sin(t * 2.0 * Math.PI) * (maxDeflection * 0.28);
    const offset = arch + ripple;
    return [start.lat + dLat * t + uLat * offset, start.lng + dLng * t + uLng * offset];
  });
}

function buildSteps(
  start: DelhiPlace,
  end: DelhiPlace,
  vehicle: VehicleMode,
  distanceKm: number,
  label: RouteLabel,
  reachBy?: string
): RouteStep[] {
  const suffix = reachBy ? ` (by ${reachBy})` : '';
  if (vehicle === 'Mix') {
    const metroMin = Math.max(8, Math.round(distanceKm * 1.62));
    const busMin = Math.max(10, Math.round(distanceKm * 1.95));
    if (label === 'Fastest') {
      return [
        { mode: 'Walk', text: `Walk to ${start.name} gate`, minutes: 3 },
        { mode: 'Metro', text: `DMRC Express line toward ${end.area}${suffix}`, minutes: metroMin },
        { mode: 'Bike', text: `Last-mile electric bike to ${end.name}`, minutes: 4 }
      ];
    }
    if (label === 'Least walking') {
      return [
        { mode: 'Bike', text: `Feeder e-rickshaw from ${start.name}`, minutes: 3 },
        { mode: 'Metro', text: `Direct Metro corridor to ${end.area}${suffix}`, minutes: metroMin + 2 },
        { mode: 'Car', text: `Shared auto drop at ${end.name}`, minutes: 3 }
      ];
    }
    return [
      { mode: 'Walk', text: `Walk to ${start.area} stop`, minutes: 4 },
      { mode: 'Bus', text: `DTC AC feeder bus via Ring Road`, minutes: Math.max(6, Math.round(busMin * 0.45)) },
      { mode: 'Metro', text: `Metro interchange to ${end.name}${suffix}`, minutes: Math.max(7, Math.round(metroMin * 0.62)) },
      { mode: 'Walk', text: `Walk to ${end.name} entrance`, minutes: 4 }
    ];
  }

  const perKmFactor =
    vehicle === 'Walk'
      ? 11.6
      : vehicle === 'Bus'
      ? 2.05
      : vehicle === 'Metro'
      ? 1.73
      : vehicle === 'Train'
      ? 1.75
      : vehicle === 'Bike'
      ? 1.70
      : 1.66;
  const baseMin = Math.max(8, Math.round(distanceKm * perKmFactor));
  if (label === 'Fastest') {
    return [
      { mode: vehicle, text: `Via primary corridor from ${start.name}`, minutes: baseMin },
      { mode: vehicle, text: `Arrive at ${end.name}${suffix}`, minutes: 3 }
    ];
  }
  if (label === 'Least walking') {
    return [
      { mode: vehicle, text: `Via Outer Ring Rd from ${start.name}`, minutes: baseMin + 1 },
      { mode: vehicle, text: `Direct drop at ${end.name}${suffix}`, minutes: 2 }
    ];
  }
  return [
    { mode: vehicle, text: `Toll-free arterial route from ${start.area}`, minutes: baseMin + 2 },
    { mode: vehicle, text: `Arrive at ${end.name}${suffix}`, minutes: 2 }
  ];
}

// Stable async signature returning 3 realistic routes after a 600ms delay
export async function getRoutes(
  start: DelhiPlace,
  end: DelhiPlace,
  vehicle: VehicleMode,
  reachBy?: string
): Promise<Route[]> {
  await new Promise((resolve) => setTimeout(resolve, 600));

  const straightKm = Math.max(1.2, haversineKm(start.lat, start.lng, end.lat, end.lng));
  const windingFactor = straightKm >= 20.0 ? 1.455 : straightKm >= 10.0 ? 1.38 : 1.30;
  const baseRoadKm = Math.round(straightKm * windingFactor * 10) / 10;

  const fastestKm = baseRoadKm;
  const leastWalkKm = Math.round(baseRoadKm * 1.02 * 10) / 10;
  const cheapestKm = Math.round(baseRoadKm * 1.05 * 10) / 10;

  const fastestSteps = buildSteps(start, end, vehicle, fastestKm, 'Fastest', reachBy);
  const leastWalkSteps = buildSteps(start, end, vehicle, leastWalkKm, 'Least walking', reachBy);
  const cheapestSteps = buildSteps(start, end, vehicle, cheapestKm, 'Cheapest', reachBy);

  const fastestMinutes = fastestSteps.reduce((s, step) => s + step.minutes, 0);
  const leastWalkMinutes = Math.max(fastestMinutes + 1, leastWalkSteps.reduce((s, step) => s + step.minutes, 0));
  const cheapestMinutes = Math.max(fastestMinutes + 3, cheapestSteps.reduce((s, step) => s + step.minutes, 0));

  const fastestCost = calculateRouteCostInr(vehicle, fastestSteps, fastestKm, 1.0);
  const cheapestCost = Math.min(
    fastestCost > 15 ? fastestCost - 10 : fastestCost,
    calculateRouteCostInr(vehicle, cheapestSteps, fastestKm, 0.88)
  );
  const leastWalkCost = calculateRouteCostInr(vehicle, leastWalkSteps, leastWalkKm, 1.06);

  const isPrivate = vehicle === 'Car' || vehicle === 'Bike' || vehicle === 'Walk';

  return [
    {
      id: `route_fastest_${vehicle.toLowerCase()}`,
      label: 'Fastest',
      minutes: fastestMinutes,
      costInr: fastestCost,
      distanceKm: fastestKm,
      transfers: isPrivate ? 0 : 1,
      walkMeters: isPrivate ? (vehicle === 'Walk' ? Math.round(fastestKm * 1000) : 50) : 320,
      crowd: 'Low',
      dataLabel: 'TIMETABLE',
      reliability: 9,
      steps: fastestSteps,
      path: buildDelhiCorridorPath(start, end, 'Fastest')
    },
    {
      id: `route_least_walk_${vehicle.toLowerCase()}`,
      label: 'Least walking',
      minutes: leastWalkMinutes,
      costInr: leastWalkCost,
      distanceKm: leastWalkKm,
      transfers: 0,
      walkMeters: vehicle === 'Walk' ? Math.round(leastWalkKm * 960) : 40,
      crowd: 'Medium',
      dataLabel: 'ESTIMATED',
      reliability: 9,
      steps: leastWalkSteps,
      path: buildDelhiCorridorPath(start, end, 'Least walking')
    },
    {
      id: `route_cheapest_${vehicle.toLowerCase()}`,
      label: 'Cheapest',
      minutes: cheapestMinutes,
      costInr: cheapestCost,
      distanceKm: cheapestKm,
      transfers: isPrivate ? 0 : 1,
      walkMeters: isPrivate ? (vehicle === 'Walk' ? Math.round(cheapestKm * 1040) : 80) : 480,
      crowd: 'High',
      dataLabel: 'SAMPLE',
      reliability: 8,
      steps: cheapestSteps,
      path: buildDelhiCorridorPath(start, end, 'Cheapest')
    }
  ];
}
