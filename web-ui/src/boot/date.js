import { date } from 'quasar';

export default ({ Vue }) => {
  Vue.prototype.$date = date;
};
