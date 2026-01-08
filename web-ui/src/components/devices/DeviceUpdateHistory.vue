<template>
  <div class>
    <div v-if="loading">
      <div class="flex flex-center mnh-100vh">
        <div class="text-center">
          <div>
            <span class="opacity-90 pr-1">Loading update history...</span>
            <loader class flat color="secondary" />
          </div>
        </div>
      </div>
    </div>
    <div v-if="!loading">
      <div class="row p-0 m-0">
        <div
          class="transition-width"
          ref="parentPkgDiv"
          style="transition: all 0.2s ease"
          v-if="!selectedHistory || $q.screen.gt.sm"
          :class="{
            'col-12': !selectedHistory || loadingPackages,
            'col-5': !!selectedHistory && !loadingPackages,
          }"
        >
          <div class="q-card p-2 row">
            <div class="col-12">
              <div class="row items-center">
                <h5 class="col m-0 p-0 mb-1"><q-icon class="mr-1" size="1.8rem" name="fa fa-history"></q-icon><span class="opacity-40">Device: </span> &nbsp;{{ device.deviceName }}</h5>
                <div class="col-auto">
                  <view-type-selector v-model="viewType" :viewSize.sync="viewSize" :columns.sync="columns" :visibleColumns.sync="visibleColumns" :views="['table', 'relaxed']" :force-icon-only="!!selectedHistory"></view-type-selector>
                </div>
                <div class="col" v-if="false">
                  <div class="pl-1">
                    <filter-input v-model="filter"></filter-input>
                  </div>
                </div>
              </div>
            </div>
            <div class="w-100 row h-divide-top-dashed" v-if="!loading && (updateHistory && updateHistory.length)">
              <div v-if="viewType === 'table'" class="w-100">
                <q-table
                  :data="tableRows"
                  :columns="columns"
                  :visible-columns="visibleColumns"
                  :rows-per-page-options="[10, 20, 30, 40, 50, 100]"
                  row-key="eventTime"
                  selection="single"
                  :selected.sync="selectedArray"
                  @row-click="
                    (evt, row, index) => {
                      toggleselectedHistory(row);
                    }
                  "
                >
                  <template v-slot:body-selection> </template>
                  <template v-slot:body-cell-eventTime="props">
                    <q-td :props="props">
                      <template v-for="(ecu, ecuIndex) in props.row.ecus || []">
                        <package-icon size="1.5rem" :key="'target_icon' + ecuIndex + '__' + targetIndex" v-for="(target, targetIndex) in ecu.target || []" :package-info="target" class="mr-1"></package-icon>
                      </template>
                      <span>{{ $date.formatDate(props.row.eventTime, 'MM/DD/YYYY @ h:mm:ss A') }}</span>
                    </q-td>
                  </template>
                  <template v-slot:body-cell-status="props">
                    <q-td :props="props">
                      <div class="text-bold ellipsis">
                        <q-icon v-if="(props.row.result || {}).success" class="pr-1 animated bounceIn" size="1.4em" color="positive" name="verified" />
                        <q-icon v-else class="pr-1 animated bounceIn" size="1.4em" color="negative" name="report" />
                        {{ (props.row.result || {}).success ? 'Update completed' : 'Update failed' }}
                      </div>
                    </q-td>
                  </template>
                </q-table>
              </div>
              <div v-else class="row h-divide-top-dashed">
                <div
                  class="col-xs-12 col-sm-12 col-md-6 col-lg-6 col-xl-6 h-divide-bottom-dashed m-0 hoverable"
                  :class="{
                    selected: selectedHistory && selectedHistory.correlationId === update.correlationId,
                    'v-divide-right-dashed': index % 2 == 0 && $q.screen.gt.sm,
                  }"
                  v-for="(update, index) in updateHistory"
                  :key="index"
                  @click="toggleselectedHistory(update)"
                  style="
                position: relative"
                >
                  <q-item class="p-1 pt-2 pb-2">
                    <q-item-label>
                      <div class="row q-item-tile label items-center">
                        <div class="col-auto">
                          <template v-for="(ecu, ecuIndex) in update.ecus || []">
                            <package-icon size="1.8rem" :key="'target_icon' + ecuIndex + '__' + targetIndex" v-for="(target, targetIndex) in ecu.target || []" :packageInfo="target" class="mr-1"></package-icon>
                          </template>
                        </div>
                        <div class="col ">
                          <div class="ellipsis">{{ $date.formatDate(update.eventTime, 'MM/DD/YYYY @ h:mm:ss A') }}</div>
                        </div>
                      </div>
                      <div class="row q-item-tile ">
                        <div class="col-auto pr-1 sublabel">Result:</div>
                        <div class="col ">
                          <span class="sublabel">{{ update.result.success ? 'Update completed' : 'Installation failed' }}</span>
                          <span class="ml-1">
                            <q-icon v-if="update.result.success" class="pr-1 animated bounceIn" size="1.4em" color="positive" name="verified" />
                            <q-icon v-else class="pr-1 animated bounceIn" size="1.4em" color="negative" name="report" />
                          </span>
                        </div>
                      </div>
                      <div class="row q-item-tile sublabel">
                        <div class="col-auto pr-1">Additional info:</div>
                        <div class="col ellipsis">{{ (update.result || {}).description }}</div>
                      </div>
                      <div class="row q-item-tile sublabel">
                        <div class="col-auto pr-1">Number of affected Components:</div>
                        <div class="col ellipsis">{{ (update.ecus || []).length }}</div>
                      </div>

                      <div></div>
                    </q-item-label>
                  </q-item>
                </div>
              </div>
            </div>
            <div v-else class="text-center w-100 items-center">
              <empty noAction noIcon></empty>
            </div>
          </div>
        </div>

        <transition appear enter-active-class="animated slideInRight" leave-active-class="animated slideOutRight" class="mnh-100vh">
          <div
            class="col-7 pt-0 mb-1 v-divide-left-dotted"
            v-if="!loadingPackages && !!showPackageVersions"
            :class="{
              'col-12': $q.screen.lt.md,
              'pl-1': $q.screen.gt.sm,
            }"
          >
            <div
              class="q-card h-100
          animated"
              style="overflow-y: auto;"
              :class="{
                pulse: giveAttensionToVersionsView,
              }"
            >
              <div class="row w-100">
                <div
                  class="col-lg-6 col-sm-12"
                  :class="{
                    'col-lg-6': showUpdateEvents,
                    'col-lg-12': !showUpdateEvents,
                    'v-divide-right': $q.screen.gt.md && showUpdateEvents,
                  }"
                >
                  <device-update-summary
                    :device="device"
                    :history-data="selectedHistory"
                    :correlation-id="selectedHistory.correlationId"
                    @close="
                      selectedHistory = null;
                      showUpdateEvents = false;
                    "
                    :show-close-btn="$q.screen.lt.lg || !showUpdateEvents"
                  ></device-update-summary>
                  <div class="text-left p-2">
                    <q-btn flat dense v-if="!showUpdateEvents" @click="showUpdateEvents = true" color="primary"> Show Update Events <q-icon class="ml-1" name="more"></q-icon> </q-btn>
                  </div>
                </div>
                <div class="col-lg-6 col-sm-12" v-if="showUpdateEvents">
                  <device-update-events
                    :device="device"
                    :history-data="selectedHistory"
                    :correlation-id="selectedHistory.correlationId"
                    @close="
                      selectedHistory = null;
                      showUpdateEvents = false;
                    "
                  ></device-update-events>
                </div>
              </div>
            </div>
          </div>
        </transition>
      </div>
    </div>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import Loader from '../loaders/Loader';
import { dom, extend } from 'quasar';
import Tooltip from '../common/Tooltip.vue';
import PackageIcon from '../packages/PackageIcon.vue';
import DeviceUpdateEvents from './DeviceUpdateEvents.vue';
import Empty from '../common/Empty.vue';
import ViewTypeSelector from '../common/ViewTypeSelector.vue';
import FilterInput from '../common/FilterInput.vue';
import DeviceUpdateSummary from './DeviceUpdateSummary.vue';
const { height, width } = dom;

export default {
  name: 'DevicePackages',
  components: {
    Loader,
    Tooltip,
    PackageIcon,
    DeviceUpdateEvents,
    Empty,
    ViewTypeSelector,
    FilterInput,
    DeviceUpdateSummary,
  },
  props: {
    device: {
      type: Object,
      default: () => {
        return {};
      },
    },
  },
  data() {
    return {
      loading: false,
      loadingPackages: false,
      showPackageVersions: false,
      parsedPackageVersions: {},
      giveAttensionToVersionsView: false,
      updateHistory: null,
      selectedHistory: null,
      viewType: 'table',
      viewSize: 20,
      filter: '',
      visibleColumns: ['eventDate', 'result', 'info'],
      columns: [
        {
          required: true,
          label: 'Update Date',
          align: 'left',
          field: (row) => row.eventTime,
          format: (val) => this.$date.formatDate(val, 'MM/DD/YYYY @ h:mm:ss A'),
          sortable: true,
          classes: 'ellipsis text-bold',
          style: 'max-width: 100px',
          headerClasses: 'text-bold',
          name: 'eventTime',
          id: 'eventTime',
        },

        {
          required: true,
          label: 'Result',
          align: 'left',
          field: (row) => (row.result || {}).success,
          format: (val) => `${val}`,
          sortable: true,
          classes: 'ellipsis text-bold',
          style: 'max-width: 100px',
          headerClasses: 'text-bold',
          name: 'status',
          id: 'status',
        },
        {
          required: false,
          label: 'Additional Information',
          align: 'left',
          field: (row) => (row.result || {}).description || 'None',
          format: (val) => `${val}`,
          sortable: true,
          classes: 'ellipsis text-bold',
          style: 'max-width: 100px',
          headerClasses: 'text-bold',
          name: 'info',
          id: 'info',
        },
        {
          required: false,
          label: 'No. of Components',
          align: 'left',
          field: (row) => (row.ecus || []).length,
          format: (val) => `${val}`,
          sortable: true,
          classes: 'ellipsis text-bold',
          style: 'max-width: 100px',
          headerClasses: 'text-bold',
          name: 'numOfEcus',
          id: 'numOfEcus',
        },
      ],
      showUpdateEvents: false,
    };
  },
  created() {},
  mounted() {
    this.setup();
  },
  beforeDestroy() {},
  computed: {
    ...mapGetters({
      packagesById: 'packages/packagesById',
    }),

    selectedArray: {
      get() {
        if (this.selectedHistory) {
          return [this.selectedHistory];
        }
        return [];
      },
      set(v) {
        this.selectedHistory = (v || [])[0];
      },
    },
    tableRows() {
      const rows = this.updateHistory.filter((f) => JSON.stringify(f).match(new RegExp(this.filter, 'i')));
      return rows;
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
      return this.device.uuid;
    },
  },
  beforeDestroy() {},

  created() {},
  methods: {
    ...mapActions({
      getUpdateInstallationReports: 'devices/getUpdateInstallationReports',
    }),
    height,
    setup() {
      this.pageTitle = 'Device Update History';
      this.refresh();
      this.$events.$on('devices:refresh', () => {
        this.refresh();
      });
    },
    refresh() {
      this.loading = true;
      const fetchHistory = () => {
        this.getUpdateInstallationReports(this.device.uuid)
          .then((history) => {
            this.updateHistory = history.map((m) => {
              return {
                ...m,
                ecus: m.ecus.map((ecu) => {
                  ecu.target = (ecu.target || []).map((filepath) => {
                    return {
                      filepath,
                      ...this.packagesById[filepath],
                    };
                  });
                  return ecu;
                }),
              };
            });
          })
          .catch((err) => {})
          .finally((r) => {
            this.loading = false;
          });
      };

      fetchHistory();
    },
    toggleselectedHistory(update) {
      if (this.selectedHistory && this.selectedHistory.correlationId === update.correlationId) {
        this.selectedHistory = null;
      } else {
        this.selectedHistory = { ...update };
      }
    },

    deviceDeleted() {
      this.$router.replace({ name: 'devices' }).catch((e) => {});
    },
  },
  watch: {
    selectedHistory(p) {
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
