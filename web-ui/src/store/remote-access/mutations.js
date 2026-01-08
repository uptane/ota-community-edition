import Vue from 'vue';

export function setPublicKeys(state, keys) {
  Vue.set(state, 'publicKeys', keys);
}
