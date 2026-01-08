import VueTimeago from 'vue-timeago';

import toNow from 'date-fns/distance_in_words_to_now';
export const timeago = (dateTime, options) => {
  if (!dateTime) {
    return '';
  }
  const { locale, addSuffix = true, includeSeconds = true } = options || {};
  return toNow(dateTime, {
    locale,
    includeSeconds,
    addSuffix,
  });
};

export default ({ Vue }) => {
  Vue.use(VueTimeago, {
    name: 'Timeago', // Component name, `Timeago` by default
    locale: 'en', // Default locale
    // We use `date-fns` under the hood
    // So you can use all locales from it
    // locales: {
    //   'zh-CN': require('date-fns/locale/zh_cn'),
    //   'ja': require('date-fns/locale/ja'),
    // }
  });
  Vue.prototype.$timeago = timeago;
};
