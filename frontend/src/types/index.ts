export type EventItem = {
  id: number;
  campaignId?: number;
  name: string;
  type: string;
  eventDate: string;
  location: string;
  description?: string;
  objective?: string;
  budget?: number | string;
  expectedParticipants?: number;
  responsible?: string;
  status: string;
};

export type Campaign = {
  id: number;
  name: string;
};

export type PeakAttendanceHour = {
  hour: number;
  attendees: number;
};

export type AnalyticsOverview = {
  registered: number;
  attendees: number;
  attendance_rate?: number | null;
  conversions: number;
  conversion_rate?: number | null;
  new_participants: number;
  recurrent_participants: number;
  nps?: number | null;
  average_rating?: number | null;
  peakAttendanceHours: PeakAttendanceHour[];
};

export type EventImpactScore = {
  eventId: number;
  score?: number | null;
  status: 'CALCULATED' | 'PARTIAL_DATA' | 'INSUFFICIENT_DATA';
  metrics: Partial<Record<'attendance' | 'engagement' | 'satisfaction' | 'conversion' | 'recurrence', number | null>>;
  weights?: Partial<Record<'attendance' | 'engagement' | 'satisfaction' | 'conversion' | 'recurrence', number>>;
  appliedWeights?: Partial<Record<'attendance' | 'engagement' | 'satisfaction' | 'conversion' | 'recurrence', number>>;
  missingMetrics?: string[];
  formula: string;
  metricDefinitions?: Partial<Record<'attendance' | 'engagement' | 'satisfaction' | 'conversion' | 'recurrence', string>>;
};

export type EventInsight = {
  eventId: number;
  generatedByAI: boolean;
  provider: 'gemini' | string;
  model: string;
  status: 'GENERATED' | 'NOT_CONFIGURED' | 'INSUFFICIENT_DATA';
  message: string;
  summary: string | null;
  insights: string[];
  anomalies: string[];
  recommendations: string[];
};

export type Participant = {
  id: number;
  firstName: string;
  lastName: string;
  phone?: string;
  email: string;
  city: string;
  ageRange: string;
  productPreference: string;
  communicationConsent: boolean;
  consentAt?: string;
  registrationSource: string;
};

export type Activity = {
  id: number;
  eventId: number;
  participantId?: number;
  name: string;
  type: string;
  description?: string;
  startAt?: string;
  endAt?: string;
  quantity?: number;
  result?: string;
  status: string;
};

export type Product = {
  id: number;
  name: string;
  category: string;
  flavor?: string;
  presentation?: string;
  status: string;
};

export type Attendance = {
  id: number;
  eventId: number;
  participantId: number;
  status: string;
  registeredAt?: string;
  checkInAt?: string;
  checkInMethod?: string;
};

export type OverviewMetrics = {
  registered: number;
  attendees: number;
  attendance_rate?: number | null;
  conversions: number;
  conversion_rate?: number | null;
  new_participants: number;
  recurrent_participants: number;
  average_rating?: number | null;
  nps?: number | null;
  peakAttendanceHours: PeakAttendanceHour[];
};

export type EventPerformance = {
  event_id: number;
  event_name: string;
  event_date: string;
  campaign_name?: string;
  registered: number;
  attended: number;
  attendance_rate_pct: number;
  participation_rate_pct: number;
  conversion_rate_pct: number;
  avg_overall_satisfaction: number;
  nps: number;
  cost_per_attendee: number;
};

export type ProductAnalysis = {
  event_name: string;
  product_name: string;
  category: string;
  total_interactions: number;
  total_tried: number;
  avg_rating: number;
  intent_low: number;
  intent_medium: number;
  intent_high: number;
  avg_intent_score: number;
};

export type DashboardSummary = {
  overview: OverviewMetrics;
  events: EventPerformance[];
  productRanking: ProductAnalysis[];
  recentEvents: EventItem[];
};
