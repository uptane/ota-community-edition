<template>
  <div class="mb-4">
    <div class="pt-5 pb-5" v-if="refreshingAllCharts">
      <empty no-action title="Rendering charts" message="Hang on for few seconds" icon="autorenew" icon-size="5em" class="justify-center items-center"></empty>
    </div>
    <div class="row " v-else>
      <div
        class="col-xs-12 col-sm-12 col-md-12 col-lg-6 col-xl-6"
        style="position:relative;"
        v-for="(chart, index) in userCharts"
        :class="{
          'v-divide-left': index > 0,
        }"
        :key="'_metric_div_' + index"
      >
        <fleet-chart-metrics
          :chart-options="chart"
          :fleet="fleet"
          :date-from.sync="parsedDateFrom"
          :date-to.sync="parsedDateTo"
          :is-custom-date-range="isCustomDateRange"
          :chart-theme="chartTheme"
          :animate-chart="animateChart"
          :fillChartLines="fillChartLines"
          :auto-update-chart="autoUpdateChart"
          :auto-update-freq="autoUpdateFreq"
          :highlightNoDataRegion="highlightNoDataRegion"
        ></fleet-chart-metrics>
      </div>
    </div>
  </div>
</template>

<script>
import _ from 'lodash';
import { colors, date } from 'quasar';
import ChartTypeSelector from 'src/components/common/ChartTypeSelector.vue';
import ChartPlaceholder from 'src/components/common/ChartPlaceholder.vue';
import FleetChartMetrics from 'src/components/fleets/FleetChartMetrics.vue';
import { mapGetters, mapActions } from 'vuex';
import Empty from 'src/components/common/Empty.vue';

const { formatDate } = date;
const parseDateAsStr = (dateVal) => formatDate(dateVal, 'YYYY-MM-DD HH:mm');
const parseDateAsNumber = (dateStr) => new Date(dateStr).getTime();
export default {
  components: { ChartTypeSelector, ChartPlaceholder, FleetChartMetrics, Empty },
  name: 'FleetChart',
  props: {
    chartTheme: {
      type: String,
      default: 'dark',
    },
    fleet: {
      type: Object,
      default: () => {
        return {};
      },
    },
    animateChart: {
      type: Boolean,
      default: true,
    },
    fillChartLines: {
      type: Boolean,
      default: true,
    },
    autoUpdateChart: {
      type: Boolean,
      default: true,
    },
    autoUpdateFreq: {
      type: Number,
      default: 30 * 1000,
    },
    dateFrom: {
      type: String,
      default: () => formatDate(new Date(1626814098114.538), 'YYYY-MM-DD HH:mm'),
    },
    dateTo: {
      type: String,
      default: () => formatDate(new Date(1626814098114.538 + 5 * 60 * 1000), 'YYYY-MM-DD HH:mm'),
    },
    isCustomDateRange: {
      type: Boolean,
      default: false,
    },
    highlightNoDataRegion: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      deviceMetrics: null,
      showMetricsManager: false,
      refreshingAllCharts: true,
    };
  },
  computed: {
    ...mapGetters({
      defaultCharts: 'metrics/defaultCharts',
      userSettings: 'ui/userSettings',
    }),
    fleetId() {
      return this.fleet.id;
    },
    darkTheme() {
      return this.$q.dark.isActive;
    },
    customCharts: {
      get() {
        const charts = Object.values(this.userSettings.customCharts || {});
        return !!charts && charts.length > 0 ? charts : this.defaultCharts;
      },
      set(value) {
        this.$set(this.userSettings, 'customCharts', { ...value });
      },
    },
    userCharts() {
      return this.refreshingAllCharts ? [] : this.customCharts || this.defaultCharts;
    },
    parsedDateFrom: {
      get() {
        return parseDateAsNumber(this.dateFrom);
      },
      set(v) {
        this.$emit('update:date-from', parseDateAsStr(v));
      },
    },
    parsedDateTo: {
      get() {
        return parseDateAsNumber(this.dateTo);
      },
      set(v) {
        this.$emit('update:date-to', parseDateAsStr(v));
      },
    },
    customChartsString() {
      return JSON.stringify(this.customCharts);
    },
  },
  mounted() {
    this.refresh();
  },
  methods: {
    ...mapActions({}),
    refresh() {
      return new Promise((resolve, reject) => {
        this.refreshingAllCharts = true;
        setTimeout(() => {
          this.refreshingAllCharts = false;
          resolve();
        }, 1000);
      });
    },
  },
  watch: {
    customChartsString(n, o) {
      this.refresh();
    },
    darkTheme(n, o) {
      this.refresh();
    },
  },
};
</script>
