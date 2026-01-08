<template>
  <div style="position:relative;" class="h-divide-bottom">
    <div class="row">
      <div class="col"></div>
      <div class="col-auto pr-1"></div>
      <div class="col-auto">
        <div class="row justify-end items-right">
          <q-btn flat color="primary" icon="troubleshoot" @click="viewOutliers">
            <tooltip>View outliers in this chart</tooltip>
          </q-btn>
          <q-btn flat color="primary" icon="query_stats" @click="zoomInByDevices">
            <tooltip>View this chart in each device </tooltip>
          </q-btn>
          <q-btn flat color="primary" icon="zoom_in" @click="zoomIn">
            <tooltip>Magnify this chart</tooltip>
          </q-btn>
          <q-btn flat color="primary" icon="insights">
            <tooltip>Metric options</tooltip>
            <q-popup-proxy transition-show="scale" transition-hide="scale">
              <chart-type-selector
                v-if="chartOptions.isCPU && chartOptions.showPerCoreBreakdown"
                :core-type.sync="coreType"
                :cores.sync="cpuCores"
                :stackSeries.sync="stackSeries"
                :connectSeries.sync="connectSeries"
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
                  <div class="col p-1">
                    <h6 class="h-divide-bottom m-0 pl-1 pr-1">Show Values</h6>
                    <q-option-group type="toggle" v-model="enabledAggregationValues" :options="aggregationOptions"> </q-option-group>
                  </div>
                  <div class="col p-1 v-divide-left-dashed">
                    <h6 class="h-divide-bottom m-0 pl-1 pr-1">Options</h6>
                    <div>
                      <q-checkbox v-model="stackSeries" label="Stack series" :disable="filteredSeries.length < 2" :color="filteredSeries.length < 2 ? 'grey' : 'primary'"></q-checkbox>
                    </div>
                    <div>
                      <q-checkbox v-model="connectSeries" label="Connect series"></q-checkbox>
                    </div>
                    <!-- <div>
                      <q-checkbox
                        v-model="useMultipleAxes"
                        label="Use multiple y axis"
                      ></q-checkbox>
                    </div> -->
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
          <loader></loader>
        </div>
      </q-card>
    </q-dialog>
    <q-dialog v-model="deviceFocused" transition-show="slide-up" transition-hide="slide-down">
      <q-card class="mnw-90vw ">
        <div class="row">
          <h5 class="col text-center mt-1 mb-1">
            <span>{{ metricTitle }}&nbsp;</span> <span class="faded"> of devices in&nbsp; </span><span>{{ fleet.groupName }}</span>
          </h5>
          <q-btn icon="close" flat class="col-auto" v-close-popup></q-btn>
        </div>
        <q-separator></q-separator>
        <div class="pt-5 pb-5 h-80vh" v-if="loadingDevicesCharts">
          <empty no-action title="Preparing charts for fleet devices" message="Hang on for a few seconds" icon="autorenew" icon-size="5em" class="justify-center items-center"></empty>
        </div>
        <div v-else ref="deviceFocusedChartDiv" class="h-80vh  row overflow-auto" style="position: relative">
          <div class="col-xs-12 col-sm-6 -col-md-4 col-lg-4 col-xl-3" v-for="deviceUuid in fleet.deviceIds" :key="deviceUuid">
            <device-chart-metrics
              :animateChart="false"
              :chartTheme="chartTheme"
              :chartOptions="chartOptions"
              :dateFrom="dateFrom"
              :dateTo="dateTo"
              :deviceUuid="deviceUuid"
              :highlightNoDataRegion="highlightNoDataRegion"
              :chartTitleOverride="(devicesByUuid[deviceUuid] || {}).deviceName"
            ></device-chart-metrics>
          </div>
        </div>
      </q-card>
    </q-dialog>
    <q-dialog v-model="showOutliers" transition-show="slide-up" transition-hide="slide-down">
      <q-card class="mnw-90vw ">
        <div class="row">
          <h5 class="col text-center mt-1 mb-1">
            <span>{{ metricTitle }}&nbsp;</span> <span class="">outliers in&nbsp;</span><span>{{ fleet.groupName }}</span>
          </h5>
          <q-btn icon="close" flat class="col-auto" v-close-popup></q-btn>
        </div>
        <q-separator></q-separator>
        <div class="pt-5 pb-5 h-80vh" v-if="loadingDevicesCharts">
          <empty no-action title="Preparing charts for fleet devices" message="Hang on for a few seconds" icon="autorenew" icon-size="5em" class="justify-center items-center"></empty>
        </div>
        <div v-else ref="deviceFocusedChartDiv" class="h-80vh overflow-auto" style="position: relative">
          <div class="q-pa-md">
            <div class="row">
              <div class="col-auto">
                <q-tabs v-model="outlierTab" shrink no-caps class="bg-theme-bg-dark">
                  <template v-for="outlier in metricOutliers">
                    <q-tab :name="outlier.metricName" :label="outlier.metricName" :key="outlier.metricName + 'tab'" :disable="loadingOutliers" />
                  </template>
                </q-tabs>
              </div>
              <q-space />
              <div class="col-auto mnw-10em">
                <q-select outlined dense v-model="outlierMethod" :options="['Max', 'Min']" label="Aggregation method" @input="fetchMetricOutliers" />
              </div>
            </div>
            <q-separator class="h-divide-bottom"></q-separator>
            <div v-if="loadingOutliers" class="flex flex-center mnh-20em">
              <q-spinner-hourglass size="50px" color="primary" />
              Loading outliers, please wait...
            </div>
            <q-tab-panels v-else v-model="outlierTab" animated class="q-ma-md">
              <template v-for="outlier in metricOutliers">
                <q-tab-panel :key="outlier.metricName + '-panel'" :name="outlier.metricName">
                  <q-table :data="outlier.outliers" :columns="outlierTableColumns" binary-state-sort row-key="name">
                    <template v-slot:body="props">
                      <q-tr :props="props">
                        <q-td key="name" :props="props">
                          <div>{{ (props.row.device || {}).deviceName }}</div>
                          <small class="text-faded">({{ props.row.device.uuid }})</small>
                        </q-td>
                        <q-td key="value" :props="props">
                          {{ props.row.value }}
                        </q-td>
                        <q-td key="observedAt" :props="props">
                          {{ $date.formatDate(props.row.observedAt, 'MM/DD/YYYY hh:mm:ss A') }}
                          <q-btn icon="subtitles" flat round dense type="a" class="q-ml-lg" color="primary" target="_blank" :to="'/devices/' + props.row.device.uuid">
                            <tooltip>
                              <span>View device in a new browser window</span>
                            </tooltip>
                          </q-btn>
                          <!-- v-if="props.row.device.sessionInfo" -->
                          <q-btn icon="terminal" flat round dense color="primary" @click="initiateRemoteSessionForDevice(props.row.device)">
                            <tooltip>
                              <span>Start a remote session with this device</span>
                            </tooltip>
                          </q-btn>
                        </q-td>
                      </q-tr>
                    </template>
                  </q-table>
                </q-tab-panel>
              </template>
            </q-tab-panels>
          </div>
        </div>
      </q-card>
    </q-dialog>
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
import Tooltip from '../common/Tooltip.vue';
import DeviceChartMetrics from '../devices/DeviceChartMetrics.vue';
import Empty from '../common/Empty.vue';

const { formatDate } = date;
export default {
  components: { ChartTypeSelector, ChartPlaceholder, Loader, Tooltip, DeviceChartMetrics, Empty },
  name: 'FleetChartMetrics',
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
  },
  data() {
    return {
      _visibleSeries: null,
      timer: 0,
      focused: false,
      deviceFocused: false,
      loadingDevicesCharts: false,
      pauseAutoUpdate: false,
      metricData: {},
      metricOutliers: [],
      _stackSeries: true,
      _connectSeries: false,
      _showLegends: true,
      _useMultipleAxes: false,
      timeoutID: null,
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
      enabledAggregationValues: ['-avg', '-count', '-max', '-min'],
      aggregationOptions: [
        { value: '-avg', label: 'Average', labelShort: 'Avg', displayUnit: true, opposite: false, props: { val: '-avg' } },
        { value: '-min', label: 'Minimum', labelShort: 'Min', displayUnit: true, opposite: false, props: { val: '-min' } },
        { value: '-max', label: 'Maximum', labelShort: 'Max', displayUnit: true, opposite: false, props: { val: '-max' } },
        { value: '-count', label: 'Device Count', labelShort: 'Device Count', displayUnit: false, opposite: true, props: { val: '-count' } },
      ],
      showOutliers: false,
      outlierTab: '',
      outlierTableColumns: [
        {
          name: 'name',
          required: true,
          label: 'Device',
          align: 'left',
          field: (row) => {
            return (row.device || {}).deviceName;
          },
          format: (val) => `${val}`,
          sortable: true,
        },
        { name: 'value', align: 'left', label: 'Value', field: 'value', sortable: true },
        { name: 'observedAt', align: 'left', label: 'Time observed', field: 'observedAt', sortable: true, sort: (a, b) => parseInt(a, 10) - parseInt(b, 10) },
      ],
      outlierMethod: 'Max',
      loadingOutliers: false,
    };
  },
  computed: {
    ...mapGetters({
      fleetsById: 'fleets/fleetsById',
      devicesByUuid: 'devices/devicesByUuid',
      chartValueParsers: 'metrics/chartValueParsers',
      userSettings: 'ui/userSettings',
    }),
    fleetId() {
      return this.fleet.id;
    },
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
      return this.chartOptions.title;
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
      // if (this.connectSeries) {
      series.forEach((s) => {
        // Remove all points that have null values
        s.points = s.points.filter((p) => !!p[1]);
      });
      // }
      const refPoints = (series[0] || {}).points || [];
      const count = refPoints.length;
      _.range(0, count).forEach((r) => {
        const point = refPoints[r];
        const dataPoint = {
          date: point[0],
        };
        const valueParser = this.chartValueParsers[this.chartOptions.valueParser] || { value: (v) => v };
        series.forEach((s) => {
          if (!s.points[r]) {
            return;
          }
          let value = !s.points[r][1] || this.chartOptions.noDataSeriesValue === s.points[r][1] ? null : s.points[r][1];
          dataPoint[s.name] = value === null || s.name.split('-').pop() == 'count' ? value : valueParser.value(value);
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
    connectSeries: {
      get() {
        return this.$data._connectSeries;
      },
      set(v) {
        this.$data._connectSeries = v;
        this.updateUserOption('connectSeries', v);
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
    enabledAggregations() {
      return this.enabledAggregationValues.map((v) => {
        return this.aggregationOptions.find((a) => a.props.val == v);
      });
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
    this.getData()
      .then((data) => {
        this.metricData = data;
        this.plotChart(this.$refs.chartDiv);
      })
      .catch((err) => {});
    this.triggerAutoUpdate();

    this.$data._stackSeries = this.getSavedSettingOrDefault('stackSeries', true);
    this.$data._connectSeries = this.getSavedSettingOrDefault('connectSeries', true);
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
      fetchMetricData: 'metrics/fetchFleetMetrics',
      fetchFleetMetricOutliers: 'metrics/fetchFleetMetricOutliers',
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
          deviceUUIDs: this.fleet.devices.map((d) => d.uuid),
          metricNames: _.keys(this.metrics),
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
      const createYAxes = (title, addUnit, opposite) => {
        var valueAxis = chart.yAxes.push(new am4charts.ValueAxis());
        if (!this.chartOptions.min && +this.chartOptions.min != 0 && this.chartOptions.min !== '-') {
          valueAxis.min = this.chartOptions.min;
        }
        if (typeof this.chartOptions.max !== 'undefined' && this.chartOptions.max !== null && this.chartOptions.max !== '-') {
          valueAxis.max = this.chartOptions.max;
        }
        const unit = !!this.chartOptions.unit.trim() && addUnit ? `(${this.chartOptions.unit})` : '';
        valueAxis.title.text = `${title} ${unit}`;
        valueAxis.renderer.opposite = opposite;
        return valueAxis;
      };

      this.dateAxis = chart.xAxes.push(new am4charts.DateAxis());
      this.dateAxis.renderer.grid.template.location = 0;
      this.dateAxis.renderer.minGridDistance = 45;
      this.dateAxis.title.text = 'Time';
      if (this.dateTo - this.dateFrom > 60 * 60 * 24 * 1000) {
        this.dateAxis.tooltipDateFormat = 'MMM dd, HH:mm';
      } else {
        this.dateAxis.tooltipDateFormat = 'HH:mm';
      }
      // this.dateAxis.baseInterval = {
      //   "timeUnit": "second",
      //   "count": 1
      // };

      let charts = this.chartValues.filter((m) => {
        const arr = m.name.split('-');
        arr.splice(arr.length - 2, 1);
        const name = m.name;
        return this.visibleSeries.indexOf(name) != -1;
      });

      charts.forEach((m, i) => {
        // if (this.useMultipleAxes) {
        let vAxis = createYAxes(m.label || m.name, true, false);
        vAxis.adjustLabelPrecision = false;

        this.enabledAggregations.forEach((agg) => {
          const type = agg.value;
          let deviceCountAxis;
          if (type === '-count') {
            if (i > 0) {
              return;
            } else {
              deviceCountAxis = createYAxes('Device Count', false, true);
              deviceCountAxis.min = 0;
              deviceCountAxis.max = this.fleet.devices.length * 2;
              deviceCountAxis.minVerticalGap = 1;
              deviceCountAxis.labelsEnabled = false;
              deviceCountAxis.ignoreAxisWidth = true;
              deviceCountAxis.integersOnly = true;
              deviceCountAxis.guides = _.range(0, this.fleet.devices.length).map((r) => ({
                lineAlpha: 0.15,
                value: r,
                label: `${r}`,
              }));
            }
          }
          const displayName = type === '-count' ? agg.label : (m.label || m.name) + ' ' + agg.labelShort;
          const tooltipText = type === '-count' ? `${agg.label}: {${m.name + type}}` : `${(m.label || m.name) + ' ' + agg.labelShort}: {${m.name + type}}${agg.displayUnit ? this.chartOptions.unit : ''}`;

          var series = chart.series.push(new am4charts.LineSeries());
          series.name = displayName;
          series.id = m.name + type;
          series.dataFields.valueY = m.name + type;
          series.dataFields.dateX = 'date';
          series.tooltipText = tooltipText;
          series.strokeWidth = 2;
          series.tooltip.pointerOrientation = 'vertical';
          series.sequencedInterpolation = true;
          series.stacked = false; // this.stackSeries;
          series.connect = this.connectSeries;
          series.tensionX = 1;

          vAxis.renderer.line.strokeOpacity = 1;
          vAxis.renderer.line.strokeWidth = 2;
          vAxis.renderer.line.stroke = series.stroke;
          vAxis.renderer.labels.template.fill = series.stroke;
          vAxis.title.marginLeft = 10;
          vAxis.title.marginRight = -5;
          vAxis.title.fill = series.stroke;
          vAxis.title.disabled = true;
          if (type === '-count') {
            deviceCountAxis.renderer.line.strokeOpacity = 1;
            deviceCountAxis.renderer.line.strokeWidth = 2;
            deviceCountAxis.renderer.line.stroke = series.stroke;
            deviceCountAxis.renderer.labels.template.fill = series.stroke;
            series.yAxis = deviceCountAxis;
            series.fillOpacity = 0.05;
            series.strokeWidth = 2;
            series.strokeDasharray = '1,2';
          } else {
            series.fillOpacity = this.fillChartLines ? (this.stackSeries ? 0.6 : 0.25) : 0;
            series.yAxis = vAxis;
          }
          if (type === '-max' || type === '-min') {
            // series.strokeWidth = 1;
            series.fillOpacity = 0.25;
            if (type === '-max') {
              series.strokeDasharray = '8,4,2,4';
            } else {
              series.strokeDasharray = '2,2';
            }
          }
        });
      });

      chart.cursor = new am4charts.XYCursor();
      chart.cursor.xAxis = this.dateAxis;
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
        this.chart.data = this.filteredData;
        this.chart.validateData();
        if (this.chart.animateAgain) {
          this.chart.animateAgain();
        }
      }
    },

    clearNoDataRanges() {
      if (!this.dateAxis) {
        return;
      }
      if (this.dateAxis.axisRanges) {
        this.dateAxis.axisRanges.clear();
      }
    },
    addNoDataRanges() {
      if (!this.dateAxis) {
        return;
      }
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
          range.axisFill.fillOpacity = 0.05;
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
    zoomInByDevices() {
      this.loadingDevicesCharts = true;
      this.deviceFocused = true;
      setTimeout(() => {
        this.loadingDevicesCharts = false;
      }, 1000);
    },
    viewOutliers() {
      // Show loading dialog
      let loader = this.$q.dialog({
        title: 'Loading Outliers',
        message: 'Please wait while we load the outliers for the selected metric.',
        progress: {
          color: 'primary',
        },
        persistent: true,
        ok: false,
        cancel: false,
      });
      this.fetchMetricOutliers()
        .then((data) => {
          this.metricOutliers = data;
          this.showOutliers = true;
          this.outlierTab = ((this.metricOutliers || [])[0] || {}).metricName;
        })
        .catch((err) => {
          console.error(err);
          // show error dialog
          this.$q
            .dialog({
              title: 'Error',
              message: 'An error occurred while loading the outliers. Please try again.',
              ok: {
                color: 'primary',
                label: 'Try Again',
              },
              cancel: {
                color: 'primary',
                label: 'Close',
                flat: true,
              },
            })
            .onOk(() => {
              this.viewOutliers();
            });
        })
        .finally(() => {
          loader.hide();
        });
    },
    initiateRemoteSessionForDevice(device) {
      this.$events.$emit('devices:initiateRemoteSession', device);
    },
    fetchMetricOutliers() {
      this.loadingOutliers = true;
      return new Promise((resolve, reject) => {
        this.fetchFleetMetricOutliers({
          deviceUUIDs: this.fleet.devices.map((d) => d.uuid),
          metricNames: _.keys(this.metrics),
          from: this.dateFromModel,
          to: this.dateToModel,
          aggregation: this.outlierMethod,
        })
          .then((data) => {
            this.metricOutliers = data;
            resolve(data);
          })
          .catch(reject)
          .finally(() => {
            this.loadingOutliers = false;
          });
      });
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
    connectSeries() {
      this.plotChart();
    },
  },
};
</script>
