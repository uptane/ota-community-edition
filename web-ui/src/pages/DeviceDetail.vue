<template>
  <q-layout class="device-detail-page">
    <q-page-container class>
      <q-page>
        <q-tab-panels v-model="currentTab" class=" bg-transparent" animated>
          <q-tab-panel class="p-0 m-0" name="device-information">
            <device-detail></device-detail>
          </q-tab-panel>
          <q-tab-panel class="p-0 m-0" name="device-metrics">
            <device-metrics-panel :device="device"></device-metrics-panel>
          </q-tab-panel>
          <q-tab-panel class="p-0 m-0" name="device-update-history">
            <device-update-history :device="device"></device-update-history>
          </q-tab-panel>
          <q-tab-panel class="p-0 m-0" name="device-packages">
            <device-packages :device="device"></device-packages>
          </q-tab-panel>
        </q-tab-panels>
      </q-page>
    </q-page-container>
  </q-layout>
</template>

<script>
import { mapActions, mapGetters, mapMutations } from 'vuex';
import DeviceDetail from '../components/devices/DeviceDetail';
import DevicePackages from '../components/devices/DevicePackages';
import Loader from '../components/loaders/Loader';
import DeviceUpdateHistory from '../components/devices/DeviceUpdateHistory.vue';
import DeviceMetricsPanel from 'src/components/devices/DeviceMetricsPanel.vue';

export default {
  name: 'PageDeviceDetail',
  components: {
    Loader,
    DeviceDetail,
    DevicePackages,
    DeviceUpdateHistory,
    DeviceMetricsPanel,
  },
  data() {
    return {
      device: {},
      loading: true,
    };
  },
  created() {},
  beforeDestroy() {
    this.setCurrentTab('device-information');
    this.setTabs(null);
  },
  mounted() {
    this.pageTitle = 'Device Information';
    this.getDevice();
    this.setCurrentTab('device-information');
    this.setTabs([
      {
        name: 'device-information',
        label: 'Device Information',
        icon: 'info',
      },

      {
        name: 'device-metrics',
        label: 'Device Metrics',
        icon: 'analytics',
        hide: () => {
          return false;
        },
      },
      {
        name: 'device-update-history',
        label: 'Update History',
        icon: 'fa fa-history',
        hide: () => {
          return false;
        },
      },
      {
        name: 'device-packages',
        label: 'Compatible Packages',
        icon: 'fa fa-box',
      },
    ]);
  },
  computed: {
    ...mapGetters({
      tabs: 'ui/tabs',
      currentTab: 'ui/currentTab',
    }),
    adminMode: {
      get() {
        return this.$store.getters['ui/adminMode'];
      },
      set(v) {
        this.$store.commit('ui/setAdminMode', v);
      },
    },
    pageTitle: {
      get() {
        return this.$store.getters['ui/currentPageTitle'];
      },
      set(val) {
        return this.$store.commit('ui/setCurrentPageTitle', val);
      },
    },
    deviceUuid() {
      return this.$route.params.deviceId;
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
    getDevice() {
      // this.device = this.device.uuid ? this.device : this.deviceData;
      this.loading = false;
      if (!this.deviceUuid) {
        return;
      }
      if (!(this.device || {}).uuid) {
        this.loading = true;
      } else {
        this.loadingUpdatedData = true;
      }
      this.fetchDevice(this.deviceUuid)
        .then((device) => {
          this.device = device;
          this.pageTitle = `${this.device.deviceName}`;
          this.loading = false;
          this.loadingUpdatedData = false;
          if (this.device && this.device.updates && this.device.updates.length) {
            this.device.updates.sort((a, b) => {
              const aDate = new Date(a.custom.createdAt).getTime();
              const bDate = new Date(b.custom.createdAt).getTime();
              let s = 0;
              if (aDate > bDate) s = -1;
              if (aDate < bDate) s = 1;
              return s;
            });
          }
        })
        .catch((err) => {
          this.loadingUpdatedData = false;
          this.loading = false;
        });
    },
  },
};
</script>
