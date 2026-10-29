export type VehicleMode = 'Mix' | 'Metro' | 'Bus' | 'Train' | 'Car' | 'Bike' | 'Walk';

export type RouteLabel = 'Fastest' | 'Cheapest' | 'Least walking';

export type CrowdLevel = 'Low' | 'Medium' | 'High';

export type DataLabel = 'TIMETABLE' | 'SAMPLE' | 'ESTIMATED';

export interface RouteStep {
  mode: VehicleMode;
  text: string;
  minutes: number;
}

export interface Route {
  id: string;
  label: RouteLabel;
  minutes: number;
  costInr: number;
  distanceKm: number;
  transfers: number;
  walkMeters: number;
  crowd: CrowdLevel;
  dataLabel: DataLabel;
  reliability: number; // 0-10
  steps: RouteStep[];
  path: [number, number][]; // [lat, lng][]
}
