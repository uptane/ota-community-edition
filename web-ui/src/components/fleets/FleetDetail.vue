<template>
  <div class>
    <div v-if="loading">
      <div v-if="!quickView" class="row mnh-100vh w-100">
        <card-skeleton v-for="index in 12" :key="'skl_' + index" class="col-md-6 col-lg-4 mnh-25em" style="max-width: auto;" />
      </div>
      <div v-if="quickView" class="p-1 flex flex-center mnh-10em">
        <div class="text-center">
          <div>
            <span class="opacity-90 pr-1">Loading fleet info...</span>
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
                    <div class="col">
                      <span class=" mxw-90 ellipsis">{{ fleet.groupName }}</span> <span class="faded"> fleet </span>
                      <q-btn flat @click="showEditDialog" color="primary" icon="edit">
                        <tooltip>Rename this fleet</tooltip>
                      </q-btn>
                    </div>
                  </div>
                </h5>
                <h5 class="m-0 pl-2 pr-2  pb-1" v-if="$q.screen.gt.sm">
                  <div class="row">
                    <div class=" col mr-0 v-divide-right">
                      <chart-controls
                        :chart-theme.sync="selectedChartTheme"
                        :animateChart.sync="animateChart"
                        :showChartsIn3d.sync="showChartsIn3d"
                        :useDonuts.sync="useDonuts"
                        :fillChartLines.sync="fillChartLines"
                        :autoUpdateChart.sync="autoUpdateChart"
                        :autoUpdateFreq.sync="autoUpdateFreq"
                        :dateFrom="dateFrom"
                        @update:dateFrom="dateFrom = $event"
                        :dateTo="dateTo"
                        @update:dateTo="dateTo = $event"
                        :highlightNoDataRegion.sync="highlightNoDataRegion"
                        :dateRangePreset.sync="dateRangePreset"
                        :dateRangePresetOptions="dateRangePresetOptions"
                        @update:all="refresh"
                        :optionsVisibility="chartOptionsVisible"
                        :is-custom-date-range.sync="isCustomDateRange"
                      ></chart-controls>
                    </div>

                    <div>
                      <q-btn color="primary" flat @click="$events.$emit('dialogs:metrics-downloader:open', { fleet })">
                        <q-icon name="download_for_offline" class="mr-1"></q-icon> Download Data
                        <tooltip>Download monitoring data for offline use</tooltip>
                      </q-btn>
                    </div>

                    <div class=" col-auto">
                      <reload-btn :busy="loadingPackages || loadingFleetData || loading" @reload-requested="refresh" color="primary"></reload-btn>
                    </div>

                    <div class="lt-sm col-12 text-center pt-2">{{ fleet.groupName }} <span class="faded">fleet </span></div>
                  </div>
                </h5>
              </div>
            </div>
            <div class="col-12 mt-1 h-divide-top">
              <fleet-aggregate-chart :fleet="fleet" :showChartsIn3d="showChartsIn3d" :useDonuts="useDonuts" :chartTheme="selectedChartTheme" ref="chartRef"> </fleet-aggregate-chart>
              <q-btn color="primary" flat @click="$events.$emit('dialogs:metrics-manager:open', {})"> <q-icon name="addchart" class="mr-1"></q-icon> Customize Metrics </q-btn>
              <q-btn color="primary" flat @click="$events.$emit('dialogs:metrics-downloader:open', { fleet })">
                <q-icon name="download_for_offline" class="mr-1"></q-icon> Download Data
                <tooltip>Download monitoring data for offline use</tooltip>
              </q-btn>
              <q-separator class="mt-1 mb-1"></q-separator>
            </div>
            <div class="col-12 col-xl-8 mb-2">
              <fleet-information show-all :fleet="fleet" expanded></fleet-information>
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
                  <fleet-actions :horizontal="$q.screen.lt.xl" :fleet="fleet" @deleted="fleetDeleted" :actions="['update', 'devices', 'rename', 'hibernate', 'wakeup', 'delete']" @view-updated="refresh"></fleet-actions>
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
import { mapActions, mapGetters } from 'vuex';
import { dom, extend, date } from 'quasar';
import Loader from 'src/components/loaders/Loader';
import ReloadBtn from 'src/components/common/ReloadBtn';
import Tooltip from 'src/components/common/Tooltip.vue';
import FleetInformation from './FleetInformation.vue';
import FleetActions from './FleetActions.vue';

import FleetChart from './FleetChart.vue';
import DateTimeRangePicker from 'src/components/common/DateTimeRangePicker.vue';
import ChartControls from 'src/components/common/ChartControls.vue';
import FleetAggregateChart from './FleetAggregateChart.vue';
import { ensureCondition } from '../../utils/Common';
import CardSkeleton from '../common/skeletons/CardSkeleton.vue';
import { OptionsService } from '../../services/options.service';

const { height, width } = dom;
const { formatDate } = date;
export default {
  name: 'FleetDetail',
  components: {
    Loader,
    ReloadBtn,
    Tooltip,
    FleetInformation,
    FleetActions,
    FleetChart,
    DateTimeRangePicker,
    ChartControls,
    FleetAggregateChart,
    CardSkeleton,
  },
  props: {
    fleet: {
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
      loadingUpdatedData: false,
      loadingPackages: false,
      loadingDevices: false,
      loadingFleetData: false,
      largeHistoryView: true,
      updateInitiated: false,
      showPackageVersions: false,
      parsedPackageVersions: {},
      giveAttensionToVersionsView: false,
      selectedPackage: null,
      _selectedChartTheme: 'default',
      _showChartsIn3d: true,
      _useDonuts: true,
      _animateChart: true,
      _fillChartLines: true,
      _autoUpdateChart: true,
      _autoUpdateFreq: 60 * 1000,
      _highlightNoDataRegion: true,
      _dateRangePreset: 60 * 60 * 1000,
      dateRangePresetOptions: [{ label: 'Last hour', value: 60 * 60 * 1000 }, { label: 'Last 4 hours', value: 4 * 60 * 60 * 1000 }, { label: 'One day', value: 24 * 60 * 60 * 1000 }, { label: 'One week', value: 7 * 24 * 60 * 60 * 1000 }],
      _dateFrom: formatDate(new Date(Date.now() - 60 * 60 * 1000), 'YYYY-MM-DD HH:mm:ss'),
      _dateTo: formatDate(new Date(), 'YYYY-MM-DD HH:mm:ss'),
      _dateData: null,
      chartOptionsVisible: {
        fillChartLines: false,
        animateChart: false,
        autoUpdateChart: false,
        highlightNoDataRegion: false,
        chartTheme: true,
        dateRangeSelector: false,
        showChartsIn3d: true,
        useDonuts: true,
      },
      actionLeftPadding: '0px',
    };
  },
  created() {},
  mounted() {
    this.setup();
    this.calulateActionsPadding();
  },
  beforeDestroy() {},
  computed: {
    ...mapGetters({
      packages: 'packages/packages',
      fleets: 'fleets/fleets',
      fleetsById: 'fleets/fleetsById',
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
    loading() {
      return this.loadingFleetData || this.loadingDevices || this.loadingPackages || this.loadingUpdatedData;
    },
    fleetId() {
      return this.$route.params.fleetId;
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
        return ((this.fleet.updates || []).find((f) => f.active) || {}).custom || {};
      },
    },
    selectedChartTheme: {
      get() {
        return this.$data._selectedChartTheme;
      },
      set(v) {
        this.$data._selectedChartTheme = v;
        this.updateUserOption('selectedChartTheme', v);
      },
    },
    showChartsIn3d: {
      get() {
        return this.$data._showChartsIn3d;
      },
      set(v) {
        this.$data._showChartsIn3d = v;
        this.updateUserOption('showChartsIn3d', v);
      },
    },
    useDonuts: {
      get() {
        return this.$data._useDonuts;
      },
      set(v) {
        this.$data._useDonuts = v;
        this.updateUserOption('useDonuts', v);
      },
    },
    animateChart: {
      get() {
        return this.$data._animateChart;
      },
      set(v) {
        this.$data._animateChart = v;
        this.updateUserOption('animateChart', v);
      },
    },
    fillChartLines: {
      get() {
        return this.$data._fillChartLines;
      },
      set(v) {
        this.$data._fillChartLines = v;
        this.updateUserOption('fillChartLines', v);
      },
    },
    autoUpdateChart: {
      get() {
        return this.$data._autoUpdateChart;
      },
      set(v) {
        this.$data._autoUpdateChart = v;
        this.updateUserOption('autoUpdateChart', v);
      },
    },
    autoUpdateFreq: {
      get() {
        return this.$data._autoUpdateFreq;
      },
      set(v) {
        this.$data._autoUpdateFreq = v;
        this.updateUserOption('autoUpdateFreq', v);
      },
    },
    highlightNoDataRegion: {
      get() {
        return this.$data._highlightNoDataRegion;
      },
      set(v) {
        this.$data._highlightNoDataRegion = v;
        this.updateUserOption('highlightNoDataRegion', v);
      },
    },

    dateRangeData: {
      set(data) {
        this.$set(this.$data, '_dateData', data);
        this.updateUserOption('fleets:chart:date-range-data', data);
      },
      get() {
        this.$data._dateData =
          this.$data._dateData ||
          OptionsService.getSavedOptionOrDefault('fleets:chart:date-range-data', {
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
      fetchDevices: 'devices/fetchDevices',
      fetchFleet: 'fleets/fetchFleet',
      getPreviousFleet: 'fleets/getPreviousFleet',
      getNextFleet: 'fleets/getNextFleet',
      fetchPackages: 'packages/fetchPackages',
      saveUserSettings: 'ui/saveUserSettings',
    }),
    height,
    updateUserOption(optionKey, value) {
      this.saveUserSettings({ [optionKey]: value });
    },
    showEditDialog() {
      this.$events.$emit(`dialogs:rename-fleet:open`, {
        show: true,
        fleet: this.fleet || {},
      });
    },
    setup() {
      this.pageTitle = 'Fleet Information';

      this.$data._selectedChartTheme = this.getSavedSettingOrDefault('selectedChartTheme', 'default');
      this.$data._animateChart = this.getSavedSettingOrDefault('animateChart', true);
      this.$data._fillChartLines = this.getSavedSettingOrDefault('animateChart', true);
      this.$data._autoUpdateChart = this.getSavedSettingOrDefault('autoUpdateChart', true);
      this.$data._autoUpdateFreq = this.getSavedSettingOrDefault('autoUpdateFreq', 60 * 1000);
      this.$data._highlightNoDataRegion = this.getSavedSettingOrDefault('highlightNoDataRegion', true);
      this.$data._dateRangePreset = this.getSavedSettingOrDefault('dateRangePreset', 24 * 60 * 60 * 1000);
    },
    getSavedSettingOrDefault(key, defaultValue) {
      return typeof this.userSettings[key] !== 'undefined' ? this.userSettings[key] : defaultValue;
    },
    refresh() {
      this.loadingFleetData = true;
      this.$events.$emit('fleets:refresh', {});
      (this.$refs.chartRef || {}).refresh &&
        (this.$refs.chartRef || {}).refresh().finally(() => {
          this.loadingFleetData = false;
        });
    },
    toggleSelectedPackage(update) {
      if (this.selectedPackage && this.selectedPackage.name === update.name) {
        this.selectedPackage = null;
      } else {
        this.selectedPackage = { ...update };
      }
    },
    nextFleet() {
      this.getNextFleet(this.fleetId)
        .then((d) => {
          const fleet = d || {};
          this.$router.replace({ name: 'fleet-detail', params: { fleetId: fleet.uuid } }).catch((e) => {});
          this.setup();
        })
        .catch((e) => {
          // log("DEV err", e)
        });
    },

    previousFleet() {
      this.getPreviousFleet(this.fleetId)
        .then((fleet) => {
          this.$router
            .replace({ name: 'fleet-detail', params: { fleetId: fleet.uuid } })
            .catch((e) => {})
            .finally((a) => {
              this.setup();
            });
        })
        .catch((e) => {});
    },
    fleetDeleted() {
      this.$router.replace({ name: 'fleets' }).catch((e) => {});
    },
    async getFleet() {
      this.loadingFleetData = true;
      try {
        const fleet = await this.fetchFleet(this.fleetId);
        this.fleet = fleet;
      } catch (e) {}
      this.loadingFleetData = false;
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
    darkMode(n, o) {
      this.refresh();
    },
    chartTheme() {
      this.refresh();
    },
    useDonuts() {
      this.refresh();
    },
    showChartsIn3d() {
      this.refresh();
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
