/**
 * OTA Community Edition Organizations Mutations
 * 
 * Simplified mutations for CE mode. Most are no-ops since
 * CE has a single fixed repository.
 */

import Vue from 'vue';

export function setHostRepository(state, repo) {
  // In CE mode, we keep the default repository but allow minor updates
  Vue.set(state, 'hostRepository', { ...state.hostRepository, ...repo });
}

export function setHostRepositoryUsers(state, users) {
  // No-op in CE mode
  Vue.set(state, 'hostRepositoryUsers', []);
}

export function setGuestRepositories(state, repos) {
  // No-op in CE mode - no guest repos
  Vue.set(state, 'guestRepositories', []);
}

export function setGuestProfiles(state, profiles) {
  // No-op in CE mode - no guest profiles
  Vue.set(state, 'guestProfiles', []);
}
