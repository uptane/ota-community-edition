<template>
  <q-page class v-touch-hold:5000.mouse="showAdvancedTabs">
    <q-tab-panels v-model="currentTab" class=" bg-transparent" animated>
      <q-tab-panel class="p-0 m-0" name="general-information">
        <general-debug-panel></general-debug-panel>
      </q-tab-panel>
      <q-tab-panel class="p-0 m-0" name="user-data-manager">
        <user-saved-data></user-saved-data>
      </q-tab-panel>
    </q-tab-panels>
  </q-page>
</template>

<script>
import { mapActions, mapMutations, mapGetters } from 'vuex';
import GeneralDebugPanel from 'src/components/debug/GeneralDebugPanel.vue';
import UserSavedData from 'src/components/debug/UserSavedData.vue';

export default {
  name: 'PageDebug',
  components: { GeneralDebugPanel, UserSavedData },
  data() {
    return {
      advancedTabsEnabled: false,
    };
  },
  computed: {
    ...mapGetters({
      tabs: 'ui/tabs',
      currentTab: 'ui/currentTab',
    }),
  },
  methods: {
    ...mapMutations({
      setTabs: 'ui/setTabs',
      setCurrentTab: 'ui/setCurrentTab',
    }),
    showAdvancedTabs() {
      this.$q
        .dialog({
          title: 'Enable Advanced Options?',
          message: 'Please note that these options are for advanced users only. If you are not sure what you are doing, please do not enable these options.',
          cancel: {
            label: 'No, I changed my mind',
            color: 'primary',
            flat: false,
            default: true,
          },
          ok: {
            label: 'Yes, I understand',
            color: 'default',
            flat: true,
          },
          persistent: true,
        })
        .onOk(() => {
          this.advancedTabsEnabled = true;
        });
    },
  },
  beforeDestroy() {
    this.setCurrentTab('general-information');
    this.setTabs(null);
  },
  mounted() {
    this.setCurrentTab('general-information');
    this.setTabs([
      {
        name: 'general-information',
        label: 'Information',
        icon: 'info',
      },

      {
        name: 'user-data-manager',
        label: 'My Saved Data',
        icon: 'api',
        hide: () => {
          return !this.advancedTabsEnabled;
        },
      },
    ]);
  },
};
</script>
