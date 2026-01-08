<template>
  <div class>
    <div v-if="loading">
      <div v-if="!quickView" class="p-1 flex flex-center mnh-100vh">
        <div class="text-center">
          <div>
            <span class="opacity-90 pr-1">Loading device information...</span>
            <loader class flat color="secondary" />
          </div>
        </div>
      </div>
      <div v-if="quickView" class="p-1 flex flex-center mnh-10em">
        <div class="text-center">
          <div>
            <span class="opacity-90 pr-1">Loading device information...</span>
            <loader class flat color="secondary" />
          </div>
        </div>
      </div>
    </div>
    <div v-if="!loading">
      <div v-if="!quickView" class="row">
        <div class="col-12" v-if="!(selectedPackage && $q.screen.lt.md)">
          <div
            class="q-card row"
            :class="{
              'p-2 ': $q.screen.lt.sm,
              'p-1 ': $q.screen.gt.xs,
            }"
            style="overflow: hidden"
          >
            <div class="col-12">
              <div class="row">
                <h5 class="m-0 col pl-0">
                  <div class="row">
                    <div class="col-auto ellipsis">{{ device.deviceName }} <span class="text-faded"></span></div>
                    <div class="col">
                      <q-btn flat @click="showEditDialog" color="primary" icon="edit">
                        <tooltip>Rename this device</tooltip>
                      </q-btn>
                    </div>
                  </div>
                </h5>
              </div>
            </div>

            <div class="col-12 col-xl-8 mb-2">
              <device-information show-all :device.sync="device"></device-information>
            </div>
            <div v-if="$q.screen.lt.xl" class="col-12 actions-view-spacer"></div>
            <div
              class="col-12 col-xl-4 q-card"
              :class="{
                ' actions-view': $q.screen.lt.xl,
              }"
            >
              <div class="row">
                <div
                  class="col-12 col-auto"
                  :style="{
                    'padding-left': actionLeftPadding,
                  }"
                >
                  <device-actions :horizontal="$q.screen.lt.xl" :device="device" @deleted="deviceDeleted" :actions="['update', 'fleet', 'rename', 'remoteAccess', 'hibernate', 'delete']"></device-actions>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { mapActions, mapGetters, mapMutations } from 'vuex';
import DeviceStatus from './DeviceStatus';
import Loader from 'src/components/loaders/Loader';
import DeviceOnlineBadge from './DeviceOnlineBadge';
import { dom, extend, date } from 'quasar';
import ReloadBtn from 'src/components/common/ReloadBtn';
import DevicePackageInformation from './DevicePackageInformation.vue';
import Tooltip from 'src/components/common/Tooltip.vue';
import DeviceInformation from './DeviceInformation.vue';
import DeviceActions from './DeviceActions.vue';

import DeviceChart from './DeviceChart.vue';
import DateTimeRangePicker from 'src/components/common/DateTimeRangePicker.vue';
import ChartControls from 'src/components/common/ChartControls.vue';
import { OptionsService } from 'src/services/options.service';

const { height, width } = dom;
const { formatDate } = date;
export default {
  name: 'DeviceDetail',
  components: {
    Loader,
    DeviceOnlineBadge,
    ReloadBtn,
    DeviceStatus,
    DevicePackageInformation,
    Tooltip,
    DeviceInformation,
    DeviceActions,
    DeviceChart,
    DateTimeRangePicker,
    ChartControls,
  },
  props: {
    deviceData: {
      type: Object,
      default: () => {
        return {};
      },
    },
    quickView: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      loading: false,
      loadingUpdatedData: false,
      loadingPackages: false,
      loadingDeviceData: false,
      largeHistoryView: true,
      updateInitiated: false,
      showPackageVersions: false,
      parsedPackageVersions: {},
      giveAttensionToVersionsView: false,
      selectedPackage: null,
      _selectedChartTheme: 'default',
      _animateChart: true,
      _autoUpdateChart: true,
      _autoUpdateFreq: 60 * 1000,
      _highlightNoDataRegion: true,
      _dateRangePreset: 60 * 60 * 1000,
      dateRangePresetOptions: [{ label: 'Last hour', value: 60 * 60 * 1000 }, { label: 'Last 4 hours', value: 4 * 60 * 60 * 1000 }, { label: 'One day', value: 24 * 60 * 60 * 1000 }, { label: 'One week', value: 7 * 24 * 60 * 60 * 1000 }],
      _dateFrom: new Date(Date.now() - 60 * 60 * 1000).toString(),
      _dateTo: new Date().toString(),
      _dateData: null,
      actionLeftPadding: '0px',
      device: {},
    };
  },
  created() {},
  mounted() {
    this.device = (this.$store.getters['devices/devices'] || []).find((f) => (f.uuid = this.deviceUuid));
    this.setup();
    this.calulateActionsPadding();
    // Get flag for notice that metric has moved to its own tab
    const metricMoveNotified = OptionsService.getSavedOptionOrDefault('metricMoveNotified', false);
    // Show notice if not already shown
    if (!metricMoveNotified) {
      this.$q
        .dialog({
          title: 'Notice',
          message: 'Device metrics and charts have moved to their own tab',
          ok: {
            label: 'View Metrics',
            color: 'primary',
            flat: false,
          },
          cancel: {
            label: 'Got it',
            color: 'primary',
            flat: true,
          },
        })
        .onOk(() => {
          this.setCurrentTab('device-metrics');
        })
        .onDismiss(() => {
          OptionsService.saveOption('metricMoveNotified', true);
        });
    }

    this.$events.$on('devices:partial-update', ({ deviceUuid, partialUpdateData }) => {
      if (deviceUuid !== this.deviceUuid) {
        return;
      }
      this.device = { ...this.device, ...partialUpdateData };
    });
    this.$events.$on('update:device', (device) => {
      if (device && device.uuid && device.uuid === this.device.uuid) {
        this.device = { ...this.device, ...device };
      }
    });
  },
  beforeDestroy() {},
  computed: {
    ...mapGetters({
      packages: 'packages/packages',
      devices: 'devices/devices',
      userSettings: 'ui/userSettings',
    }),

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
    adminMode: {
      get() {
        return this.$store.getters['ui/adminMode'];
      },
      set(v) {
        this.$store.commit('ui/setAdminMode', v);
      },
    },
    installedImageData: {
      get() {
        return ((this.device.updates || []).find((f) => f.active) || {}).custom || {};
      },
    },
    selectedChartTheme: {
      get() {
        return OptionsService.getSavedOptionOrDefault('selectedChartTheme', this.$data._selectedChartTheme);
      },
      set(v) {
        this.$data._selectedChartTheme = v;
        this.updateUserOption('selectedChartTheme', v);
      },
    },
    animateChart: {
      get() {
        return OptionsService.getSavedOptionOrDefault('animateChart', this.$data._animateChart);
      },
      set(v) {
        this.$data._animateChart = v;
        this.updateUserOption('animateChart', v);
      },
    },
    autoUpdateChart: {
      get() {
        return OptionsService.getSavedOptionOrDefault('autoUpdateChart', this.$data._autoUpdateChart);
      },
      set(v) {
        this.$data._autoUpdateChart = v;
        this.updateUserOption('autoUpdateChart', v);
      },
    },
    autoUpdateFreq: {
      get() {
        return OptionsService.getSavedOptionOrDefault('autoUpdateFreq', this.$data._autoUpdateFreq);
      },
      set(v) {
        this.$data._autoUpdateFreq = v;
        this.updateUserOption('autoUpdateFreq', v);
      },
    },
    highlightNoDataRegion: {
      get() {
        return OptionsService.getSavedOptionOrDefault('highlightNoDataRegion', this.$data._highlightNoDataRegion);
      },
      set(v) {
        this.$data._highlightNoDataRegion = v;
        this.updateUserOption('highlightNoDataRegion', v);
      },
    },
    fillChartLines: {
      get() {
        return OptionsService.getSavedOptionOrDefault('fillChartLines', true);
      },
      set(v) {
        this.updateUserOption('fillChartLines', v);
      },
    },
    dateRangeData: {
      set(data) {
        this.$set(this.$data, '_dateData', data);
        this.updateUserOption('devices:chart:date-range-data', data);
      },
      get() {
        this.$data._dateData =
          this.$data._dateData ||
          OptionsService.getSavedOptionOrDefault('devices:chart:date-range-data', {
            from: this.$data._dateFrom,
            to: this.$data._dateTo,
            preset: this.$data._dateRangePreset,
            isCustom: false,
          });
        return this.$data._dateData;
      },
    },
    dateRangePreset: {
      get() {
        return this.dateRangeData.preset;
      },
      set(v) {
        this.dateRangeData = { ...this.dateRangeData, preset: v };
      },
    },
    dateFrom: {
      get() {
        return this.dateRangeData.from;
      },
      set(v) {
        this.dateRangeData = { ...this.dateRangeData, from: v };
      },
    },
    dateTo: {
      get() {
        return this.dateRangeData.to;
      },
      set(v) {
        this.dateRangeData = { ...this.dateRangeData, to: v };
      },
    },
    isCustomDateRange: {
      get() {
        return this.dateRangeData.isCustom;
      },
      set(v) {
        this.dateRangeData = { ...this.dateRangeData, isCustom: v };
      },
    },
  },
  beforeDestroy() {},

  created() {},
  methods: {
    ...mapActions({
      fetchDevice: 'devices/fetchDevice',
      fetchDeviceFleets: 'devices/fetchDeviceFleets',
      getPreviousDevice: 'devices/getPreviousDevice',
      getNextDevice: 'devices/getNextDevice',
      fetchPackages: 'packages/fetchPackages',
      saveUserSettings: 'ui/saveUserSettings',
    }),
    ...mapMutations({
      setCurrentTab: 'ui/setCurrentTab',
    }),
    height,
    setCustomeDateRangeFlag(value) {
      this.isCustomDateRange = value;
    },
    updateUserOption(optionKey, value) {
      OptionsService.saveOption(optionKey, value);
    },
    showEditDialog() {
      this.$events.$emit(`dialogs:rename-device:open`, {
        show: true,
        device: this.device || {},
      });
    },
    setup() {
      this.pageTitle = 'Device Information';
      this.device = { ...(this.deviceData || {}) };
      this.$store.commit('devices/setDeviceListRefreshRate', -1);
      this.$store.commit('devices/setDeviceRefreshRate', 10);
      this.getDevice();

      this.$events.$on('devices:refresh', () => {
        this.refresh();
      });
      this.$data._selectedChartTheme = this.getSavedSettingOrDefault('selectedChartTheme', 'default');
      this.$data._animateChart = this.getSavedSettingOrDefault('animateChart', true);
      this.$data._autoUpdateChart = this.getSavedSettingOrDefault('autoUpdateChart', true);
      this.$data._autoUpdateFreq = this.getSavedSettingOrDefault('autoUpdateFreq', 60 * 1000);
      this.$data._highlightNoDataRegion = this.getSavedSettingOrDefault('highlightNoDataRegion', true);
      this.$data._dateRangePreset = this.getSavedSettingOrDefault('dateRangePreset', 24 * 60 * 60 * 1000);
    },
    getSavedSettingOrDefault(key, defaultValue) {
      return typeof this.userSettings[key] !== 'undefined' ? this.userSettings[key] : defaultValue;
    },
    refresh() {
      this.loadingPackages = true;
      this.getDevice();
      (this.$refs.metricsRef || {}).refresh();
      this.fetchPackages()
        .catch((a) => {})
        .finally((f) => {
          setTimeout(() => {
            this.loadingPackages = false;
          }, 1500);
        });
    },
    toggleSelectedPackage(update) {
      if (this.selectedPackage && this.selectedPackage.name === update.name) {
        this.selectedPackage = null;
      } else {
        this.selectedPackage = { ...update };
      }
    },
    nextDevice() {
      this.getNextDevice(this.deviceUuid)
        .then((d) => {
          const device = d || {};
          this.$router.replace({ name: 'device-detail', params: { deviceId: device.uuid } }).catch((e) => {});
          this.setup();
        })
        .catch((e) => {
          // log("DEV err", e)
        });
    },

    previousDevice() {
      this.getPreviousDevice(this.deviceUuid)
        .then((device) => {
          this.$router
            .replace({ name: 'device-detail', params: { deviceId: device.uuid } })
            .catch((e) => {})
            .finally((a) => {
              this.setup();
            });
        })
        .catch((e) => {});
    },
    deviceDeleted() {
      this.$router.replace({ name: 'devices' }).catch((e) => {});
    },
    async getDevice() {
      this.loading = false;
      if (!this.deviceUuid) {
        return;
      }
      if (!(this.device || {}).uuid) {
        this.loading = true;
      } else {
        this.loadingUpdatedData = true;
      }
      try {
        this.device = await this.fetchDevice(this.deviceUuid);
        try {
          // Wrap in try catch to avoid breaking the page if fleet fetch fails
          const fleetResp = await this.fetchDeviceFleets(this.deviceUuid);
          this.$set(this.device, 'fleets', fleetResp.values || []);
        } catch (e) {
          console.log('Error fetching device fleets', e);
        }
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
          if (this.device.updates.length > 4) {
            this.largeHistoryView = false;
          }
        }
      } catch (error) {
        console.error('Error fetching device', error);
      }
      this.loadingUpdatedData = false;
      this.loading = false;
    },

    calulateActionsPadding() {
      const el = document.querySelector('.q-drawer.q-drawer--left') || {};
      const outputsize = () => {
        let padding = el.offsetWidth;
        this.actionLeftPadding = padding + 'px';
      };
      outputsize();
      new ResizeObserver(outputsize).observe(el);
    },
  },
  watch: {
    selectedPackage(p) {
      if (p) {
        this.giveAttensionToVersionsView = true;
        setTimeout(() => {
          this.showPackageVersions = true;
          this.giveAttensionToVersionsView = false;
        }, 200);
      } else {
        this.showPackageVersions = false;
      }
    },
  },
};
</script>
<style lang="scss" scoped>
.actions-view {
  position: fixed;
  bottom: 0;
  right: 0;
  z-index: 1;
  width: 100%;
  height: 4.5em;
}
.actions-view-spacer {
  position: relative;
  bottom: 0;
  right: 0;
  z-index: 1;
  width: 100%;
  height: 5.5em;
}
</style>
