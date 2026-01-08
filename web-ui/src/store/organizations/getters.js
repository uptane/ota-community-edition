/**
 * OTA Community Edition Organizations Getters
 * 
 * CE has a single namespace, so these getters return simplified data.
 * The interface is maintained for compatibility with existing components.
 */

// Default CE repository
const CE_REPOSITORY = {
  id: 'ce-default-repository',
  name: 'OTA CE Repository',
  description: 'OTA Community Edition default repository',
  is_host_repo: true,
};

export function hostRepository(state) {
  return state.hostRepository || CE_REPOSITORY;
}

export function myRepository(state, getters, rootState, rootGetters) {
  return {
    ...CE_REPOSITORY,
    avatar: (rootState.users.userData || {}).avatar,
  };
}

export function hostRepositoryIsDefined(state) {
  return true; // Always true in CE mode
}

export function availableRepositories(state, getters, rootState) {
  // Only the host repository is available in CE
  return [myRepository(state, getters, rootState)];
}

export function availableRepositoriesByKey(state, getters, rootState) {
  const repos = availableRepositories(state, getters, rootState);
  return _.keyBy(repos, 'id');
}

export function hostRepositoryUsers(state) {
  return []; // No multi-user in CE
}

export function guestRepositories(state) {
  return []; // No guest repos in CE
}

export function guestProfiles(state) {
  return []; // No guest profiles in CE
}

export function isGuestAccess(state) {
  return false; // Never guest access in CE
}

export function activeRepository(state) {
  return state.hostRepository || CE_REPOSITORY;
}

export function defaultRepositoryData(state, getters, rootState, rootGetters) {
  return {
    name: 'OTA CE Repository',
    description: 'OTA Community Edition default repository',
  };
}
