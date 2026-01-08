<template>
  <div>
    <div class="p-4" v-if="loading">
      <list-loader></list-loader>
    </div>
    <div v-else-if="deviceHistoryData" class="text-left w-100 items-center">
      <div class="text-h5 pt-2 pl-2 pb-1 opacity-50">
        <div class="row">
          <div class="col">{{ title }}</div>
          <div class="col-auto" v-if="showCloseBtn">
            <q-btn flat icon="close" dense v-close-popup class="mr-1" @click="$emit('close', {})">
              <tooltip>Hide</tooltip>
            </q-btn>
          </div>
        </div>
      </div>
      <q-separator class="mb-2"></q-separator>
      <div class="pl-2 pr-2">
        <p
          class="text-1"
          :class="{
            'text-positive': (deviceHistoryData.result || {}).success,
            'text-negative': !(deviceHistoryData.result || {}).success,
          }"
        >
          <q-icon :name="(deviceHistoryData.result || {}).success ? 'check_circle' : 'error'" size="2em" class=""></q-icon> &nbsp;{{ (deviceHistoryData.rawReport || '').replace('ECU', 'component') }}
        </p>
        <div class="text-1 opacity-60 pb-1 mb-1 h-divide-bottom-dashed">Report By Components</div>
        <div :key="groupName" v-for="(group, groupName) in deviceHistoryData.ecuGroup" class="mb-2">
          <div class=" text-1 ellipsis">
            <span>{{ groupName }}</span>
          </div>

          <div>
            <div :key="ecuIndex" v-for="(ecu, ecuIndex) in group">
              <div>
                <span class="opacity-40 ellipsis items-center">Status: &nbsp;</span>
                <span
                  :class="{
                    'text-positive': ecu.result.success,
                    'text-negative': !ecu.result.success,
                  }"
                >
                  <q-icon size="1.1rem" :name="ecu.result.success ? 'verified' : 'error'"></q-icon>
                  {{ ecu.result.description || (ecu.result.success ? 'Successful' : 'Failed') }}
                </span>
              </div>
              <div :key="'target_info' + '__' + targetIndex" v-for="(target, targetIndex) in ecu.target">
                <package-info :pkg="target" show-hash show-labels max-width="30em"></package-info>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div v-else>
      <empty noAction noIcon :title="'Working on it'" :message="'No update events reported yet. Check back later.'"></empty>
    </div>
  </div>
</template>

<script>
import { titleCase } from 'change-case';
import { mapGetters, mapActions, mapMutations } from 'vuex';
import ListLoader from '../loaders/ListLoader.vue';
import Empty from '../common/Empty.vue';
import Tooltip from '../common/Tooltip.vue';
import PackageInfo from '../packages/PackageInfo.vue';
export default {
  components: {
    ListLoader,
    Empty,
    Tooltip,
    PackageInfo,
  },
  name: 'DeviceUpdateEvents',
  props: {
    device: {
      type: Object,
      default: () => {
        return {};
      },
    },
    historyData: {
      type: Object,
      default: () => {
        return null;
      },
    },
    correlationId: {
      type: String,
      default: '',
    },
    title: {
      type: String,
      default: 'Update Summary',
    },
    showCloseBtn: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      loading: false,
      fetchedHistoryData: {},
      updateHistory: [],
      statusData: {},
    };
  },
  mounted() {
    if (!this.historyData) {
      this.fetchNewHistoryData();
    }
  },
  methods: {
    ...mapActions({
      fetchPackages: 'packages/fetchPackages',
      getUpdateInstallationReports: 'devices/getUpdateInstallationReports',
      getUpdateStatus: 'devices/getUpdateStatus',
    }),
    makeReadable(eventName) {
      return titleCase(eventName);
    },
    fetchNewHistoryData() {
      this.loading = true;
      const findPackageByPath = (filepath) => {
        return this.allPackagesByPath[filepath];
      };
      const fetchHistory = () => {
        this.getUpdateInstallationReports(this.device.uuid)
          .then((history) => {
            this.fetchedHistoryData = history.find((h) => h.correlationId === this.correlationId);
            this.fetchedHistoryData = {
              ...this.fetchedHistoryData,
              ecus: this.fetchedHistoryData.ecus.map((ecu) => {
                ecu.target = (ecu.target || []).map((filepath) => {
                  return {
                    filepath,
                    ...findPackageByPath(filepath),
                  };
                });
                return ecu;
              }),
            };
          })
          .catch((err) => {
            console.error(err);
          })
          .finally((r) => {
            this.loading = false;
          });
      };

      fetchHistory();
    },
  },
  computed: {
    ...mapGetters({
      allPackagesByPath: 'packages/packagesById',
    }),
    deviceHistoryData() {
      let history = this.historyData;
      if (!this.historyData) {
        history = this.fetchedHistoryData;
      }
      if (!history) return null;
      history.ecuGroup = _.groupBy(history.ecus, (g) => {
        const firstTarget = (g.target || [])[0] || {};
        return (firstTarget.hardwareIds || [])[0] || firstTarget.filepath;
      });
      return history;
    },
  },
};
</script>
