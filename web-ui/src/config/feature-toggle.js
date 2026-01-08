/**
 * OTA Community Edition Feature Toggle
 * 
 * Simplified feature toggle for CE mode.
 * CE mode grants full access to all basic features without role-based restrictions.
 * Some features that require proprietary backends are disabled.
 */

// CE default role - all basic features enabled
const CE_DEFAULT_ROLE = 'ce-admin';

// Features that are NOT available in Community Edition
const CE_DISABLED_FEATURES = [
  'view-offline-update',      // Lockboxes/offline updates - commercial feature
  'view-debug-page',          // Debug page - internal feature
  'view-api-client-manager',  // API client manager - requires accounts service
  'view-remote-access-manager', // Remote access manager - requires RAS service
  'use-remote-access',        // Remote access - requires RAS service
];

// Configuration map for feature toggle
// In CE mode, most features are enabled by including CE_DEFAULT_ROLE
export const FEATURE_TOGGLE_CONFIG = {
  // Device Operations - all enabled in CE
  'provision-device': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'manage-credentials': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'delete-device': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'update-device-info': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'view-devices-list': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'view-device-detail': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'view-device-history': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'view-device-events': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'manage-device-fleets': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },

  // Device Update Operations - all enabled in CE
  'create-device-update': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'view-device-pending-update': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'cancel-device-pending-update': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'view-device-update-history': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },

  // Fleet Operations - all enabled in CE
  'create-fleet': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'view-fleet': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'manage-fleet': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'manage-fleet-update': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'delete-fleet': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },

  // Package Operations - all enabled in CE
  'create-package': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'view-packages-list': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'view-package-detail': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'modify-package': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
  'delete-package': {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },

  // Disabled features in CE - empty roles means no access
  'view-offline-update': {
    roles: [], // Disabled in CE
    scopes: [],
  },
  'view-debug-page': {
    roles: [], // Disabled in CE
    scopes: [],
  },
  'view-api-client-manager': {
    roles: [], // Disabled in CE
    scopes: [],
  },
  'view-remote-access-manager': {
    roles: [], // Disabled in CE
    scopes: [],
  },
  'use-remote-access': {
    roles: [], // Disabled in CE
    scopes: [],
  },

  // Hibernation - enabled in CE
  hibernation: {
    roles: [CE_DEFAULT_ROLE],
    scopes: [],
  },
};

/**
 * Check if a feature is accessible in CE mode
 * @param {string} featureId - The feature identifier
 * @returns {boolean} - True if feature is accessible
 */
export function canAccessFeature(featureId) {
  // If feature is explicitly disabled, return false
  if (CE_DISABLED_FEATURES.includes(featureId)) {
    return false;
  }

  // Check if feature exists in config
  const featureConfig = FEATURE_TOGGLE_CONFIG[featureId];
  if (!featureConfig) {
    // Unknown features are disabled by default
    return false;
  }

  // In CE mode, if the feature has CE_DEFAULT_ROLE, it's enabled
  if (featureConfig.roles && featureConfig.roles.includes(CE_DEFAULT_ROLE)) {
    return true;
  }

  // If no roles specified, feature is disabled
  return false;
}
