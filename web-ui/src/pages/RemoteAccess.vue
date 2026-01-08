<template>
  <q-page class="user-account">
    <remote-access-manager></remote-access-manager>
  </q-page>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import RemoteAccessManager from 'src/components/users/remote-access/RemoteAccessManager.vue';

export default {
  name: 'PageUserAccount',
  components: {
    RemoteAccessManager,
  },

  data() {
    return {
      isLoading: false,
    };
  },
  beforeDestroy() {},
  mounted() {
    this.pageTitle = 'Remote Access Manager';
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
  },
};
</script>
