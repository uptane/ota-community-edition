import Vue from 'vue';

export function setCustomCharts(state, charts) {
  Vue.set(state, 'customCharts', charts);
}
