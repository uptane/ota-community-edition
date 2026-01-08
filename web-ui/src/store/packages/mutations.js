import Vue from 'vue';

export function setPackagesUploading(state, val) {
  Vue.set(state, 'packagesUploading', val);
}
export function setPackages(state, val) {
  Vue.set(state, 'packages', val);
}
export function setDelegations(state, delegations) {
  Vue.set(state, 'delegations', delegations);
}
export function setSelectedDelegationSources(state, sources) {
  Vue.set(state, 'selectedDelegationSources', sources);
}
export function setTrustedDelegations(state, delegations) {
  Vue.set(state, 'trustedDelegations', delegations);
}

export function setDeltas(state, val) {
  Vue.set(state, 'deltas', { ...state.deltas, ...val });
}
