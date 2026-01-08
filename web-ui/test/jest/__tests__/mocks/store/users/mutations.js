import Vue from 'vue';

export function setIsDashboardPage(state, val) {
  Vue.set(state, 'isDashboardPage', val);
}
