/**
 * OTA Community Edition Routes
 * 
 * Simplified routes without authentication-related pages.
 * CE does not require login/register functionality.
 */

import { canAccessFeature } from 'src/config/feature-toggle.js';

const routes = [
  {
    path: '/',
    component: () => import('layouts/MainLayout.vue'),
    children: [
      { name: 'dashboard', path: '', component: () => import('pages/Dashboard.vue') },
      { name: 'pair-device', path: '/pair-device', component: () => import('pages/Devices.vue') },
      { name: 'devices', path: '/devices', component: () => import('pages/Devices.vue') },
      { name: 'devices-quick-view', path: '/devices/:deviceId', component: () => import('pages/Devices.vue') },
      { name: 'device-detail', path: '/devices/:deviceId/detail', component: () => import('pages/DeviceDetail.vue') },
      { name: 'fleet-detail', path: '/fleets/:fleetId', component: () => import('pages/FleetDetail.vue') },
      { name: 'packages', path: '/packages', component: () => import('pages/Packages.vue') },
      { name: 'about', path: '/about', component: () => import('pages/About.vue') },
      { name: 'fleets', path: '/fleets', component: () => import('pages/Fleets.vue') },
      { name: 'fleet-manager', path: '/fleet-manager', component: () => import('pages/FleetManager.vue') },
      // Remote access - not available in CE
      {
        name: 'remote-access',
        path: '/remote-access',
        component: () => import('pages/RemoteAccess.vue'),
        meta: {
          restrictedFeature: () => !canAccessFeature('use-remote-access'),
        },
      },
      // Lockboxes/offline updates - not available in CE
      {
        name: 'lockboxes',
        path: '/lockboxes',
        component: () => import('pages/Lockboxes.vue'),
        meta: {
          restrictedFeature: () => !canAccessFeature('view-offline-update'),
        },
      },
      // Debug page - not available in CE
      {
        name: 'debug',
        path: '/debug',
        component: () => import('pages/Debug.vue'),
        meta: {
          restrictedFeature: () => !canAccessFeature('view-debug-page'),
        },
      },
    ],
  },
];

// Always leave this as last one - 404 handler
if (process.env.MODE !== 'ssr') {
  routes.push({
    path: '*',
    component: () => import('pages/Error404.vue'),
  });
}

export default routes;
