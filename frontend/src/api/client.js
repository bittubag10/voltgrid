const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export async function apiRequest(endpoint, options = {}) {
  const token = localStorage.getItem('voltgrid_token');
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers,
  };

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers,
  });

  if (response.status === 204) {
    return null;
  }

  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }

  if (!response.ok) {
    let errorMsg = data?.message || data?.error;
    if (data?.fieldErrors && typeof data.fieldErrors === 'object' && Object.keys(data.fieldErrors).length > 0) {
      errorMsg = Object.values(data.fieldErrors).join(', ');
    }
    if (!errorMsg) {
      errorMsg = typeof data === 'string' ? data : 'API Request failed';
    }
    if (response.status === 401) {
      localStorage.removeItem('voltgrid_token');
      localStorage.removeItem('voltgrid_user');
      window.dispatchEvent(new Event('voltgrid_auth_change'));
    }
    const err = new Error(errorMsg);
    err.status = response.status;
    err.data = data;
    throw err;
  }

  return data;
}

export const api = {
  // Auth
  login: (email, password) =>
    apiRequest('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  register: (name, email, password, role) =>
    apiRequest('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ name, email, password, role }),
    }),

  // Vehicles (Driver)
  listVehicles: () => apiRequest('/api/vehicles?page=0&size=50'),
  createVehicle: (payload) =>
    apiRequest('/api/vehicles', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  deleteVehicle: (id) =>
    apiRequest(`/api/vehicles/${id}`, {
      method: 'DELETE',
    }),

  // Owner Stations & Chargers
  listOwnerStations: () => apiRequest('/api/owner/stations?page=0&size=50'),
  getOwnerAnalytics: () => apiRequest('/api/owner/analytics'),
  createStation: (payload) =>
    apiRequest('/api/owner/stations', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  changeStationStatus: (stationId, status) =>
    apiRequest(`/api/owner/stations/${stationId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),
  listOwnerChargers: (stationId) =>
    apiRequest(`/api/owner/stations/${stationId}/chargers`),
  createCharger: (stationId, payload) =>
    apiRequest(`/api/owner/stations/${stationId}/chargers`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  changeChargerStatus: (stationId, chargerId, status) =>
    apiRequest(`/api/owner/stations/${stationId}/chargers/${chargerId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),

  // Public stations
  listPublicStations: () => apiRequest('/api/stations?page=0&size=50'),

  // Smart Recommendations (Driver)
  getRecommendations: (payload) =>
    apiRequest('/api/recommendations/chargers', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  // Highway Trip Planner (Dijkstra)
  planTrip: (payload) =>
    apiRequest('/api/trips/plan', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  // Grounded AI Trip Co-Pilot
  parseTripPrompt: (prompt, vehicleId = null, autoPlan = true) =>
    apiRequest('/api/ai/parse-trip', {
      method: 'POST',
      body: JSON.stringify({ prompt, vehicleId, autoPlan }),
    }),

  // Bookings (Driver)
  createBooking: (payload) =>
    apiRequest('/api/bookings', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  listBookings: () => apiRequest('/api/bookings?page=0&size=50'),
  cancelBooking: (id) =>
    apiRequest(`/api/bookings/${id}/cancel`, {
      method: 'POST',
    }),

  // Payment Confirmation & Session Simulator
  confirmPayment: (id) =>
    apiRequest(`/api/bookings/${id}/confirm-payment`, {
      method: 'POST',
    }),
  startSession: (id) =>
    apiRequest(`/api/bookings/${id}/start`, {
      method: 'POST',
    }),
  completeCharging: (id) =>
    apiRequest(`/api/bookings/${id}/charge-complete`, {
      method: 'POST',
    }),
  plugOut: (id) =>
    apiRequest(`/api/bookings/${id}/plug-out`, {
      method: 'POST',
    }),
  simulateOverstay: (id, minutes = 18) =>
    apiRequest(`/api/bookings/${id}/simulate-overstay?minutes=${minutes}`, {
      method: 'POST',
    }),

  // Admin Portal Endpoints
  getAdminOverview: () => apiRequest('/api/admin/overview'),
  listAdminUsers: (page = 0, size = 50) =>
    apiRequest(`/api/admin/users?page=${page}&size=${size}`),
  toggleAdminUserStatus: (userId, enabled) =>
    apiRequest(`/api/admin/users/${userId}/status?enabled=${enabled}`, {
      method: 'PATCH',
    }),
};
