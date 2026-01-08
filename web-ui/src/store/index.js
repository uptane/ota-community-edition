import Vue from 'vue';
import Vuex from 'vuex';

import ui from './ui';
import users from './users';
import devices from './devices';
import packages from './packages';
import fleets from './fleets';
import hardware from './hardware';
import metrics from './metrics';
import updates from './updates';
import organizations from './organizations';
import remoteAccess from './remote-access';

Vue.use(Vuex);

/*
 * If not building with SSR mode, you can
 * directly export the Store instantiation
 */

const store = new Vuex.Store({
  modules: {
    ui,
    users,
    devices,
    fleets,
    packages,
    hardware,
    metrics,
    updates,
    organizations,
    remoteAccess,
  },
});

export default store;
