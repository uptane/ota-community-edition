import { uid } from 'quasar';
import store from '../store';

window.dataLayer = window.dataLayer || [];

export default {
  logEvent(category, action, label, value) {
    window.dataLayer.push({
      category,
      action,
      label,
      value,
      tier: this.getAccountTier(),
      event: 'customEvent',
    });
  },

  logPage(path, name) {
    window.dataLayer.push({
      name,
      path,
      tier: this.getAccountTier(),
      event: 'customPageView',
    });
  },

  getAccountTier() {
    if (store.getters['users/isCommercialUser']) {
      return 'Commercial';
    }
    if (store.getters['users/betaFeaturesEnabled']) {
      return 'Beta Features Enabled';
    }
    if (store.getters['users/hasBetaAccess']) {
      return 'Beta Access';
    }

    return 'Standard';
  },
};
