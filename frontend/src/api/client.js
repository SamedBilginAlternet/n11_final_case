import axios from 'axios';

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api';

export const api = axios.create({ baseURL: baseURL.endsWith('/api') ? baseURL.slice(0, -4) : baseURL });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('n11.token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('n11.token');
      localStorage.removeItem('n11.user');
    }
    return Promise.reject(error);
  },
);
