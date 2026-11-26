import client from './client';

export type Allergy = 'OILS' | 'NUTS' | 'LATEX';
export type Pressure = 'LIGHT' | 'MEDIUM' | 'FIRM';
export interface CustomAnswer {
  questionId: number;
  prompt?: string;
  answer: string;
}
export interface HealthAnswers {
  pregnant: boolean | null;
  allergies: Allergy[];
  allergyDetails: string;
  recentInjuryOrSurgery: boolean | null;
  injuryDetails: string;
  skinCondition: boolean | null;
  skinDetails: string;
  heartCondition: boolean | null;
  medications: string;
  pressure: Pressure;
  avoidAreas: string;
  notes: string;
  custom: CustomAnswer[];
}
export interface HealthQuestion {
  id: number;
  prompt: string;
}
export interface GuestHealthForm {
  bookingId: number;
  enabled: boolean;
  editable: boolean;
  message?: string;
  terms: string;
  questions: HealthQuestion[];
  status: 'NONE' | 'SUBMITTED' | 'UNREADABLE';
  answers?: HealthAnswers;
  prefilled: boolean;
  prefilledFrom?: string;
  updatedAt?: string;
}
export interface SpaHealthForm {
  enabled: boolean;
  status: 'NONE' | 'SUBMITTED' | 'UNREADABLE';
  attention: string[];
  answers?: HealthAnswers;
  terms?: string;
  updatedAt?: string;
  consentedAt?: string;
}

export const blankAnswers = (): HealthAnswers => ({
  pregnant: null,
  allergies: [],
  allergyDetails: '',
  recentInjuryOrSurgery: null,
  injuryDetails: '',
  skinCondition: null,
  skinDetails: '',
  heartCondition: null,
  medications: '',
  pressure: 'MEDIUM',
  avoidAreas: '',
  notes: '',
  custom: []
});

export const getMyHealthForm = async (booking: string | number) =>
  (await client.get<GuestHealthForm>(`/api/me/bookings/${booking}/health-form`)).data;

export const submitHealthForm = async (
  booking: string | number,
  answers: HealthAnswers,
  confirmedCurrent: boolean
) =>
  (
    await client.put<GuestHealthForm>(`/api/me/bookings/${booking}/health-form`, {
      answers,
      consent: true,
      confirmedCurrent
    })
  ).data;

export const withdrawHealthForm = async (booking: string | number) => {
  await client.delete(`/api/me/bookings/${booking}/health-form`);
};

export const getSpaHealthForm = async (spa: string, booking: string | number) =>
  (await client.get<SpaHealthForm>(`/api/owner/spas/${spa}/desk/bookings/${booking}/health-form`)).data;

export const getHealthQuestions = async (spa: string, service: string) =>
  (await client.get<HealthQuestion[]>(`/api/owner/spas/${spa}/services/${service}/health-questions`)).data;

export const saveHealthQuestions = async (spa: string, service: string, prompts: string[]) =>
  (await client.put<HealthQuestion[]>(`/api/owner/spas/${spa}/services/${service}/health-questions`, { prompts }))
    .data;

export const getHealthTerms = async (spa: string) =>
  (await client.get<{ terms: string }>(`/api/owner/spas/${spa}/health-terms`)).data.terms;

export const saveHealthTerms = async (spa: string, terms: string) =>
  (await client.put<{ terms: string }>(`/api/owner/spas/${spa}/health-terms`, { terms })).data.terms;
