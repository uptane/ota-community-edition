<template>
  <div>
    <div class="p-4" v-if="loading">
      <list-loader></list-loader>
    </div>
    <div v-else-if="devicePendingUpdateData" class="text-left w-100 items-center">
      <div class="text-h5 pt-2 pl-2 pb-1">
        <div class="row items-center">
          <div class="col-auto pr-1">
            <q-spinner-hourglass color="info" size="2rem" />
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
      <p v-if="duration"><span class="opacity-50">The following update assignment has been pending for </span>{{ duration }}</p>
      <div class="pl-2 pr-2">
        <div :key="'target_info' + '__' + ecuName" v-for="(target, ecuName) in devicePendingUpdateData.ecus" class="pb-2">
          <div class="text-1">{{ ecuName }}</div>
          <package-info show-hash show-labels max-width="40em" :pkg="target"></package-info>
        </div>
      </div>
    </div>
    <div v-else>
      <empty noAction noIcon :title="'Working on it'" :message="'No update events reported yet. Check back later.'"></empty>
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
      default: 'Pending Update Summary',
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
      statusData: {},
      createdAt: null,
    };
  },
  mounted() {
    this.fetchPendingUpdateData();
  },
  methods: {
    ...mapActions({
      getUpdateStatus: 'devices/getUpdateStatus',
    }),
    fetchPendingUpdateData() {
      this.loading = true;
      const fetchStatus = () => {
        this.getUpdateStatus([this.device.uuid])
          .then((status) => {
            const deviceStatus = status[this.device.uuid];
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
          })
          .catch((err) => {})
          .finally((r) => {
            this.loading = false;
          });
      };
      fetchStatus();
    },
  },
  computed: {
    ...mapGetters({
      packagesById: 'packages/packagesById',
    }),
    devicePendingUpdateData() {
      return this.statusData;
    },
    duration() {
      return this.createdAt ? moment(this.createdAt).fromNow(true) : null;
    },
  },
};
</script>
