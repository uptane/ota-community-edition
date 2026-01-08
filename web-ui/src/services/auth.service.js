/**
 * OTA Community Edition Auth Service
 * 
 * This is a no-op auth service for OTA Community Edition.
 * CE does not require authentication on the admin UI - authentication
 * is expected to be provided externally (e.g., reverse proxy, VPN).
 * 
 * This service mocks the AuthService interface to maintain compatibility
 * with existing components while always returning "authenticated" state.
 */

import { SessionStorage } from 'quasar';
import store from '../store';

// Default CE user - always authenticated, full access
const CE_USER = {
  email: 'admin@ota-ce.local',
  name: 'OTA CE Admin',
  preferred_username: 'admin',
  sub: 'ce-default-user',
  // Grant all roles for full access in CE mode
  realm_access: {
    roles: ['torizon-commercial-tier', 'torizon-admin'],
  },
  resource_access: {
    'ota-user-manager': {
      roles: ['torizon-commercial-tier', 'torizon-admin'],
    },
  },
};

// CE default roles - full access
const CE_ROLES = ['torizon-commercial-tier', 'torizon-admin'];

class Auth {
  constructor() {
    this._initialized = false;
  }

  /** CE mode is always considered "guest access" disabled */
  get isOrganizationGuestAccess() {
    return false;
  }

  /** No tokens needed in CE mode */
  get accessToken() {
    return null;
  }

  get hostAccessToken() {
    return null;
  }

  get guestAccessToken() {
    return null;
  }

  get resolvedAccessToken() {
    return null;
  }

  get authorizationCodeUrl() {
    return null;
  }

  get profileUrl() {
    return null;
  }

  get registrationUrl() {
    return null;
  }

  /** Initialize - immediate success in CE mode */
  async init() {
    if (this._initialized) return;
    this._initialized = true;
    console.log('[OTA CE] Auth service initialized (no-auth mode)');
    return Promise.resolve();
  }

  /** Get current user data - returns CE default user */
  getUserData() {
    return new Promise((resolve) => {
      store.commit('ui/setUser', CE_USER);
      store.commit('users/setAccountTypeData', CE_ROLES);
      resolve(CE_USER);
    });
  }

  /** Get user roles - returns CE default roles */
  getUserRoles() {
    return new Promise((resolve) => {
      store.commit('users/setHostRoles', CE_ROLES, { root: true });
      store.commit('users/setGuestRoles', [], { root: true });
      store.commit('users/setRoles', CE_ROLES, { root: true });
      store.commit('users/setAccessScopes', [], { root: true });
      resolve({
        all: CE_ROLES,
        host: CE_ROLES,
        guest: [],
        scopes: [],
      });
    });
  }

  /** Get user info - returns CE default user */
  getUserInfo() {
    return Promise.resolve(CE_USER);
  }

  getUser() {
    return this.getUserData();
  }

  getSession() {
    return this.getUserData();
  }

  getAccessToken() {
    return null;
  }

  getHostAccessToken() {
    return null;
  }

  getGuestAccessToken() {
    return null;
  }

  getResolvedAccessToken() {
    return null;
  }

  /** Session refresh - no-op in CE mode */
  refreshSession(force = false) {
    return Promise.resolve(true);
  }

  refreshGuestSession() {
    return Promise.resolve(true);
  }

  refreshResolvedSession() {
    return Promise.resolve(true);
  }

  /** User data operations - no-op in CE mode */
  clearFirstTimer() {
    return Promise.resolve({});
  }

  updateUserData(update) {
    return Promise.resolve({});
  }

  isValidAuthState(state) {
    return true;
  }

  /** Sign out - just reload the page in CE mode */
  signOut() {
    window.location.reload();
  }

  /** Login redirects - no-op in CE mode, go to dashboard */
  gotoLogin(redirectUri) {
    window.location.href = '/#/';
  }

  gotoRegister(redirectUri) {
    window.location.href = '/#/';
  }

  /** Auth operations - not supported in CE mode */
  signIn(model) {
    return Promise.resolve(CE_USER);
  }

  signUp(model) {
    return Promise.reject(new Error('Registration not available in OTA Community Edition'));
  }

  changePassword(model) {
    return Promise.reject(new Error('Password change not available in OTA Community Edition'));
  }

  forgotPassword(model) {
    return Promise.reject(new Error('Password reset not available in OTA Community Edition'));
  }

  resetPassword(model) {
    return Promise.reject(new Error('Password reset not available in OTA Community Edition'));
  }

  resendConfirmation(email) {
    return Promise.reject(new Error('Email confirmation not available in OTA Community Edition'));
  }

  confirmRegistration(model) {
    return Promise.reject(new Error('Registration not available in OTA Community Edition'));
  }

  verifyAttribute(model) {
    return Promise.reject(new Error('Attribute verification not available in OTA Community Edition'));
  }

  resendAttributeVerificationCode(model) {
    return Promise.reject(new Error('Verification not available in OTA Community Edition'));
  }

  /** Always logged in for CE mode */
  async isLoggedIn() {
    await this.getUserRoles();
    return true;
  }

  /** CE is never in demo mode */
  get isDemoMode() {
    return false;
  }

  /** CE mode flag */
  get isCEMode() {
    return true;
  }

  /** Profile operations - not supported in CE mode */
  updateUserImage(imageUrl) {
    return Promise.reject(new Error('Profile updates not available in OTA Community Edition'));
  }

  updateProfile(update) {
    return Promise.reject(new Error('Profile updates not available in OTA Community Edition'));
  }

  resetCredentials() {
    return Promise.reject(new Error('Credential reset not available in OTA Community Edition'));
  }

  getCredentials() {
    return Promise.reject(new Error('Credentials not available in OTA Community Edition'));
  }

  /** Organization/repo switching - not supported in CE mode (single namespace) */
  switchAccessType(org) {
    return Promise.resolve();
  }

  getCurrentOrganization() {
    return null;
  }

  /** Redirect URL helpers */
  setRedirectUrl(url) {
    SessionStorage.set('redirectUrl', url);
  }

  getRedirectUrl() {
    return SessionStorage.getItem('redirectUrl');
  }

  removeRedirectUrl() {
    SessionStorage.remove('redirectUrl');
  }

  /** Token cleanup - no-op in CE mode */
  clearTokens() {
    return;
  }

  /** Guest mode flag - always false in CE */
  get isGuestMode() {
    return false;
  }
}

export const AuthService = new Auth();
