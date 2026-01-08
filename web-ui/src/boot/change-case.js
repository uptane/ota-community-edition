import changeCase from 'change-case';

export default ({ Vue }) => {
  Vue.prototype.$changeCase = changeCase;
};
