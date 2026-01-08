<template>
  <div style="position:relative;" class="h-divide-bottom">
    <div v-if="loading" class="row">
      <div class="col-12 p-0 m-0">
        <div class="w-100 h-25em flex flex-center">
          <q-spinner-hourglass size="7em" color="primary" class="opacity-20" />
        </div>
      </div>
      <div class="col-12">
        <h6 class="text-center m-0 opacity-60">{{ metricTitle }}</h6>
      </div>
    </div>
    <div v-else>
      <div class="row">
        <div class="col"></div>
        <div class="col-auto pr-1"></div>
        <div class="col-auto">
          <div class="row justify-end items-right">
            <q-btn flat color="primary" icon="zoom_in" @click="zoomIn"></q-btn>
            <q-btn flat color="primary" icon="insights">
              <q-popup-proxy transition-show="scale" transition-hide="scale">
                <chart-type-selector
                  v-if="chartOptions.isCPU && chartOptions.showPerCoreBreakdown"
                  :core-type.sync="coreType"
                  :cores.sync="cpuCores"
                  :stackSeries.sync="stackSeries"
                  :showLegends.sync="showLegends"
                  :processes.sync="cpuProcesses"
                  :granularity.sync="cpuUsageChartGranularity"
                  @update="updateCpuUsageData"
                  class="light-card"
                ></chart-type-selector>
                <div class="p-1" v-else>
                  <div class="row mnw-30em">
                    <div class="col p-1">
                      <h6 class="h-divide-bottom m-0 pl-1 pr-1">Series</h6>
                      <q-option-group type="toggle" v-model="visibleSeries" :options="filteredSeries"> </q-option-group>
                    </div>
                    <div class="col p-1 v-divide-left-dashed">
                      <h6 class="h-divide-bottom m-0 pl-1 pr-1">Options</h6>
                      <div>
                        <q-checkbox v-model="stackSeries" label="Stack series" :disable="filteredSeries.length < 2" :color="filteredSeries.length < 2 ? 'grey' : 'primary'"></q-checkbox>
                      </div>
                      <div>
                        <q-checkbox v-model="showLegends" label="Show legends" :color="'primary'"></q-checkbox>
                      </div>
                    </div>
                  </div>
                  <div class="h-divide-top">
                    <q-btn flat class="w-100" color="primary" @click="updateSeries()" v-close-popup>
                      Update Chart
                    </q-btn>
                  </div>
                </div>
              </q-popup-proxy>
            </q-btn>
          </div>
        </div>
      </div>
      <div ref="chartDiv" style="height:25em"></div>
      <chart-placeholder v-if="chartUnavailable"></chart-placeholder>
      <h6 class="text-center m-0 opacity-60">{{ metricTitle }}</h6>
      <q-dialog v-model="focused" transition-show="slide-up" transition-hide="slide-down">
        <q-card class="mnw-80vw ">
          <h6 class="text-center mt-1 m-0 opacity-60">{{ metricTitle }} Metric</h6>
          <div ref="focusedChartDiv" class="h-70vh flex-center row" style="position: relative">
            <loader color="primary"></loader>
          </div>
        </q-card>
      </q-dialog>
    </div>
  </div>
</template>

<script>
import _ from 'lodash';
import { mapActions, mapGetters } from 'vuex';
import { date } from 'quasar';
import * as am4core from '@amcharts/amcharts4/core';
import * as am4charts from '@amcharts/amcharts4/charts';
import am4themes_animated from '@amcharts/amcharts4/themes/animated';
import am4themes_dark from '@amcharts/amcharts4/themes/dark';
import am4themes_material from '@amcharts/amcharts4/themes/material';
import am4themes_frozen from '@amcharts/amcharts4/themes/frozen';
import am4themes_dataviz from '@amcharts/amcharts4/themes/dataviz';
import am4themes_kelly from '@amcharts/amcharts4/themes/kelly';
import am4themes_spiritedaway from '@amcharts/amcharts4/themes/spiritedaway';
import am4themes_moonrisekingdom from '@amcharts/amcharts4/themes/moonrisekingdom';
import placeholderData from 'src/components/common/monitor-data.json';
import ChartTypeSelector from 'src/components/common/ChartTypeSelector.vue';
import ChartPlaceholder from 'src/components/common/ChartPlaceholder.vue';
import Loader from 'src/components/loaders/Loader.vue';

const { formatDate } = date;
export default {
  components: { ChartTypeSelector, ChartPlaceholder, Loader },
  name: 'DeviceChartMetrics',
  props: {
    chartTheme: {
      type: String,
      default: 'dark',
    },
    deviceUuid: {
      type: String,
      default: '',
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
      type: Number,
      default: () => new Date(Date.now() - 5 * 60 * 1000).getTime(),
    },
    dateTo: {
      type: Number,
      default: () => new Date().getTime(),
    },
    isCustomDateRange: {
      type: Boolean,
      default: false,
    },
    chartOptions: {
      type: Object,
      default: () => {
        return {};
      },
    },
    highlightNoDataRegion: {
      type: Boolean,
      default: true,
    },
    chartTitleOverride: {
      type: String,
      default: '',
    },
  },
  data() {
    return {
      _visibleSeries: null,
      timer: 0,
      focused: false,
      pauseAutoUpdate: false,
      metricData: {},
      _stackSeries: true,
      _showLegends: true,
      _useMultipleAxes: false,
      timeoutID: null,
      loading: false,
      chartThemes: {
        dark: am4themes_dark,
        material: am4themes_material,
        frozen: am4themes_frozen,
        dataviz: am4themes_dataviz,
        kelly: am4themes_kelly,
        spiritedaway: am4themes_spiritedaway,
        moonrisekingdom: am4themes_moonrisekingdom,
      },
      dateAxis: null,
      /** @type {am4charts.XYChart} */
      chart: null,
      cpuUsageChartGranularity: 'processes',
      cpuProcesses: ['cpu_p', 'user_p', 'system_p'],
      cpuCores: ['cpu0_p_cpu', 'cpu1_p_cpu', 'cpu2_p_cpu', 'cpu3_p_cpu'],
      coreType: 'cpu_p',
    };
  },
  computed: {
    ...mapGetters({
      chartValueParsers: 'metrics/chartValueParsers',
      userSettings: 'ui/userSettings',
    }),
    chartUnavailable() {
      return !this.metricData || !this.metricData.series || this.metricData.series.length < 1;
    },
    chartData() {
      if (this.chartUnavailable) {
        return placeholderData;
      }
      return this.metricData;
    },
    chartValues() {
      return _.values(this.metrics);
    },
    metrics() {
      return this.chartOptions.metrics;
    },
    metricTitle() {
      return this.chartTitleOverride || this.chartOptions.title;
    },
    visibleSeries: {
      get() {
        return this.$data._visibleSeries || this.filteredSeries.map((a) => a.value);
      },
      set(v) {
        this.$data._visibleSeries = v;
      },
    },
    filteredSeries() {
      const series =
        (
          this.chartValues.map((a) => {
            return { ...a, value: a.name, label: a.label || a.name };
          }) || []
        ).filter((m) => {
          let show = m.show;
          if (typeof m.show == 'function') {
            show = m.show(this.metricData.series || []);
          } else if (typeof m.show == 'undefined') {
            show = true;
          }
          return show;
        }) || [];
      if (series.length == 1) {
        series[0] = { ...series[0], disable: true };
      }
      return series;
    },
    filteredData() {
      const data = [];
      const series = this.chartData.series || [];
      const refPoints = (series[0] || {}).points || [];
      const count = refPoints.length;
      _.range(0, count).forEach((r) => {
        const point = refPoints[r];
        const dataPoint = {
          date: point[0],
        };
        const valueParser = this.chartValueParsers[this.chartOptions.valueParser] || { value: (v) => v };
        series.forEach((s) => {
          dataPoint[s.name] = !s.points[r][1] || this.chartOptions.noDataSeriesValue === s.points[r][1] ? null : valueParser.value(s.points[r][1]);
        });
        data.push(dataPoint);
      });
      return data;
    },
    noDataRanges() {
      const data = [];
      const series = (this.chartData.series || []).find((s) => s.name === this.chartOptions.noDataSeriesName) || (this.chartData.series || [])[0] || { points: [] };
      const count = series.points.length;
      let start = null;
      let end = null;
      _.range(0, count).forEach((r) => {
        const [date, value] = series.points[r];
        if (value == null || this.chartOptions.noDataSeriesValue === value) {
          if (!start) {
            start = series.points[r][0];
          } else {
            end = series.points[r][0];
          }
        } else {
          if (start && end) {
            data.push([start, end]);
            start = null;
          } else if (start) {
            data.push([start, series.points[r][0]]);
          }
          start = null;
          end = null;
        }
      });
      return data;
    },
    dateFromModel: {
      get() {
        return this.dateFrom;
      },
      set(v) {
        this.$emit('update:date-from', v);
      },
    },
    dateToModel: {
      get() {
        return this.dateTo;
      },
      set(v) {
        this.$emit('update:date-to', v);
      },
    },
    stackSeries: {
      get() {
        return this.$data._stackSeries;
      },
      set(v) {
        this.$data._stackSeries = v;
        this.updateUserOption('stackSeries', v);
      },
    },
    useMultipleAxes: {
      get() {
        return this.$data._useMultipleAxes;
      },
      set(v) {
        this.$data._useMultipleAxes = v;
        this.updateUserOption('useMultipleAxes', v);
      },
    },
    showLegends: {
      get() {
        return this.$data._showLegends;
      },
      set(v) {
        this.$data._showLegends = v;
        this.updateUserOption('showLegends', v);
      },
    },
    chartOptionsString() {
      return JSON.stringify(this.chartOptions);
    },
  },
  beforeDestroy() {
    this.clearAutoUpdate();
  },
  mounted() {
    this.setChartTheme();
    this.loading = true;
    this.getData()
      .then((data) => {
        this.loading = false;
        this.metricData = data;
        setTimeout(() => {
          this.plotChart(this.$refs.chartDiv);
        }, 200);
      })
      .catch((err) => {})
      .finally(() => {
        this.loading = false;
      });
    this.triggerAutoUpdate();

    this.$data._stackSeries = this.getSavedSettingOrDefault('stackSeries', true);
    this.$data._showLegends = this.getSavedSettingOrDefault('showLegends', true);
    this.$events.$on('update:date-range-selector-active', (active) => {
      this.pauseAutoUpdate = active;
    });
    this.$events.$on('update:date-range-selector-updated', () => {
      this.refreshChartData();
    });
  },
  methods: {
    ...mapActions({
      fetchMetricData: 'metrics/fetchDeviceMetrics',
      saveUserSettings: 'ui/saveUserSettings',
    }),
    updateUserOption(optionKey, value) {
      this.saveUserSettings({ [optionKey]: value });
    },
    getSavedSettingOrDefault(key, defaultValue) {
      return typeof this.userSettings[key] !== 'undefined' ? this.userSettings[key] : defaultValue;
    },
    getData() {
      return new Promise((resolve, reject) => {
        this.fetchMetricData({
          deviceUUID: this.deviceUuid,
          metrics: this.metrics,
          from: this.dateFromModel,
          to: this.dateToModel,
        })
          .then((data) => {
            this.metricData = data;
            resolve(data);
          })
          .catch(reject);
      });
    },
    refreshChartData() {
      if (this.timeoutID) {
        clearTimeout(this.timeoutID);
      }
      this.timeoutID = setTimeout(() => {
        this.getData()
          .then((data) => {
            this.updateChartData();
          })
          .catch((err) => {});
      }, 100);
    },
    updateCpuUsageData() {
      this.plotChart(this.$refs.chartDiv);
    },
    plotChart(parent, /* set this.chart to the newly created instance */ useVmInstance = true) {
      this.setChartTheme();
      // Create chart instance
      var chart = am4core.create(parent, am4charts.XYChart);
      chart.data = this.filteredData;
      if (useVmInstance) {
        this.chart = chart;
      }

      // Create axes
      const createYAxes = (showAxisTitle) => {
        var valueAxis = chart.yAxes.push(new am4charts.ValueAxis());
        if (!this.chartOptions.min && +this.chartOptions.min != 0 && this.chartOptions.min !== '-') {
          valueAxis.min = this.chartOptions.min;
        }
        if (typeof this.chartOptions.max !== 'undefined' && this.chartOptions.max !== null && this.chartOptions.max !== '-') {
          valueAxis.max = this.chartOptions.max;
        }
        if (showAxisTitle) {
          const unit = !!this.chartOptions.unit.trim() ? `(${this.chartOptions.unit})` : '';
          valueAxis.title.text = `${this.chartOptions.title} ${unit}`;
        }
        return valueAxis;
      };

      var dateAxis = chart.xAxes.push(new am4charts.DateAxis());
      dateAxis.renderer.grid.template.location = 0;
      dateAxis.renderer.minGridDistance = 45;
      dateAxis.title.text = 'Time';
      // dateAxis.baseInterval = {
      //   "timeUnit": "second",
      //   "count": 1
      // };

      let charts = this.chartValues.filter((m) => {
        return this.visibleSeries.indexOf(m.name) != -1;
      });
      if (this.chartOptions.isCPU) {
        charts = charts.filter((f) => {
          let chartType = this.cpuCores;
          if (this.cpuUsageChartGranularity === 'processes') {
            chartType = this.cpuProcesses;
          }
          return chartType.indexOf(f.name) !== -1;
        });
      }

      let vAxis = createYAxes(!this.useMultipleAxes);

      charts.forEach((m) => {
        if (this.useMultipleAxes) {
          vAxis = createYAxes(!this.useMultipleAxes);
        }
        var series = chart.series.push(new am4charts.LineSeries());
        series.name = m.label || m.name;
        series.id = m.name;
        series.yAxis = vAxis;
        series.dataFields.valueY = m.name;
        series.dataFields.dateX = 'date';
        series.tooltipText = `${m.label || m.name}: {${m.name}}${this.chartOptions.unit}`;
        series.strokeWidth = 2;
        series.tooltip.pointerOrientation = 'vertical';
        series.sequencedInterpolation = true;
        if (this.fillChartLines) {
          series.fillOpacity = this.stackSeries ? 0.6 : 0.25;
        } else {
          series.fillOpacity = 0;
        }
        series.stacked = this.stackSeries;
        series.connect = true;
        series.tensionX = 1;
      });
      chart.cursor = new am4charts.XYCursor();
      chart.cursor.xAxis = dateAxis;
      this.dateAxis = dateAxis;
      // axis ranges
      if (this.highlightNoDataRegion) {
        this.addNoDataRanges();
      }

      if (this.showLegends) {
        chart.legend = new am4charts.Legend();
        chart.legend.position = 'top';
      }
    },
    updateChartData() {
      if (this.chart) {
        if (this.highlightNoDataRegion) {
          this.addNoDataRanges();
        } else {
          this.clearNoDataRanges();
        }
        if (this.dateTo - this.dateFrom > 60 * 60 * 24 * 1000) {
          this.dateAxis.tooltipDateFormat = 'MMM dd, HH:mm';
        } else {
          this.dateAxis.tooltipDateFormat = 'HH:mm';
        }
        this.chart.data = this.filteredData;
        this.chart.validateData();
        if (this.chart.animateAgain) {
          this.chart.animateAgain();
        }
      }
    },

    clearNoDataRanges() {
      if (this.dateAxis.axisRanges) {
        this.dateAxis.axisRanges.clear();
      }
    },
    addNoDataRanges() {
      if (this.dateAxis.axisRanges) {
        this.dateAxis.axisRanges.clear();
      }
      this.noDataRanges.forEach((r) => {
        if (r[1] - r[0] >= 30 * 1000) {
          /** @type {am4charts.DateAxisDataItem}  */
          var range = this.dateAxis.axisRanges.create();
          range.date = new Date(r[0]);
          range.endDate = new Date(r[1]);
          range.axisFill.fill = this.chart.colors.getIndex(7);
          range.axisFill.fillOpacity = 0.2;
          // range.label.text = "No data";
          range.label.inside = true;
          range.label.rotation = 90;
          range.label.horizontalCenter = 'right';
          range.label.verticalCenter = 'bottom';
        }
      });
    },

    setChartTheme() {
      am4core.unuseAllThemes();
      if (this.animateChart) {
        am4core.useTheme(am4themes_animated);
      }
      if (this.$q.dark.isActive) {
        am4core.useTheme(am4themes_dark);
      }
      if (this.chartTheme !== 'default') {
        am4core.useTheme(this.chartThemes[this.chartTheme]);
      }
    },
    updateSeries() {
      this.plotChart(this.$refs.chartDiv);
    },
    adjustDateRange() {
      // Only adjust the date range if the user is not using a custom date range
      if (!this.isCustomDateRange) {
        this.dateFromModel = Date.now() - (this.dateToModel - this.dateFromModel);
        this.dateToModel = Date.now();
      }
    },
    triggerAutoUpdate() {
      if (this.autoUpdateChart) {
        this.clearAutoUpdate();
        const freq = this.autoUpdateFreq;
        this.timer = setInterval(() => {
          // Do not update the chart if pause flag is set
          if (!this.pauseAutoUpdate) {
            this.adjustDateRange();
            this.refreshChartData();
          }
        }, freq);
      } else {
        this.clearAutoUpdate();
      }
    },
    clearAutoUpdate() {
      clearInterval(this.timer);
    },
    zoomIn() {
      this.focused = true;
      setTimeout(() => {
        this.plotChart(this.$refs.focusedChartDiv, false);
      }, 500);
    },
  },
  watch: {
    dateFrom() {
      this.refreshChartData();
    },
    dateTo() {
      this.refreshChartData();
    },
    chartTheme() {
      this.plotChart(this.$refs.chartDiv);
    },
    autoUpdateFreq() {
      this.triggerAutoUpdate();
    },
    autoUpdateChart() {
      this.triggerAutoUpdate();
    },
    filteredData() {
      this.updateChartData();
    },
    highlightNoDataRegion() {
      this.updateChartData();
    },
    fillChartLines() {
      this.plotChart(this.$refs.chartDiv);
    },
  },
};
</script>
