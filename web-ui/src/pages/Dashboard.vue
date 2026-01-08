<template>
  <q-page class="mxw dashboard" id="dashboard">
    <!-- Welcome banner for first-time users -->
    <div v-if="firstTimer">
      <div
        class="w-100 min-50em"
        :class="{
          'h-50vh': $q.screen.gt.sm,
        }"
        :style="{
          height: $q.screen.gt.sm ? '50vh' : $q.screen.gt.xs ? '26vh' : '22vh',
        }"
      >
        <div class="w-100 h-100 flex flex-center bg-primary">
          <div class="text-center text-white">
            <q-icon name="cloud_upload" size="5rem" class="q-mb-md" />
            <h2 class="q-ma-none">OTA Community Edition</h2>
            <p class="text-h6 opacity-80">Open-source OTA updates for embedded devices</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Quick links section -->
    <div class="p-1 pr-2 row" style="margin-top: -4.5rem">
      <div class="col-12">
        <div class="row q-gutter-md">
          <div class="col text-right">
            <q-btn
              type="a"
              flat
              padding="md"
              class="q-card text-secondary"
              href="https://github.com/uptane/ota-community-edition"
              target="_blank"
            >
              <q-icon name="fab fa-github" class="q-mr-sm" />
              Documentation
            </q-btn>
          </div>
          <div class="col">
            <q-btn
              type="a"
              flat
              padding="md"
              class="q-card text-secondary"
              href="https://uptane.github.io/"
              target="_blank"
            >
              <q-icon name="security" class="q-mr-sm" />
              Uptane Standard
            </q-btn>
          </div>
        </div>
      </div>

      <!-- Dashboard widgets -->
      <div class="p-1 col-4 col-xs-12 col-sm-12 col-md-4 col-lg-4">
        <recent-devices :limit="firstTimer ? 5 : 7"></recent-devices>
      </div>
      <div class="p-1 col-4 col-xs-12 col-sm-12 col-md-4 col-lg-4">
        <recent-packages title="Recent Packages" viewType="list" :limit="firstTimer ? 5 : 7"></recent-packages>
      </div>
      <div class="p-1 col-4 col-xs-12 col-sm-12 col-md-4 col-lg-4">
        <recent-fleets :limit="firstTimer ? 5 : 7" title="Recent Fleets"></recent-fleets>
      </div>
    </div>
  </q-page>
</template>

<script>
import Packages from '../components/packages/Packages';
import { openURL } from 'quasar';
import { mapGetters, mapActions } from 'vuex';
import RecentDevices from '../components/devices/RecentDevices.vue';
import RecentFleets from '../components/fleets/RecentFleets.vue';
import RecentPackages from '../components/packages/RecentPackages.vue';

export default {
  name: 'PageDashboard',
  components: {
    Packages,
    RecentDevices,
    RecentFleets,
    RecentPackages,
  },
  data() {
    return {
      openURL,
    };
  },
  created() {},
  mounted() {
    this.isDashboardPage = true;
    this.pageTitle = 'Dashboard';
  },
  beforeDestroy() {
    this.isDashboardPage = false;
  },
  computed: {
    ...mapGetters({
      userSettings: 'ui/userSettings',
    }),
    user_settings() {
      return this.userSettings || {};
    },
    pageTitle: {
      get() {
        return this.$store.getters['ui/currentPageTitle'];
      },
      set(val) {
        return this.$store.commit('ui/setCurrentPageTitle', val);
      },
    },
    firstTimer: {
      get() {
        return this.user_settings['notFirstLogin'] === undefined || !this.user_settings['notFirstLogin'];
      },
      set(v) {
        return this.saveUserSettings({
          notFirstLogin: !v,
        });
      },
    },
    isDashboardPage: {
      get() {
        return this.$store.getters['ui/isDashboardPage'];
      },
      set(val) {
        return this.$store.commit('ui/setIsDashboardPage', val);
      },
    },
  },
  methods: {
    ...mapActions({
      saveUserSettings: 'ui/saveUserSettings',
    }),
    getStarted() {
      this.$router.push({ name: 'devices' });
      setTimeout(() => {
        this.provisionDevice();
      }, 1000);
    },
    provisionDevice() {
      this.$events.$emit('dialogs:create-device:open', {
        show: true,
      });
    },
  },
};
</script>
