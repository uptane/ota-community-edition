/**
 * OTA Community Edition Axios Configuration
 * 
 * Simplified HTTP client configuration without authentication.
 * CE does not require auth tokens - requests go directly to the backend.
 */

import axios from 'axios';
import { Platform } from 'quasar';

// Default timeout for API requests
axios.defaults.timeout = 50000;

// Request interceptor - no auth token injection needed for CE
axios.interceptors.request.use(function(config) {
  // Return config as-is, no auth headers needed
  return { ...config };
});

// Response interceptor - simplified error handling for CE
axios.interceptors.response.use(
  function(response) {
    return response;
  },
  function(error) {
    // Log errors but don't try to handle auth errors (there is no auth in CE)
    const statusCode = ((error || {}).response || {}).status;
    if (statusCode) {
      console.log(`[OTA CE] API error: ${statusCode}`, error.config?.url);
    }
    return Promise.reject(error);
  },
);

export default ({ Vue }) => {
  Vue.prototype.$axios = axios;
};
