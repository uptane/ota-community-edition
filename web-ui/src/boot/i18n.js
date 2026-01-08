import VueI18n from 'vue-i18n';
import messages from 'src/i18n';
import Quasar from 'quasar';

export default ({ app, Vue }) => {
  const { lang } = Quasar;
  Vue.use(VueI18n);
  // Set i18n instance on app
  app.i18n = new VueI18n({
    locale: lang.getLocale(),
    fallbackLocale: 'en-us',
    messages,
  });
};
