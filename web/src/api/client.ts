import { EmergencyRequest, Volunteer, ReliefResource, ActivityLog, Assignment, UserRole } from '../types';

const API_BASE = '/api';

function getAuthHeaders(): HeadersInit {
  const token = localStorage.getItem('resqhunt_token');
  const headers: HeadersInit = {
    'Content-Type': 'application/json'
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

export const api = {
  // Auth
  async login(email: string, password: string) {
    const res = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || 'Login failed');
    return data;
  },

  async register(email: string, password: string, fullName: string, role: UserRole, phone?: string) {
    const res = await fetch(`${API_BASE}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password, fullName, role, phone })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || 'Registration failed');
    return data;
  },

  async getMe() {
    const res = await fetch(`${API_BASE}/auth/me`, {
      headers: getAuthHeaders()
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || 'Failed to fetch current user');
    return data.user;
  },

  // Incidents
  async getIncidents(params?: { category?: string; severity?: string; status?: string; search?: string }) {
    const query = new URLSearchParams();
    if (params?.category) query.set('category', params.category);
    if (params?.severity) query.set('severity', params.severity);
    if (params?.status) query.set('status', params.status);
    if (params?.search) query.set('search', params.search);

    const res = await fetch(`${API_BASE}/incidents?${query.toString()}`, {
      headers: getAuthHeaders()
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || 'Failed to fetch incidents');
    return data;
  },

  async getIncidentById(id: string) {
    const res = await fetch(`${API_BASE}/incidents/${id}`, {
      headers: getAuthHeaders()
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || 'Failed to fetch incident');
    return data.incident as EmergencyRequest;
  },

  async updateIncidentStatus(id: string, status: string, note?: string) {
    const res = await fetch(`${API_BASE}/incidents/${id}/status`, {
      method: 'PATCH',
      headers: getAuthHeaders(),
      body: JSON.stringify({ status, note })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || 'Failed to update status');
    return data.incident as EmergencyRequest;
  },

  async overridePriority(id: string, newScore: number, newCategory: string, reason: string) {
    const res = await fetch(`${API_BASE}/incidents/${id}/priority-override`, {
      method: 'PATCH',
      headers: getAuthHeaders(),
      body: JSON.stringify({ newScore, newCategory, reason })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || 'Failed to override priority');
    return data.incident as EmergencyRequest;
  },

  // Direct SOS Creation (For Testing & Coordinator creation)
  async createSos(payload: {
    category: string;
    severity: string;
    affectedCount: number;
    description: string;
    latitude?: number;
    longitude?: number;
    locationAddress?: string;
  }) {
    const res = await fetch(`${API_BASE}/sos`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(payload)
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || 'Failed to create emergency request');
    return data;
  },

  // Volunteers
  async getVolunteers(params?: { available?: boolean; skill?: string }) {
    const query = new URLSearchParams();
    if (params?.available !== undefined) query.set('available', String(params.available));
    if (params?.skill) query.set('skill', params.skill);

    const res = await fetch(`${API_BASE}/volunteers?${query.toString()}`, {
      headers: getAuthHeaders()
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || 'Failed to fetch volunteers');
    return data.volunteers as Volunteer[];
  },

  // Assignments
  async getAssignments() {
    const res = await fetch(`${API_BASE}/assignments`, {
      headers: getAuthHeaders()
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || 'Failed to fetch assignments');
    return data.assignments as Assignment[];
  },

  async createAssignment(incidentId: string, volunteerId: string, notes?: string) {
    const res = await fetch(`${API_BASE}/assignments`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ incidentId, volunteerId, notes })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || 'Failed to assign volunteer');
    return data.assignment as Assignment;
  },

  async updateAssignmentStatus(id: string, status: string, notes?: string) {
    const res = await fetch(`${API_BASE}/assignments/${id}/status`, {
      method: 'PATCH',
      headers: getAuthHeaders(),
      body: JSON.stringify({ status, notes })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || 'Failed to update assignment status');
    return data.assignment as Assignment;
  },

  // Resources
  async getResources() {
    const res = await fetch(`${API_BASE}/resources`, {
      headers: getAuthHeaders()
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || 'Failed to fetch resources');
    return data.resources as ReliefResource[];
  },

  async allocateResource(resourceId: string, incidentId: string, quantity: number, notes?: string) {
    const res = await fetch(`${API_BASE}/resources/allocate`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ resourceId, incidentId, quantity, notes })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || 'Failed to allocate resource');
    return data;
  },

  // Activity Logs
  async getActivityLogs() {
    const res = await fetch(`${API_BASE}/activity`, {
      headers: getAuthHeaders()
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || 'Failed to fetch activity logs');
    return data.logs as ActivityLog[];
  },

  // Demo Reset
  async resetDemoData() {
    const res = await fetch(`${API_BASE}/demo/reset`, {
      method: 'POST'
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || 'Failed to reset demo data');
    return data;
  }
};
