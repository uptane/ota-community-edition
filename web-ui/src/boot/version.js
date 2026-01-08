import { version } from '../../package.json';

export default ({ Vue }) => {
  Vue.prototype.$version = version;
};
