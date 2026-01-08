<template>
  <q-dialog v-model="show" @hide="onClose">
    <q-card v-if="downloadError" class="mnw-40em">
      <q-card-section class="text-center">
        <h1 class="m-0"><q-icon size="1.2em" name="info" class="text-negative mr-1"></q-icon> Download Failed</h1>
        <p>{{ downloadError }}</p>
      </q-card-section>
      <q-card-section class="text-center">
        <q-btn flat class="w-100" color="primary" icon="refresh" @click="downloadError = null">
          &nbsp; Try Again
        </q-btn>
      </q-card-section>
    </q-card>
    <q-card v-else-if="downloadSuccess" class="mnw-40em">
      <q-card-section class="text-center">
        <h1 class="m-0"><q-icon size="1.2em" name="check_circle" class="text-positive mr-1"></q-icon> Data successfully Downloaded</h1>
        <p>
          Look in your download folder for the file with name <em>{{ downloadSuccess }}</em>
        </p>
      </q-card-section>
      <q-card-section class="text-center ">
        <q-btn flat class="w-100" color="default" icon="close" v-close-popup>
          &nbsp; Close
        </q-btn>
      </q-card-section>
    </q-card>
    <q-card v-else class="mnw-40em">
      <q-card-section class="text-center">
        <h1 class="m-0">Download Monitoring Data</h1>
      </q-card-section>
      <q-card-section class="justify-center flex flex-center">
        <q-form ref="metricsDataForm">
          <div class="row mxw-30em ">
            <div class="col-12 opacity-40 text-1">
              Date range:
            </div>
            <div class="col-auto">
              <date-time-range-picker :from.sync="dateFrom" :to.sync="dateTo" :tabbed="true" :button-only="$q.screen.lt.md" :dateRangePreset.sync="dateRangePreset" :presetOptions="dateRangePresetOptions" doneLabel="Done"></date-time-range-picker>
            </div>
            <div class="row mxw-30em mt-2">
              <div class="col-12 opacity-40 text-1">
                Data Aggregation:
              </div>
              <div class="col-auto">
                <q-radio v-model="dataType" checked-icon="task_alt" unchecked-icon="panorama_fish_eye" val="raw" label="Raw data" :disable="downloading">
                  <tooltip>
                    <span>
                      Download as raw data (no aggregation)
                    </span>
                  </tooltip>
                </q-radio>
                <q-radio v-model="dataType" checked-icon="task_alt" unchecked-icon="panorama_fish_eye" val="avg" label="Averaged data" :disable="downloading">
                  <tooltip>
                    <span>
                      Download as averaged data, calculated by averaging the data over a time period.
                    </span>
                  </tooltip>
                </q-radio>
              </div>
              <div v-if="dataType !== 'raw'" class="col-12 mt-2">
                <div class="row justify-left">
                  <div class="col-12 faded  text-1">
                    Total buckets:
                  </div>
                  <div class="col-auto">
                    <q-input type="number" outlined dense v-model="total_buckets" :disable="downloading" class="mxw-20em" :rules="[(val) => val <= 2000 || 'Total buckets `resolution` is limited to 2000']" />
                  </div>
                </div>
              </div>
              <div v-else class="col-12 mt-2">
                <div class="row justify-left">
                  <div class="col-12 faded  text-1">
                    Raw data point count:
                  </div>
                  <div class="col-auto">
                    <q-input type="number" outlined dense v-model="raw_datapoints" :disable="downloading" class="mxw-20em" :rules="[(val) => val <= 2000 || 'Max raw datapoints is 2000']" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </q-form>
      </q-card-section>
      <q-card-section class="row p-2 justify-center">
        <q-btn color="primary" :loading="downloading" :disable="downloading" @click="downloadData">
          <q-icon name="download_for_offline" class="mr-1"></q-icon> Download Data
          <template v-slot:loading>
            <loader color="default" class="mr-1"></loader> Downloading...
          </template>
        </q-btn>
      </q-card-section>
    </q-card>
  </q-dialog>
</template>

<script>
import { exportFile } from 'quasar';
import { mapActions, mapGetters } from 'vuex';
import DateTimeRangePicker from './DateTimeRangePicker.vue';
import Tooltip from './Tooltip.vue';
import Loader from '../loaders/Loader.vue';
import { date } from 'quasar';
const { formatDate } = date;
const parseDateAsStr = (dateVal) => formatDate(dateVal, 'YYYY-MM-DD HH:mm');
const parseDateAsNumber = (dateStr) => new Date(dateStr).getTime();

export default {
  components: { DateTimeRangePicker, Tooltip, Loader },
  name: 'MetricsDataDownloader',
  props: {},
  data() {
    return {
      show: false,
      dateFrom: new Date(Date.now() - 60 * 60 * 1000).toString(),
      dateTo: new Date().toString(),
      dateRangePreset: 60 * 60 * 1000,
      dateRangePresetOptions: [{ label: 'Last hour', value: 60 * 60 * 1000 }, { label: 'Last 4 hours', value: 4 * 60 * 60 * 1000 }, { label: 'One day', value: 24 * 60 * 60 * 1000 }, { label: 'One week', value: 7 * 24 * 60 * 60 * 1000 }],
      dataType: 'raw',
      total_buckets: 1000,
      raw_datapoints: 1000,
      downloading: false,
      downloadError: null,
      downloadSuccess: null,
      device: null,
      fleet: null,
    };
  },
  computed: {
    ...mapGetters({
      defaultCharts: 'metrics/defaultCharts',
      userSettings: 'ui/userSettings',
    }),
    avgTimeInterval() {
      return this.dateRangePreset / 1000 / 60 / 60;
    },
    deviceId() {
      return this.device.deviceId;
    },
    deviceUuid() {
      return this.device.uuid;
    },
    fleetDeviceUuids() {
      if (!this.fleet) return [];
      return this.fleet.deviceIds;
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
    metrics() {
      return _.reduce(
        this.customCharts || this.defaultCharts,
        (acc, chart) => {
          Object.keys(chart.metrics).forEach((key) => {
            acc[key] = chart.metrics[key];
          });
          return acc;
        },
        {},
      );
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
  },
  methods: {
    ...mapActions({
      fetchDeviceMetricsCSV: 'metrics/fetchDeviceMetricsCSV',
      fetchFleetMetricsCSV: 'metrics/fetchFleetMetricsCSV',
    }),
    downloadData() {
      this.$refs.metricsDataForm.validate(true).then((valid) => {
        if (valid) {
          this.downloadError = null;
          this.downloadSuccess = null;
          this.downloading = true;
          const params = {
            from: this.parsedDateFrom,
            to: this.parsedDateTo,
            metrics: this.metrics,
            dataType: this.dataType,
            raw_datapoints: this.raw_datapoints,
            total_buckets: this.total_buckets,
          };
          if (this.fleet) {
            params.deviceUUIDs = this.fleetDeviceUuids;
          } else {
            params.deviceUUID = this.deviceUuid;
          }
          let promise;
          if (this.fleet) {
            promise = this.fetchFleetMetricsCSV(params);
          } else {
            promise = this.fetchDeviceMetricsCSV(params);
          }
          promise
            .then((data) => {
              let id = this.fleet ? this.fleet.groupName : this.deviceId;
              let blob = new Blob([data], { type: 'text/csv' });
              const filename = `${id}_${this.dataType}_${this.$date.formatDate(Date.now(), 'YYYY_MM_DD')}`.replace(/\W/g, '_') + '.csv';
              const status = exportFile(filename, blob, 'text/csv');
              this.downloading = false;
              this.downloadSuccess = filename;
            })
            .catch((error) => {
              logError('ERROR: ', error);
              this.downloadError = "We couldn't download the data. Please try again later.";
              this.downloading = false;
            });
        }
      });
    },
    onClose() {
      this.dataType = 'raw';
      this.downloading = false;
      this.downloadError = null;
      this.downloadSuccess = null;
      if (this.$refs.metricsDataForm) {
        this.$refs.metricsDataForm.resetValidation();
      }
    },
  },
  mounted() {
    this.$events.$on('dialogs:metrics-downloader:open', (data) => {
      this.device = data.device;
      this.fleet = data.fleet;
      this.show = true;
    });
  },
};
</script>
