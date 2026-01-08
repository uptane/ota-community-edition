export function preparedPackages(state) {
  return state.preparedPackages;
}
export function packagesUploading(state) {
  return state.packagesUploading;
}
export function tableData(state) {
  return state.tableData;
}
export function packages(state) {
  return state.packages;
}
export function preparedOndevicePackages(state) {
  return state.preparedOndevicePackages;
}
export function packagesById(state) {
  return {};
}
export function packagesByHash(state) {
  return {};
}
export function torizonPackagesByHash(state) {
  return {};
}
export function userPackagesByHash(state) {
  return {};
}

export function packageSourceOptions(state) {
  return [];
}
export function selectedDelegationSources(state, getters, rootState) {
  return [];
}
export function selectedDelegations(state, getters, rootState) {
  return [];
}

export function packageGroupsInSelectedSources(state, getters, rootState) {
  return [];
}
export function packageGroupsInAllSources(state, getters, rootState) {
  return [];
}

export function packagesInAllSources(state, getters, rootState) {
  return {};
}
export function packagesInSelectedSources(state, getters, rootState) {
  return {};
}
