import { EventBus } from '../event-bus';
export default ({ Vue }) => {
  Vue.prototype.$events = EventBus;
};
