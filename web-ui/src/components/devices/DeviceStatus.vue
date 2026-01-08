<template>
  <div>
    <div v-if="device.deviceStatus === 'Outdated'">
      <update-status-indicator :size="size" minimized :device="device"> </update-status-indicator>
    </div>
    <div
      v-else
      :class="{
        'text-negative': device.deviceStatus === 'Failed',
        'text-negative': device.deviceStatus === 'Error',
        'text-default': device.deviceStatus === 'UpToDate',
        'text-positive': device.deviceStatus === 'Outdated',
        'opacity-40': device.deviceStatus === 'NotSeen',
      }"
    >
      <q-spinner-hourglass :size="size" v-if="device.deviceStatus === 'Outdated'" color="positive" />
      {{ deviceState.summary }}
      <q-icon v-if="device.deviceStatus === 'UpToDate'" class="text-positive" name="verified" />
      <q-icon v-if="device.deviceStatus === 'Error' || device.deviceStatus === 'Failed'" class="text-positive" name="error" />
    </div>
    <tooltip>
      <div class="mxw-20em" v-html="deviceState.message"></div>
    </tooltip>
  </div>
</template>

<script>
import { DEVICE_UPDATE_EVENT_MESSAGE_TYPES, DEVICE_UPDATE_STATES } from '../../constants';
import Tooltip from '../common/Tooltip';
import UpdateStatusIndicator from '../updates/UpdateStatusIndicator';
export default {
  name: 'DeviceStatus',
  components: {
    Tooltip,
    UpdateStatusIndicator,
  },
  props: {
    size: {
      type: String,
      default: '0.8rem',
    },
    device: {
      type: Object,
      default: () => {
        return {};
      },
    },
  },
  data() {
    return {
      deviceStates: {
        NotSeen: {
          key: 'NotSeen',
          summary: 'Not Activated',
          message: 'Device was provisioned but not yet seen by the server',
        },
        Failed: DEVICE_UPDATE_STATES.UpdateError,
        Error: DEVICE_UPDATE_STATES.UpdateError,
        UpToDate: DEVICE_UPDATE_STATES[DEVICE_UPDATE_EVENT_MESSAGE_TYPES.EcuInstallationCompleted],
      },
    };
  },
  mounted() {},
  beforeDestroy() {},
  methods: {},
  computed: {
    deviceState() {
      return this.deviceStates[this.device.deviceStatus] || {};
    },
  },
};
</script>
