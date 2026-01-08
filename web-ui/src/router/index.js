/**
 * OTA Community Edition Router
 * 
 * Simplified router without authentication guards.
 * CE does not require authentication - all routes are accessible.
 */

import Vue from 'vue';
import VueRouter from 'vue-router';

import routes from './routes';
import store from '../store';
import { AuthService } from '../services/auth.service';

Vue.use(VueRouter);

const Router = new VueRouter({
  scrollBehavior: () => ({ y: 0 }),
  routes,
  mode: process.env.VUE_ROUTER_MODE,
  base: process.env.VUE_ROUTER_BASE,
});

// Simple navigation guard for CE - no authentication required
Router.beforeEach(async (to, from, next) => {
  // Clean up any OAuth-style query params that might be leftover
  const oldPath = to.path.replace('/', '');
  const params = oldPath.split('&');
  if (params.length > 1 && params.some((h) => h.startsWith('code=')) && params.some((h) => h.startsWith('state='))) {
    return next({ path: to.path.replace(oldPath, '') });
  }

  // Redirect login/register/logout routes to dashboard in CE mode
  if (to.name === 'login' || to.name === 'register' || to.name === 'logout' || to.name === 'welcome') {
    return next({ name: 'dashboard' });
  }

  // Check for restricted features (features that don't exist in CE)
  if (to.matched.some((record) => {
    if (!record.meta || record.meta.restrictedFeature === undefined) {
      return false;
    }
    if (typeof record.meta.restrictedFeature === 'function') {
      return record.meta.restrictedFeature();
    }
    return record.meta.restrictedFeature;
  })) {
    // Redirect to dashboard if feature is restricted
    return next({ name: 'dashboard' });
  }

  // Allow all other navigation
  next();
});

export default function(/* { store, ssrContext } */) {
  return Router;
}
