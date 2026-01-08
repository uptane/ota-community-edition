import Vue from 'vue';
import Vuex from 'vuex';

import ui from './ui';
import users from './users';
import devices from './devices';
// import fleets from './fleets';
// import hardware from './hardware';
import packages from './packages';

// Vue.use(Vuex);

/*
 * If not building with SSR mode, you can
 * directly export the Store instantiation
 */

const storeMods = {
  ui,
  users,
  devices,
  // fleets,
  packages,
  // hardware,
};

export const storeModules = storeMods;
