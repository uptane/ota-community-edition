<template>
  <q-page class="user-account">
    <q-tab-panels v-model="currentTab" class=" bg-transparent" animated>
      <q-tab-panel class="p-0 m-0" name="account-information">
        <user-account-panel></user-account-panel>
      </q-tab-panel>
      <q-tab-panel class="p-0 m-0" name="api-client-manager">
        <api-token-manager-panel></api-token-manager-panel>
      </q-tab-panel>
      <q-tab-panel class="p-0 m-0" name="repository-sharing">
        <organization-manager-panel> </organization-manager-panel>
      </q-tab-panel>
    </q-tab-panels>

    <konami-code></konami-code>
  </q-page>
</template>

<script>
import { mapActions, mapMutations, mapGetters } from 'vuex';
import KonamiCode from 'src/components/users/KonamiCode';
import UserAccountPanel from 'src/components/users/UserAccountPanel';
import ApiTokenManagerPanel from 'src/components/users/ApiTokenManagerPanel.vue';
import OrganizationManagerPanel from 'src/components/users/organizations/OrganizationManagerPanel.vue';
import { canAccessFeature } from 'src/config/feature-toggle.js';

export default {
  name: 'PageUserAccount',
  components: {
    UserAccountPanel,
    KonamiCode,
    ApiTokenManagerPanel,
    OrganizationManagerPanel,
  },

  data() {
    return {
      isLoading: false,
    };
  },
  beforeDestroy() {
    this.setCurrentTab('account-information');
    this.setTabs(null);
  },
  mounted() {
    this.pageTitle = 'Account Information';
    this.setCurrentTab('account-information');
    if (this.$route.query.tab) {
      this.setCurrentTab(this.$route.query.tab);
    }
    this.setTabs([
      {
        name: 'account-information',
        label: 'Account Information',
        icon: 'info',
      },

      {
        name: 'api-client-manager',
        label: 'API Client Manager',
        icon: 'api',
        hide: () => {
          return !canAccessFeature('view-api-client-manager');
        },
      },
      {
        name: 'repository-sharing',
        label: 'Repository Sharing',
        icon: 'share',
      },
    ]);
  },
  computed: {
    ...mapGetters({
      tabs: 'ui/tabs',
      currentTab: 'ui/currentTab',
      isSuperUser: 'users/hasSuperUserAccess',
      isInternalUser: 'users/hasInternalUserAccess',
      betaFeaturesEnabled: 'users/betaFeaturesEnabled',
    }),
    pageTitle: {
      get() {
        return this.$store.getters['ui/currentPageTitle'];
      },
      set(val) {
        return this.$store.commit('ui/setCurrentPageTitle', val);
      },
    },
  },
  methods: {
    ...mapActions({
      fetchDevice: 'devices/fetchDevice',
    }),
    ...mapMutations({
      setTabs: 'ui/setTabs',
      setCurrentTab: 'ui/setCurrentTab',
    }),
  },
};
</script>
