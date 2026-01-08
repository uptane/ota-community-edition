import jQuery from 'jquery';

export default ({ Vue }) => {
  // Vue.prototype.$jquery = jQuery
  // window.jQuery = jQuery;
  window.jQuery = jQuery;
  Vue.prototype.$jq = jQuery;
};
