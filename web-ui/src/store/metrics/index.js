import state from './state';
import * as getters from './getters';
import * as mutations from './mutations';
import * as actions from './actions';

export default {
  namespaced: true,
  actions,
  state,
  getters,
  mutations,
};
