import { AuthService } from '../../services/auth.service';
import _ from 'lodash';
import { OptionsService } from '../../services/options.service';

export function userData(state) {
  return state.userData;
}
export function apiClients(state) {
  return state.apiClients;
}
export function accessToken(state) {
  return AuthService.accessToken;
}
export function guestAccessToken(state) {
  return AuthService.guestAccessToken;
}
export function resolvedAccessToken(state) {
  return AuthService.getResolvedAccessToken();
}
export function accountTypeData(state) {
  return state.accountTypeData;
}
export function hostRoles(state) {
  return state.hostRoles;
}
export function guestRoles(state) {
  return state.guestRoles;
}
export function accessScopes(state) {
  return state.accessScopes;
}
export function effectiveRoles(state, getters, rootState, rootGetters) {
  const roles = rootGetters['organizations/isGuestAccess'] ? state.guestRoles : state.hostRoles;
  if (isCommercialUser(state) && betaFeaturesEnabled(state)) {
    return [...roles, 'user-enabled-beta-access'];
  }
  return roles;
}

export function roles(state) {
  return state.roles;
}
export function isCommercialUser(state) {
  return _.includes(state.accountTypeData, 'torizon-commercial-tier');
}
export function hasBetaAccess(state) {
  return _.includes(state.accountTypeData, 'torizon-beta-access');
}
export function hasSuperUserAccess(state) {
  return _.includes(state.accountTypeData, 'torizon-super-user');
}
export function hasInternalUserAccess(state) {
  return _.includes(state.accountTypeData, 'tdx-internal-access');
}
export function betaFeaturesEnabled(state) {
  let flag = isCommercialUser(state) && OptionsService.getSavedOptionOrDefault('enableBetaFeatures', false);
  return flag;
}
