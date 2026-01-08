/**
 * OTA Community Edition Organizations Actions
 * 
 * CE has a single namespace, so these actions are no-ops or return defaults.
 * The interface is maintained for compatibility with existing components.
 */

// No-op actions for CE - multi-repo functionality is not available

export function createRepository({ commit, state, dispatch, rootGetters }, organizationData) {
  return Promise.reject(new Error('Repository creation not available in OTA Community Edition'));
}

export function updateRepository({ commit, state, dispatch, rootGetters }, organizationData) {
  return Promise.reject(new Error('Repository update not available in OTA Community Edition'));
}

export function addUsersToRepository({ commit, state, dispatch, rootGetters }, { users }) {
  return Promise.reject(new Error('User management not available in OTA Community Edition'));
}

export function updateUserInRepository({ commit, state, dispatch, rootGetters }, { user }) {
  return Promise.reject(new Error('User management not available in OTA Community Edition'));
}

export function removeUserFromRepository({ commit, state, dispatch, rootGetters }, { user }) {
  return Promise.reject(new Error('User management not available in OTA Community Edition'));
}

export function getHostRepository({ commit, state, dispatch, rootGetters }, payload) {
  // Return the default CE repository
  return Promise.resolve([state.hostRepository]);
}

export function getHostRepositoryUsers({ commit, state, dispatch, rootGetters }, payload) {
  // No users in CE mode
  return Promise.resolve([]);
}

export function getGuestRepositories({ commit, state, dispatch, rootGetters }, payload) {
  // No guest repos in CE mode
  return Promise.resolve([]);
}

export function getGuestProfiles({ commit, state, dispatch, rootGetters }) {
  // No guest profiles in CE mode
  return Promise.resolve([]);
}
