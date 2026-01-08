import Vue from 'vue';

export function setUpdates(state, updates) {
  Vue.set(state, 'updates', updates);
}
export function setVisibleColumns(state, values) {
  Vue.set(state, 'visibleColumns', values);
}
