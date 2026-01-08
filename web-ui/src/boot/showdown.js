import showdown from 'showdown';
export default ({ Vue }) => {
  showdown.setFlavor('github');
  showdown.setOption('emoji', true);
  Vue.prototype.$showdown = showdown;
};
