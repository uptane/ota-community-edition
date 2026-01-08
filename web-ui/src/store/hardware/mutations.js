import Vue from 'vue';

export function setHardwareData(state, val) {
  Vue.set(state, 'hardwareData', val);
}
export function setHardwareIds(state, val) {
  Vue.set(state, 'hardwareIds', val);
}
