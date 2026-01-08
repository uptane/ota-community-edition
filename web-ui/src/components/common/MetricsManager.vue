<template>
  <div>
    <q-dialog v-model="show" persistent @show="onShow" @hide="onHide">
      <div ref="mainDiv" class="q-card w-90em mxw-90 mxh-90vh h-auto" style="position: relative">
        <q-card-section>
          <div class="row" ref="titleSection">
            <div class="col m-0 p-0 text-h5">Metrics Manager</div>
            <div class="col-auto m-0 p-0 text-h5">
              <q-btn flat icon="close" @click="attemptToCloseDialog">Close</q-btn>
            </div>
          </div>
        </q-card-section>
        <q-separator class=""></q-separator>
        <q-card-section>
          <div class="row" ref="contentSection">
            <div class="col v-divide-right">
              <div class="row ">
                <div class="text-h6 col pl-1 ">Your charts</div>
                <div class="col-auto m-0 p-0 text-h5">
                  <q-btn flat color="primary" icon="add" @click="addChart">Add chart</q-btn>
                </div>
                <div class="col-12 pr-1">
                  <filter-input v-model="chartsFilter" placeholder="Filter charts" :rounded="false"></filter-input>
                </div>
              </div>
              <q-list dense v-if="filteredCustomCharts && filteredCustomCharts.length" style="overflow:auto" class="mxh-40em">
                <draggable :list="customCharts" group="metrics" @start="drag = true" @end="drag = false">
                  <transition-group>
                    <template v-for="(metric, index) in filteredCustomCharts">
                      <q-item :value="metric == selectedChart" :selected="metric == selectedChart" :active="metric == selectedChart" :label="metric.title" :caption="'Unit: ' + metric.unit" :key="index" :clickable="true" color="primary" dense @click="selectChart(metric)">
                        <q-item-section avatar>
                          <q-icon name="drag_handle" size="24" color="white" />
                        </q-item-section>
                        <q-item-section>
                          <q-item-label>
                            {{ metric.title }}
                          </q-item-label>
                          <q-item-label caption> Unit: &nbsp; {{ metric.unit }} </q-item-label>
                        </q-item-section>
                        <q-item-section side>
                          <q-icon class="" name="arrow_right"></q-icon>
                        </q-item-section>
                      </q-item>
                    </template>
                  </transition-group>
                </draggable>
              </q-list>
              <div v-else class="opacity-30 p-1">
                <div v-if="chartsFilter">
                  No charts matching <i>"{{ chartsFilter }}"</i>
                </div>
                <div v-else>No charts available</div>
              </div>
            </div>

            <div class="col-4 " v-if="selectedChart">
              <div ref="selectedChartHeader">
                <div class="text-h6 row h-divide-bottom pl-1">
                  <div class="col ellipsis">{{ selectedChart.title }} <span class="opacity-40 ">chart</span></div>
                  <div class="col-auto">
                    <q-btn flat color="negative" @click="removeSelectedChart()" icon="delete">Remove</q-btn>
                  </div>
                </div>
                <div class="pl-1 pr-1">
                  <inline-edit v-model="selectedChart.title" placeholder="Title" label="Title" />
                  <inline-edit v-model="selectedChart.unit" placeholder="Unit" label="Unit" empty-indicator="Not set" />
                  <inline-edit v-model="selectedChart.min" placeholder="Min" label="Min" empty-indicator="Not set" />
                  <inline-edit v-model="selectedChart.max" placeholder="Max" label="Max" empty-indicator="Not set" />
                  <inline-edit v-model="selectedChart.valueParser" placeholder="Post-processing" label="Post-processing" emit-value map-options :options="valueParserOptions" type="select" empty-indicator="None" />
                  <inline-edit v-model="selectedChart.stackSeries" placeholder="Stack series" label="Stack series" emit-value map-options :options="stackSeriesOptions" type="select" empty-indicator="No" />
                  <div class="text-1 mt-1 h-divide-bottom">Metrics</div>
                </div>
              </div>
              <q-list dense v-if="selectedChartHasCharts" style="overflow:auto" class="mxh-30em">
                <template v-for="(chart, index) in selectedChart.metrics">
                  <q-item :key="index" dense class="h-divide-bottom-dotted">
                    <q-item-section>
                      <q-item-label>{{ chart.name }}</q-item-label>
                      <inline-edit v-model="chart.label" placeholder="Label" label="Label" />
                    </q-item-section>
                    <q-item-section side>
                      <q-btn dense flat color="negative" class="rotate-0" icon="close" @click="removeMetric(chart)" />
                    </q-item-section>
                  </q-item>
                </template>
              </q-list>
              <div v-else class="opacity-30 p-1">
                No metrics selected
              </div>
            </div>
            <q-separator vertical class=""></q-separator>
            <div class="col-4 pl-1  mnh-30em" style="position: relative">
              <div class="text-h6 row" ref="headerDiv">
                <div class="col">Available metrics</div>
                <div class="col-auto">
                  <q-btn flat class="" color="primary" @click="refreshMetrics()" icon="loop" :loading="loadingMetrics">Refresh</q-btn>
                </div>
                <div class="col-12">
                  <filter-input v-model="metricsFilter" placeholder="Filter metrics" :rounded="false"></filter-input>
                </div>
              </div>
              <div v-if="loadingMetrics" class="opacity-60 text-center p-2">Loading metrics ...</div>
              <div v-else-if="availableMetrics && availableMetrics.length" class="w-100">
                <q-list dense style="overflow:auto" class="mxh-40em">
                  <template v-for="(metric, index) in availableMetrics">
                    <q-item :key="index" dense class="h-divide-bottom-dotted">
                      <q-item-section>
                        <q-item-label>{{ metric.name }}</q-item-label>
                      </q-item-section>
                      <q-item-section side v-if="selectedChart">
                        <q-btn dense flat color="primary" @click="addMetric(metric)" icon="add" />
                      </q-item-section>
                    </q-item>
                  </template>
                </q-list>
              </div>
              <div v-else class="opacity-30 p-1">
                <div v-if="metricsFilter">
                  No metrics matching <i>"{{ metricsFilter }}"</i>
                </div>
                <div v-else>No metrics available</div>
              </div>
            </div>
          </div>
        </q-card-section>
        <q-separator class=""></q-separator>
        <q-card-actions>
          <div class="row p-1 w-100" ref="footerSection">
            <div class="col"></div>
            <div class="col-auto" v-if="isDirty">
              <q-btn color="primary" @click="saveChanges" icon="check">Save Changes And Close</q-btn>
            </div>

            <div class="col-auto">
              <q-btn flat @click="attemptToCloseDialog" icon="close">Cancel</q-btn>
            </div>
          </div>
        </q-card-actions>
      </div>
    </q-dialog>
    <q-dialog v-model="showPromptCloseDialog">
      <q-card>
        <q-card-section>
          <div class="text-h6">Unsaved Changes</div>
        </q-card-section>

        <q-card-section class="q-pt-none">
          You have unsaved changes. Are you sure you want to close this dialog?
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat label="Yes" color="primary" @click="closeDialog" />
          <q-btn flat label="No" v-close-popup />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex';
import Empty from './Empty.vue';
import InlineEdit from './InlineEdit.vue';
import draggable from 'vuedraggable';
import FilterInput from './FilterInput.vue';

export default {
  components: { InlineEdit, Empty, draggable, FilterInput },
  name: 'DeviceMetricsSelector',
  props: {},
  data() {
    return {
      show: false,
      addChartDialog: false,
      deviceMetrics: null,
      customCharts: null,
      metricsData: [],
      selectedChart: null,
      metricToAdd: {},
      orignialDeviceMetrics: null,
      showPromptCloseDialog: false,
      loadingMetrics: true,
      metricsFilter: '',
      chartsFilter: '',
    };
  },
  computed: {
    ...mapGetters({
      defaultCharts: 'metrics/defaultCharts',
      // myMetrics: 'metrics/customCharts',
      chartValueParsers: 'metrics/chartValueParsers',
      userSettings: 'ui/userSettings',
    }),
    myMetrics: {
      get() {
        return this.$store.getters['metrics/customCharts'];
      },
      set(value) {
        this.$store.commit('metrics/setCustomCharts', value);
      },
    },

    filteredCustomCharts() {
      return (this.customCharts || this.defaultCharts).filter((metric) => {
        return !!metric && this.matchAsString(metric, this.chartsFilter);
      });
    },
    availableMetrics() {
      const selectedMetrics = (this.selectedChart || {}).metrics;
      return _.uniqBy(this.metricsData, 'name').filter((m) => {
        return (!selectedMetrics || !selectedMetrics[m.name] || m.name !== selectedMetrics[m.name].name) && this.matchAsString(m, this.metricsFilter);
      });
    },
    customChartsString() {
      return JSON.stringify(this.customCharts);
    },
    selectedChartHasCharts() {
      return _.values(this.selectedChart.metrics).length > 0;
    },
    valueParserOptions() {
      return _.map(this.chartValueParsers, (val, key) => {
        return {
          label: val.label,
          value: key,
        };
      });
    },
    stackSeriesOptions() {
      return [
        {
          label: 'Yes',
          value: true,
        },
        {
          label: 'No',
          value: false,
        },
      ];
    },

    contentHeight() {
      return this.mainHeight - this.headerHeight - this.footerHeight;
    },

    selectedChartMetricsHeight() {
      return this.mainHeight - this.getElementHeight('titleSection') - this.selectedChartHeaderHeight;
    },
    selectedChartHeaderHeight() {
      return this.getElementHeight('selectedChartHeader');
    },
    mainHeight() {
      return this.getElementHeight('mainDiv');
    },
    headerHeight() {
      return this.getElementHeight('headerDiv') + this.getElementHeight('titleSection');
    },
    footerHeight() {
      return this.getElementHeight('footerSection');
    },
    isDirty() {
      return !_.isEqual(this.orignialDeviceMetrics, this.customCharts);
    },
  },
  methods: {
    ...mapActions({
      fetchListOfMetrics: 'metrics/fetchListOfMetrics',
      saveUserSettings: 'ui/saveUserSettings',
    }),

    getElementRef(ref) {
      return this.$refs[ref] || {};
    },
    getElementHeight(ref) {
      return +this.getElementRef(ref).offsetHeight;
    },
    selectChart(chart) {
      this.selectedChart = chart;
    },
    refreshMetrics() {
      this.loadingMetrics = true;
      this.fetchListOfMetrics()
        .then((metricsData) => {
          this.metricsData = metricsData;
        })
        .finally(() => {
          this.loadingMetrics = false;
        });
    },
    addMetric(metric) {
      this.selectedChart.metrics = this.selectedChart.metrics || {};
      this.$set(this.selectedChart.metrics, metric.name, metric);
    },
    removeMetric(metric) {
      console.log('removeMetric', metric);
      this.$delete(this.selectedChart.metrics, metric.name);
      //   this.selectedChart.metrics = this.selectedChart.metrics.filter(c => c.name !== chart.name);
    },
    addChart() {
      this.customCharts = this.customCharts || [];
      const metric = {
        title: 'My Chart ' + (this.customCharts.length + 1),
        unit: '%',
        min: 0,
        max: 100,
        stackSeries: false,
        metrics: {},
      };
      this.customCharts.push(metric);
      this.selectChart(metric);
    },
    removeChart(metric) {
      this.customCharts = this.customCharts.filter((m) => m != metric);
    },
    removeSelectedChart() {
      this.$q
        .dialog({
          title: 'Remove Selected Chart',
          message: 'Are you sure you want to proceed?',
          ok: {
            label: 'Yes',
            color: 'primary',
            flat: true,
          },
          cancel: {
            label: 'No',
            color: 'default',
            flat: true,
          },
          persistent: true,
        })
        .onOk(() => {
          this.removeChart(this.selectedChart);
          this.selectedChart = null;
        });
    },
    showAddChartDialog() {
      this.addChartDialog = true;
    },
    equals(a, b) {
      return JSON.stringify(a) === JSON.stringify(b);
    },
    attemptToCloseDialog() {
      if (this.isDirty) {
        this.showPromptCloseDialog = true;
      } else {
        this.closeDialog();
      }
    },
    saveChanges() {
      // Save metrics as object
      this.saveUserSettings({ customCharts: { ...this.customCharts } });
      // this.$q.localStorage.set('customCharts', { ...this.customCharts });
      this.myMetrics = this.customCharts;
      this.show = false;
    },
    closeDialog() {
      this.show = false;
    },
    onShow() {
      this.refreshMetrics();
      // const metrics = this.$q.localStorage.getItem('customCharts');
      const metrics = this.userSettings.customCharts;
      // Convert custom metrics object to array
      const customCharts = metrics ? Object.values(metrics || {}) : null;
      this.myMetrics = customCharts;

      if (this.myMetrics && this.myMetrics.length > 0) {
        this.customCharts = this.copy(this.myMetrics);
      } else {
        this.customCharts = this.copy(this.defaultCharts);
      }
      this.orignialDeviceMetrics = this.copy(this.customCharts);
    },
    onHide() {
      this.showPromptCloseDialog = false;
      this.customCharts = this.copy(this.orignialDeviceMetrics);
      this.selectedChart = null;
    },
    copy(obj) {
      return JSON.parse(JSON.stringify(obj));
    },
    matchAsString(metric, filter) {
      return JSON.stringify(metric).match(new RegExp(filter, 'gi'));
    },
  },
  watch: {
    customChartsString(n, o) {},
  },
  mounted() {
    this.$events.$on('dialogs:metrics-manager:open', (data) => {
      Object.assign(this, data);
      this.show = true;
    });
  },
};
</script>
<style>
.flip-list-move {
  transition: transform 0.5s;
}
.no-move {
  transition: transform 0s;
}
</style>
