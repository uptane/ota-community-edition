<template>
  <div>
    <div v-if="!inline" class="last-seen-badge animated slideInUp absolute-bottom-left" :class="badgeData.colorClass">
      <template v-if="badgeData.icon">
        <q-icon :name="badgeData.icon" color="white" size="2em" />
      </template>
      <template v-else>{{ badgeData.text }}</template>
    </div>
    <q-badge class="last-seen-badge-inline" v-else :color="badgeData.color" :text-color="badgeData.textColor">
      <template v-if="badgeData.icon">
        <q-icon :name="badgeData.icon" color="white" size="1.2em" />
      </template>
      <template v-else>{{ badgeData.text }}</template>
    </q-badge>
    <tooltip>{{ badgeData.summary }}</tooltip>
  </div>
</template>

<script>
import { mapActions } from 'vuex';
import Tooltip from '../common/Tooltip.vue';
export default {
  name: 'OnlineBadge',
  components: { Tooltip },
  props: {
    device: {
      type: Object,
      default: () => {
        return {};
      },
    },
    inline: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      badgeData: {},
      interval: 0,
    };
  },
  mounted() {
    this.updateView();
  },
  computed: {
    lastSeen() {
      return (this.device || {}).lastSeen;
    },
    hibernate() {
      let device = this.device || {};
      device = device.device || device;
      return device.hibernated;
    },
  },
  methods: {
    ...mapActions({
      getDeviceStatusData: 'devices/getDeviceStatusData',
    }),
    updateView() {
      this.getDeviceStatusData(this.device || {}).then((b) => {
        this.badgeData = b;
        this.$events.$emit('devices:updated', {});
        clearInterval(this.interval);
        this.interval = setTimeout(() => {
          this.updateView();
        }, 60 * 1000);
      });
    },
  },
  watch: {
    lastSeen() {
      this.updateView();
    },
    hibernate() {
      this.updateView();
    },
  },
  beforeDestroy() {
    clearInterval(this.interval);
  },
};
</script>
