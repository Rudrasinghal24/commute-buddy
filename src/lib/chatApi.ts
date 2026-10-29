import { DelhiPlace } from '../data/places';
import { Route, VehicleMode } from './types';

export interface ChatContext {
  start: DelhiPlace | null;
  end: DelhiPlace | null;
  vehicle: VehicleMode;
  selectedRoute: Route | null;
  reachBy?: string;
}

// Later call our backend /api/chat, never put the Gemini key in the app.
export async function sendMessage(
  message: string,
  context: ChatContext
): Promise<string> {
  // Later call our backend /api/chat, never put the Gemini key in the app.
  await new Promise((resolve) => setTimeout(resolve, 800));

  const startName = context.start?.name ?? 'your starting point';
  const endName = context.end?.name ?? 'your destination';
  const route = context.selectedRoute;
  const duration = route?.minutes ?? 24;
  const cost = route?.costInr ?? 35;
  const q = message.trim().toLowerCase();

  if (q.includes('miss') && q.includes('metro')) {
    return `Next DMRC train from ${startName} toward ${endName} departs in 6 min. Total trip time only shifts to ${duration + 6} min with the same ₹${cost} fare.`;
  }
  if (q.includes('safe') && q.includes('night')) {
    return `Stations between ${startName} and ${endName} have CCTV, CISF security, and lit exits until 11:30 PM. Prefer the first coach and main gate exits after 9:30 PM.`;
  }
  if (q.includes('leave by') || q.includes('what time')) {
    return `Leave ${duration + 5} min before your target arrival from ${startName} (${duration} min ${context.vehicle} route plus 5 min buffer) to reach ${endName} on time.`;
  }
  if (route) {
    return `Your ${route.label} ${context.vehicle} route from ${startName} to ${endName} takes ${route.minutes} min (₹${route.costInr}, ${route.transfers} transfers, ${route.walkMeters} m walk, Reliable ${route.reliability}/10).`;
  }
  return `Select your trip start and destination in Delhi NCR to get metro frequency, night safety, and departure buffer details.`;
}
