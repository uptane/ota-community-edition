import Vue from 'vue';

export function setPreparedPackages(state, val) {
  Vue.set(state, 'preparedPackages', val);
}
export function setPackagesUploading(state, val) {
  Vue.set(state, 'packagesUploading', val);
}
export function setTableData(state, val) {
  Vue.set(state, 'tableData', val);
}
export function setPackages(state, val) {
  Vue.set(state, 'packages', val);
}
export function setPreparedOndevicePackages(state, val) {
  Vue.set(state, 'preparedOndevicePackages', val);
}
