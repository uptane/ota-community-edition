import Vue from 'vue';

export function setUserData(state, userData) {
  Vue.set(state, 'userData', { ...state.userData, ...userData });
}
export function setUserDataNamespace(state, ns) {
  Vue.set(state.userData, 'namespace', ns);
}
export function setAccountTypeData(state, data) {
  Vue.set(state, 'accountTypeData', data);
}
export function setApiClients(state, data) {
  Vue.set(state, 'apiClients', data);
}
export function setHostRoles(state, data) {
  Vue.set(state, 'hostRoles', data);
}
export function setGuestRoles(state, data) {
  Vue.set(state, 'guestRoles', data);
}
export function setRoles(state, data) {
  Vue.set(state, 'roles', data);
}
export function setAccessScopes(state, data) {
  Vue.set(state, 'accessScopes', data);
}
