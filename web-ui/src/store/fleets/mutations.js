import Vue from 'vue';

export function setFleets(state, val) {
  Vue.set(state, 'fleets', val);
}
export function setPreparedFleets(state, val) {
  Vue.set(state, 'preparedFleets', val);
}

export function setSelectedFleet(state, val) {
  Vue.set(state, 'selectedFleet', val);
}
