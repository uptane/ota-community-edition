import state from './state';
import * as actions from './actions';
import * as getters from './getters';
import * as mutations from './mutations';

export default {
  namespaced: true,
  state,
  actions,
  getters,
  mutations,
};
