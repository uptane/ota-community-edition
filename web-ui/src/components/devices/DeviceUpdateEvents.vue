<template>
  <div>
    <div class="p-4" v-if="loading">
      <list-loader></list-loader>
    </div>
    <div v-else-if="updateEvents && updateEvents.length">
      <div class="text-h5 pt-2 pl-2 pb-1 opacity-50">
        <div class="row">
          <div class="col">{{ title }}</div>
          <div class="col-auto">
            <q-btn flat icon="close" dense v-close-popup @click="$emit('close', {})">
              <tooltip>Hide</tooltip>
            </q-btn>
          </div>
        </div>
      </div>
      <q-separator />
      <q-timeline layout="dense" side="right" color="secondary" class="ml-3 mxw-90">
        <q-timeline-entry v-for="event in updateEvents" :title="infoFromEvent(event).summary" :subtitle="$date.formatDate(event.deviceTime, 'MM/DD/YYYY h:mm:ss:SSS A')" side="left" :color="infoFromEvent(event).color" :icon="infoFromEvent(event).icon" :key="event.eventId">
          <div v-if="affectedEcu(event)">
            <div class="opacity-50 text-1 pb-1">Target Information</div>
            <div :key="'target_info' + '__' + targetIndex" v-for="(target, targetIndex) in affectedEcu(event).target || []">
              <package-info showHash :pkg="target"></package-info>
            </div>
          </div>
        </q-timeline-entry>
      </q-timeline>
    </div>
    <div v-else class="text-center w-100 items-center">
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
import PackageIcon from '../packages/PackageIcon.vue';
import PackageInfo from '../packages/PackageInfo.vue';
export default {
  components: {
    ListLoader,
    Empty,
    Tooltip,
    PackageIcon,
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
      default: 'Update Events Timeline',
    },
  },
  data() {
    return {
      loading: true,
      allUpdateEvents: null,
      updateHistory: [],
      icons: {
        EcuInstallationCompletedSuccess: {
          icon: 'download_done',
          summary: 'Component Installation Completed Successfully',
          detail: 'Component Installation Completed Successfully',
          color: 'positive',
        },
        EcuInstallationCompletedFailed: {
          icon: 'error',
          summary: 'Component Installation Failed',
          detail: 'Component Installation Failed',
          color: 'negative',
        },
        EcuInstallationApplied: {
          icon: 'download_done',
          summary: 'Component Installation Applied',
          detail: 'Component Installation Applied',
          color: 'info',
        },
        EcuInstallationStarted: {
          icon: 'downloading',
          summary: 'Component Installation Started',
          detail: 'Component Installation Started',
          color: 'grey-7',
        },

        EcuDownloadStarted: {
          icon: 'downloading',
          summary: 'Component Target Download Started',
          detail: 'Component Target Download Started',
          color: 'grey-7',
        },
        EcuDownloadCompletedSuccess: {
          icon: 'download_done',
          summary: 'Component Target Download Completed Successfully',
          detail: 'Component Target Download Completed Successfully',
          color: 'positive',
        },
        EcuDownloadCompletedFailed: {
          icon: 'file_download_off',
          summary: 'Component Target Download failed',
          detail: 'Component Target Download failed',
          color: 'negative',
        },
      },
    };
  },
  mounted() {
    this.loading = true;
    this.getUpdateEvents(this.device.uuid)
      .then((events) => {
        this.allUpdateEvents = _.sortBy(events, function(item) {
          return [item.deviceTime, item.eventType.id.substr(item.eventType.id.length - 5)];
        }).reverse();
      })
      .catch((err) => {})
      .finally(() => {
        this.loading = false;
        if (!this.historyData) {
          this.fetchHistoryData();
        }
      });
  },
  methods: {
    ...mapActions({
      getUpdateEvents: 'devices/getUpdateEvents',
      getUpdateInstallationReports: 'devices/getUpdateInstallationReports',
    }),
    makeReadable(eventName) {
      return titleCase(eventName);
    },
    infoFromEvent(event) {
      const completed = event.eventType.id.indexOf('Completed') !== -1;
      let prefix = '';
      if (completed) {
        prefix = event.payload.success ? 'Success' : 'Failed';
      }
      return this.icons[event.eventType.id + prefix];
    },
    affectedEcu(event) {
      return ((this.deviceHistoryData || {}).ecus || []).find((ecu) => ecu.ecuId === event.payload.ecu);
    },
    fetchHistoryData() {
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
  },
  computed: {
    ...mapGetters({
      packagesById: 'packages/packagesById',
    }),
    updateEvents() {
      return (this.allUpdateEvents || []).filter((f) => f.payload.correlationId === this.correlationId);
    },
    deviceHistoryData() {
      if (this.historyData) {
        return this.historyData;
      }
      return this.updateHistory.find((f) => f.correlationId == this.correlationId);
    },
  },
};
</script>
