<template>
  <q-page-sticky expand position="top" v-if="!alertData.useQAlert && show">
    <i @click="markAsRead" class="q-icon notranslate material-icons cursor-pointer float-right q-ma-xs text-white" aria-hidden="true" role="presentation">close</i>
    <div v-html="alertData.message" :class="['q-pa-xs text-center', 'text-' + (alertData.textColor || 'white'), 'bg-' + (alertData.bgColor || 'toradex-light-blue')]"></div>
  </q-page-sticky>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
export default {
  name: 'AlertBanner',
  props: {
    alertData: {
      type: Object,
      default: () => {
        return {
          message: 'Alert Message',
          optionKey: 'message_key',
          textColor: 'white',
          bgColor: 'toradex-light-blue',
          useQAlert: false,
        };
      },
    },
  },
  data() {
    return {
      requested: false,
      onCloseAction: () => {},
    };
  },
  computed: {
    ...mapGetters({
      userSettings: 'ui/userSettings',
    }),
    optionKey() {
      return this.alertData.optionKey;
    },
    show: {
      get() {
        return this.requested && this.getSavedSettingOrDefault(this.optionKey, true);
      },
      set(v) {
        this.updateUserOption(this.optionKey, v);
      },
    },
  },
  methods: {
    ...mapActions({
      saveUserSettings: 'ui/saveUserSettings',
    }),
    markAsRead() {
      this.show = false;
      this.onCloseAction();
    },
    updateUserOption(key, value) {
      this.$set(this.userSettings, key, value);
      this.saveUserSettings({ [key]: value });
    },
    getSavedSettingOrDefault(key, defaultValue) {
      return typeof this.userSettings[key] !== 'undefined' ? this.userSettings[key] : defaultValue;
    },
  },
  mounted() {
    this.$events.$on(`alert:banner:request`, ({ key, onClose }) => {
      if (key !== null && key == this.optionKey) {
        this.onCloseAction = onClose || (() => {});
        this.requested = true;
        if (this.alertData.useQAlert && this.show)
          this.$q.notify({
            position: 'top',
            color: this.alertData.bgColor,
            message: this.alertData.message,
            html: true,
            timeout: 0,
            onDismiss: () => {
              this.show = !this.show;
            },
          });
      }
    });
  },
};
</script>
