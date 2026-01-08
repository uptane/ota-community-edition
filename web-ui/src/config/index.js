/**
 * OTA Community Edition Configuration
 * 
 * Main configuration for the OTA CE web interface.
 */

/* Main config */
export const APP_TITLE = 'OTA Community Edition';
export const APP_LAYOUT = 'atsgarage';
export const APP_LOCALSTORAGE = 'OTA-Community-Edition';

/**
 * API Endpoints Configuration
 * 
 * These endpoints are proxied through nginx to the OTA CE backend services:
 * - /api/v1/user_repo/* -> reposerver
 * - /device-registry/api/v1/* -> director
 * - /api/v1/admin/* -> director
 * - /api/treehub/* -> treehub
 */

/* Update Operations */
export const API_SEND_DEVICE_UPDATE = '/api/metadata/targets/update';
export const API_GET_MULTI_TARGET_UPDATE_INDENTIFIER = '/api/v1/multi_target_updates';
export const API_CREATE_MULTI_TARGET_UPDATE = '/api/v1/multi_target_updates';
export const API_FETCH_MULTI_TARGET_UPDATES = '/api/v1/assignments';
export const API_APPLY_MULTI_TARGET_UPDATES = '/api/v1/assignments';
export const API_CANCEL_MULTI_TARGET_UPDATE = '/api/v1/assignments';
export const API_CANCEL_UPDATE_NEW = '/api/v1/updates/devices/:device-id/:update-id';
export const API_GET_UPDATES_NEW = '/api/v1/updates/devices/:device-id';
export const API_CANCEL_IN_FLIGHT_UPDATE = '/api/v1/assignments/:device-id';
export const API_CREATE_SCHEDULED_UPDATES = '/api/v1/admin/devices/{deviceUuid}/scheduled-updates';
export const API_CREATE_UPDATES_NEW = '/api/v1/updates/devices';
export const API_CREATE_SCHEDULED_UPDATES_NEW = '/api/v1/updates/devices/{deviceUuid}';
export const API_GET_SCHEDULED_UPDATES_STATUS = '/api/v1/admin/devices/{deviceUuid}/scheduled-updates';
export const API_CANCEL_SCHEDULED_UPDATE = '/api/v1/admin/devices/{deviceUuid}/scheduled-updates/{updateId}';
export const API_SET_LOCKBOX_EXPIRATION = '/api/v1/user_repo/targets/expire/not-before';
export const API_FETCH_DEVICE_UPDATE_INSTALATION_REPORTS = '/device-registry/api/v1/devices/{deviceUuid}/installation_reports';
export const API_FETCH_DEVICE_UPDATE_EVENTS = '/device-registry/api/v1/devices/{deviceUuid}/events';

export const API_FETCH_DEVICE_CORE_UPDATE = '/api/core/devices';
export const API_NAMESPACE_SETUP_STEPS = '/user/setup';

/* User Operations - CE mode uses local storage instead of API */
export const API_USER_DETAILS = '/user/profile';
export const API_USER_CONTRACTS = '/user/contracts';
export const API_USER_UPDATE = '/user/profile';
export const API_USER_CHANGE_PASSWORD = '/user/change_password';
export const API_USER_ACTIVE_DEVICE_COUNT = '/api/v1/active_device_count';
export const API_USER_DEVICES_SEEN = '/api/v1/auditor/devices_seen_in';
// User/account endpoints - not used in CE mode
export const API_USER_CREATE = '/api/accounts/users';
export const API_LEAD_CREATE = '/api/accounts/leads';
export const API_USER_FETCH = '/api/accounts/users';
export const API_USER_UPDATE_METADATA = '/api/accounts/users/{userID}/metadata';
export const API_USER_GET_METADATA = '/api/accounts/users/{userID}/metadata';
export const API_USER_GET_CREDENTIALS_ZIP = '/api/accounts/users/{userID}/keys/credentials.zip';
export const API_USER_KEYS_PROVISION_ROTATE = '/api/accounts/users/{userID}/keys/provision/rotate';
export const API_USER_KEYS_GARAGE_TOOLS_ROTATE = '/api/accounts/users/{userID}/keys/garage-tools/rotate';
export const API_USER_REPO_TARGETS = '/api/v1/user_repo/targets/';
export const API_USER_REPO_SET_COMPATIBILITY = '/api/v1/user_repo/proprietary-custom/';
export const API_USER_REPO_ROTATE = '/api/v1/user_repo/root/rotate';

// Role/permission endpoints - not used in CE mode
export const API_CREATE_ROLES = '/api/accounts/roles';
export const API_LIST_ROLES = '/api/accounts/roles';
export const API_GET_ROLE_BY_ID = '/api/accounts/roles/{roleID}';
export const API_ASSIGN_ROLES_TO_USER = '/api/accounts/users/{userID}/roles';
export const API_GET_USER_ROLES = '/api/accounts/users/{userID}/roles';
export const API_ASSIGN_PERMISSIONS_TO_ROLE = '/api/accounts/roles/{roleID}/permissions';
export const API_LIST_PERMISSIONS = '/api/accounts/permissions';
export const API_GET_ROLE_PERMISSIONS = '/api/accounts/roles/{roleID}/permissions';

// Organization endpoints - not used in CE mode (single namespace)
export const API_ACCOUNT_GET_ORGANIZATION = '/api/accounts/organizations/share';
export const API_ACCOUNT_CREATE_ORGANIZATION = '/api/accounts/organizations/share';
export const API_ACCOUNT_ADD_ORGANIZATION_USERS = '/api/accounts/organizations/share/{org_id}/users';
export const API_ACCOUNT_GET_ORGANIZATION_USERS = '/api/accounts/organizations/share/{org_id}/users';
export const API_ACCOUNT_UPDATE_ORGANIZATION_USER = '/api/accounts/organizations/share/{org_id}/users/{user_namespace}';
export const API_ACCOUNT_UPDATE_ORGANIZATION = '/api/accounts/organizations/share';
export const API_ACCOUNT_DELETE_ORGANIZATION = '/api/accounts/organizations/share';
export const API_ACCOUNT_GET_GUEST_ORGANIZATIONS = '/api/accounts/organizations/guest';
export const API_ACCOUNT_LOGIN_ORGANIZATION_GUEST = '/api/accounts/organizations/guest';
export const API_ACCOUNT_GET_ORGANIZATION_GUEST_PROFILES = '/api/accounts/organizations/guest_profiles';
export const API_ACCOUNT_ORGANIZATION_GUEST_LOGIN = '/api/accounts/organizations/guest/{org_id}';

export const API_FEATURES_FETCH = '/api/v1/features';

/* Device Operations */
export const API_DEVICES_SEARCH = '/device-registry/api/v1/devices';
export const API_FLEET_DEVICES_SEARCH = '/device-registry/api/v1/devices';
export const API_DEVICES_NETWORK_INFO = '/device-registry/api/v1/devices';
export const API_DIRECTOR_DEVICES_SEARCH = '/api/v1/admin/devices';
export const API_DIRECTOR_DEVICES_INSTALLED_PACKAGES = 'api/v1/admin/devices/list-installed-targets';
export const API_DEVICES_GET_CREDENTIALS = '/api/provision/create-device';
export const API_DEVICES_CREATE = '/device-registry/api/v1/devices';
export const API_DEVICES_UPDATE = '/device-registry/api/v1/devices';
export const API_DEVICES_DELETE = '/device-registry/api/v1/devices';
export const API_DEVICES_DEVICE_DETAILS = '/device-registry/api/v1/devices';
export const API_DEVICES_DIRECTOR_DEVICE = '/api/v1/admin/devices';
export const API_DEVICES_UPDATE_DATA = '/device-registry/api/v1/devices';

// Metrics - may not be available in CE
export const API_DEVICE_METRICS_NAMES_FETCH = '/api/alpha1/device-metrics';
export const API_DEVICE_METRICS_FETCH = '/api/alpha1/device-metrics/devices';
export const API_DEVICE_APPROVAL_PENDING_CAMPAIGNS = '/api/v2/device';
export const API_DEVICES_HIBERNATE = '/device-registry/api/v1/devices/:uuid/hibernation';
export const API_DEVICES_PROVISIONING_CODE_CLAIM = '/api/accounts/prov-claim';

export const API_FLEET_METRICS_NAMES_FETCH = '/api/alpha1/fleet/metric-names';
export const API_FLEET_METRICS_FETCH = '/api/alpha1/fleet/metrics';

export const API_UPDATES_SEARCH = '/api/v2/updates';
export const API_UPDATES_CREATE = '/api/v2/updates';
export const API_OFFLINE_UPDATES_CREATE = '/api/v1/admin/repo/offline-updates';
export const API_OFFLINE_UPDATES_GET = '/api/v1/admin/repo/offline-updates';
export const API_OFFLINE_UPDATES_SNAPSHOT_GET = '/api/v1/admin/repo/offline-snapshot.json';

export const API_HARDWARE_IDS_FETCH = '/api/v1/admin/devices/hardware_identifiers';
export const API_ECUS_FETCH = '/device-registry/api/v1/devices';
export const API_ECUS_PUBLIC_KEY_FETCH = '/api/v1/admin/devices';

/* Fleet/Group Operations */
export const API_GROUPS_FETCH = '/device-registry/api/v1/device_groups';
export const API_GROUPS_CREATE = '/device-registry/api/v1/device_groups';
export const API_GROUPS_RENAME = '/device-registry/api/v1/device_groups';
export const API_GROUPS_DELETE = '/device-registry/api/v1/device_groups';
export const API_GROUPS_DEVICES_FETCH = '/device-registry/api/v1/device_groups';
export const API_GROUPS_DETAIL = '/device-registry/api/v1/device_groups';
export const API_GROUPS_ADD_DEVICE = '/device-registry/api/v1/device_groups';
export const API_GROUPS_REMOVE_DEVICE = '/device-registry/api/v1/device_groups';
export const API_GROUPS_HIBERNATE = '/device-registry/api/v1/device_groups/:uuid/hibernation';

/* Package Operations */
export const API_PACKAGES = '/api/v1/user_repo/targets.json';
export const API_PACKAGES_REFRESH_REMOTE_DELEGATIONS = '/api/v1/user_repo/trusted-delegations/{delegation_name}/remote/refresh';
export const API_PACKAGES_STATIC_DELTAS = '/api/treehub/v3/deltas';
export const API_PACKAGES_GET_CONTENT = '/api/v1/user_repo/targets/';
export const API_PACKAGES_DELEGATIONS = '/api/v1/user_repo/delegations';
export const API_USER_REPO_TRUSTED_DELEGATIONS_KEYS = '/api/v1/user_repo/trusted-delegations/keys';
export const API_USER_REPO_TRUSTED_DELEGATIONS = '/api/v1/user_repo/trusted-delegations';
export const API_USER_REPO_ALL_TRUSTED_DELEGATION_INFO = '/api/v1/user_repo/trusted-delegations/info';
export const API_USER_REPO_TRUSTED_DELEGATION_INFO = '/api/v1/user_repo/trusted-delegations/{delegation_name}/info';
export const API_USER_REPO_DELETE_TRUSTED_DELEGATION = '/api/v1/user_repo/trusted-delegations/{delegation_name}';
export const API_USER_REPO_UPLOAD_DELEGATIONS_METADATA = '/api/v1/user_repo/delegations/{delegation_name}';
export const API_USER_REPO_ADD_DELEGATIONS_METADATA_URL = '/api/v1/user_repo/trusted-delegations/{delegation_name}/remote';

export const API_PACKAGES_METADATA_DELEGATIONS = '/api/metadata/delegations/tdx';
export const API_UPLOAD_PACKAGE = '/api/v1/user_repo/targets/{filename}';
export const API_DELETE_PACKAGE = '/api/v1/user_repo/targets';
export const API_PACKAGES_COUNT_VERSION_BY_NAME = '/api/v1/device_packages';
export const API_PACKAGES_COUNT_DEVICE_AND_GROUP = '/api/v1/device_count';
export const API_PACKAGES_BLACKLIST_FETCH = '/api/v1/blacklist';
export const API_PACKAGES_PACKAGE_BLACKLISTED_FETCH = '/api/v1/blacklist';
export const API_PACKAGES_BLACKLIST = '/api/v1/blacklist';
export const API_PACKAGES_UPDATE_BLACKLISTED = '/api/v1/blacklist';
export const API_PACKAGES_REMOVE_FROM_BLACKLIST = '/api/v1/blacklist';
export const API_PACKAGES_AFFECTED_DEVICES_COUNT_FETCH = '/api/v1/blacklist';
export const API_PACKAGES_DEVICE_PACKAGES = '/device-registry/api/v1/devices';
export const API_PACKAGES_DEVICE_AUTO_INSTALLED_PACKAGES = '/api/v1/auto_install';
export const API_PACKAGES_DEVICE_QUEUE = '/api/v1/device_updates';
export const API_PACKAGES_DEVICE_HISTORY = '/device-registry/api/v1/devices';
export const API_PACKAGES_DIRECTOR_DEVICE_HISTORY = '/api/v1/auditor/update_reports';
export const API_PACKAGES_DEVICE_UPDATES_LOGS = '/api/v1/device_updates';
export const API_PACKAGES_DEVICE_AUTO_INSTALL = '/api/v1/auto_install';
export const API_PACKAGES_DIRECTOR_DEVICE_AUTO_INSTALL = '/api/v1/admin/devices';
export const API_PACKAGES_DEVICE_INSTALL = '/api/v1/device_updates';
export const API_PACKAGES_DEVICE_CANCEL_INSTALLATION = '/api/v1/device_updates';
export const API_PACKAGES_COUNT_INSTALLED_ECUS = '/api/v1/admin/images/installed_count';
export const API_PACKAGES_CREATE_DESCRIPTION = '/api/v1/user_repo/comments';
export const API_PACKAGES_GET_DESCRIPTIONS = '/api/v1/user_repo/comments';

export const API_PACKAGES_DESCRIPTIONS = '/api/v1/user_repo/comments';

/* Campaign Operations - may not be available in CE */
export const API_CAMPAIGNS_FETCH = '/api/v2/campaigns';
export const API_CAMPAIGNS_FETCH_SINGLE = '/api/v2/campaigns';
export const API_CAMPAIGNS_CAMPAIGN_DETAILS = '/api/v1/campaigns';
export const API_CAMPAIGNS_STATISTICS_SINGLE = '/api/v2/campaigns';
export const API_CAMPAIGNS_LEGACY_CAMPAIGN_STATISTICS = '/api/v1/campaigns';
export const API_CAMPAIGNS_CREATE = '/api/v2/campaigns';
export const API_CAMPAIGNS_LEGACY_CREATE = '/api/v1/campaigns';
export const API_CAMPAIGNS_PACKAGE_SAVE = '/api/v1/campaigns';
export const API_CAMPAIGNS_GROUPS_SAVE = '/api/v1/campaigns';
export const API_CAMPAIGNS_LAUNCH = '/api/v2/campaigns';
export const API_CAMPAIGNS_LEGACY_LAUNCH = '/api/v1/campaigns';
export const API_CAMPAIGNS_RENAME = '/api/v2/campaigns';
export const API_CAMPAIGNS_LEGACY_RENAME = '/api/v1/campaigns';
export const API_CAMPAIGNS_CANCEL = '/api/v2/campaigns';
export const API_CAMPAIGNS_LEGACY_CANCEL = '/api/v1/campaigns';
export const API_CAMPAIGNS_CANCEL_REQUEST = '/api/v1/update_requests';

export const API_IMPACT_ANALYSIS_FETCH = '/api/v1/impact/blacklist';

export const API_PROVISIONING_STATUS = '/api/v1/provisioning/status';
export const API_PROVISIONING_ACTIVATE = '/api/v1/provisioning/activate';
export const API_PROVISIONING_DETAILS = '/api/v1/provisioning';
export const API_PROVISIONING_KEYS_FETCH = '/api/v1/provisioning/credentials/registration';
export const API_PROVISIONING_KEY_CREATE = '/api/v1/provisioning/credentials/registration';

// Account endpoints - not used in CE mode
export const API_ACCOUNT_REGISTER = '/api/accounts/users/register';
export const API_ACCOUNT_MIGRATE = '/api/accounts/users/migrate';
export const API_ACCOUNT_MIGRATED = '/api/accounts/users/migrated';
export const API_ACCOUNT_SEND_EMAIL_VERIFICATION_LINK = '/api/accounts/users/verify-email';
export const API_ACCOUNT_SEND_FORGOT_PASSWORD_LINK = '/api/accounts/users/reset-pw';
export const API_ACCOUNT_UPDATE_USER_ATTR = '/api/accounts/users/attributes';

export const API_ACCOUNT_ACTIVATE_COMMERCIAL_ACCESS_FREE_TRIAL = '/api/accounts/users/roles/subscription/free_trial';

export const API_ACCOUNT_CREATE_API_CLIENT = '/api/accounts/users/clients';
export const API_ACCOUNT_GET_API_CLIENTS = '/api/accounts/users/clients';
export const API_ACCOUNT_UPDATE_API_CLIENT = '/api/accounts/users/clients';
export const API_ACCOUNT_DELETE_API_CLIENT = '/api/accounts/users/clients';

/* Default values, limits, definitions */

// Global
export const VIEWPORT_MIN_WIDTH = 1280;
export const VIEWPORT_MIN_HEIGHT = 768;

// What's new
export const WHATS_NEW_INITIAL_STEP = 'introduction';
export const WHATS_NEW_DEFAULT_ACTIONS = ['Back', 'Close', 'Next'];

// Updates
export const LIMIT_UPDATES_WIZARD = 5;
export const LIMIT_UPDATES_MAIN = 30;

// Campaigns
export const LIMIT_CAMPAIGNS = 20;
export const CAMPAIGNS_STATUSES = ['prepared', 'launched', 'finished', 'cancelled'];

// Base URL - not used in CE mode (API calls are relative)
export const API_BASE_URL = '';

// User settings keys - stored in local storage for CE
export const FIRST_TIME_USER_ATTRIBUTE_KEY = 'ce_hide_intro';
export const USER_SETTINGS_ATTRIBUTE_KEY = 'ce_settings';

// Release notes - CE uses local or GitHub release notes
export const API_APP_RELEASE_NOTES_MD = '';

// Remote Access - not available in CE
export const API_REMOTE_ACCESS_CREATE_SESSION = '/api/ras/v1alpha/device/{uuid}/sessions';
export const API_REMOTE_ACCESS_DELETE_SESSION = '/api/ras/v1alpha/device/{uuid}/sessions';
export const API_REMOTE_ACCESS_GET_SESSIONS = '/api/ras/v1alpha/device/{uuid}/sessions';
export const API_REMOTE_ACCESS_GET_ALL_SESSIONS = '/api/ras/v1alpha/user/sessions';
export const API_REMOTE_ACCESS_GET_DEVICE_CURRENT_SESSION_DATA = '/api/ras/v1alpha/device/{uuid}';
export const API_REMOTE_ACCESS_GET_DEVICE_PUBLIC_KEYS = '/api/ras/v1alpha/device/{uuid}/public-keys';
export const API_REMOTE_ACCESS_ADD_DEVICE_PUBLIC_KEY = '/api/ras/v1alpha/device/{uuid}/public-keys';
export const API_REMOTE_ACCESS_GET_USER_PUBLIC_KEYS = '/api/ras/v1alpha/user/public-keys';
export const API_REMOTE_ACCESS_ADD_USER_PUBLIC_KEY = '/api/ras/v1alpha/user/public-keys';
export const API_REMOTE_ACCESS_DELETE_USER_PUBLIC_KEY = '/api/ras/v1alpha/user/public-keys';
export const API_REMOTE_ACCESS_GET_IP_ACCEPT_LIST = '/api/ras/v1alpha/user/ip-accept-list';
export const API_REMOTE_ACCESS_ADD_IP_ACCEPT_LIST = '/api/ras/v1alpha/user/ip-accept-list';
export const API_REMOTE_ACCESS_DELETE_IP_ACCEPT_LIST = '/api/ras/v1alpha/user/ip-accept-list/{ip}';

export const PRIVACY_POLICY_LINK = 'https://github.com/uptane/ota-community-edition';

export const AUTHORIZED_ENDPOINTS = [];

export const NO_LOADER_PAGES = [];
export const ALLOWED_USERS_FOR_ROUTES = {};
