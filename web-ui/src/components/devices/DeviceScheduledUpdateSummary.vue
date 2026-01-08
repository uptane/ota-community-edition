<template>
  <div>
    <div class="p-4" v-if="loading">
      <list-loader></list-loader>
    </div>
    <div v-else-if="deviceScheduledUpdateData" class="text-left w-100 items-center">
      <div class="text-h5 pt-2 pl-2 pb-1">
        <div class="row items-center">
          <div class="col-auto pr-1">
            <q-icon name="schedule" size="2rem" class="text-info"></q-icon>
          </div>
          <div class="col  opacity-50">{{ title }}</div>
          <div class="col-auto" v-if="showCloseBtn">
            <q-btn flat icon="close" dense v-close-popup class="mr-1" @click="$emit('close', {})">
              <tooltip>Hide</tooltip>
            </q-btn>
          </div>
        </div>
      </div>
      <q-separator class="mb-2"></q-separator>
      <p v-if="duration">
        <span class="opacity-50">The following update assignment is scheduled to start </span> {{ duration }}
        <tooltip>{{ $date.formatDate(scheduledAt, 'YYYY-MM-DD hh:mm A') }}</tooltip>
      </p>
      <div class="pl-2 pr-2">
        <div :key="'target_info' + '__' + ecuName" v-for="(target, ecuName) in deviceScheduledUpdateData.ecus" class="pb-2">
          <div class="text-1">{{ ecuName }}</div>
          <package-info show-hash show-labels max-width="40em" :pkg="target"></package-info>
        </div>
      </div>
    </div>
    <div v-else>
      <empty noAction noIcon :title="'Working on it'" message="We don't have any information about the scheduled update yet. Check back later."></empty>
    </div>
  </div>
</template>

<script>
import { mapGetters, mapActions, mapMutations } from 'vuex';
import moment from 'moment';
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
  name: 'DevicePendingUpdateSummary',
  props: {
    device: {
      type: Object,
      default: () => {
        return {};
      },
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
      updateHistory: [],
      statusData: null,
      scheduledAt: null,
    };
  },
  mounted() {
    this.fetchUpdateData();
  },
  methods: {
    ...mapActions({
      getMtuInformation: 'updates/getMtuInformation',
      getScheduledUpdateStatus: 'updates/getScheduledUpdateStatus',
    }),
    async fetchUpdateData() {
      const fetchStatus = async () => {
        this.loading = true;
        const scheduleData = await this.getScheduledUpdateStatus({ deviceUuid: this.device.uuid });
        const scheduleReports = scheduleData.values;
        const scheduleStatus = scheduleReports.find((update) => ['Scheduled', 'Assigned', 'PartiallyCompleted'].includes(update.status));
        this.scheduledAt = scheduleStatus ? scheduleStatus.scheduledAt : null;
        const mtuInfo = (await this.getMtuInformation({ mtuId: scheduleStatus.updateId })) || {};
        const mtuHardwareIds = Object.keys(mtuInfo);
        const deviceStatus = {
          targets: mtuHardwareIds.map((hardwareId) => {
            const targetTo = mtuInfo[hardwareId].to;
            return {
              hardwareIds: [hardwareId],
              image: {
                hash: targetTo.checksum.hash,
                filepath: targetTo.target,
              },
            };
          }),
        };
        this.statusData = {
          ...deviceStatus,
          ecus: _.keyBy(
            _.map(deviceStatus.targets, (target) => {
              this.createdAt = this.createdAt || target.createdAt;
              const filepath = target.image.filepath;
              return {
                filepath,
                ...this.packagesById[filepath],
              };
            }),
            (t) => t.hardwareIds[0],
          ),
        };
      };

      this.loading = true;
      try {
        await fetchStatus();
      } catch (error) {
        console.error(error);
      }
      this.loading = false;
    },
  },
  computed: {
    ...mapGetters({
      packagesById: 'packages/packagesById',
    }),
    deviceScheduledUpdateData() {
      return this.statusData;
    },
    duration() {
      return this.scheduledAt ? this.$timeago(this.scheduledAt) : null;
    },
  },
};
</script>
