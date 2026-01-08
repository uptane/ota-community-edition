<template>
  <q-dialog v-model="show" @cancel="onCancel" @show="onShow" @hide="onHide">
    <q-card class="p-2 w-100vw mxw-80em mxh-80h">
      <device-mode v-if="mode === 'device'" :device="device" @done="onDeviceDone"></device-mode>
      <fleet-mode v-else :fleet-id="fleet.id" @done="onFleetDone"></fleet-mode>
    </q-card>
  </q-dialog>
</template>

<script>
import { mapGetters, mapActions } from 'vuex';
import DeviceMode from './FleetsAddDevices/DeviceMode.vue';
import FleetMode from './FleetsAddDevices/FleetMode.vue';

export default {
  name: 'FleetDeviceDialog',
  components: {
    DeviceMode,
    FleetMode,
  },
  props: {
    // show: {
    //   type: Boolean,
    //   default: false
    // },
  },
  data() {
    return {
      show: false,
      fleet: {},
      device: {},
      mode: 'device', // fleet=devices-to-fleet, device=fleets-to-device
    };
  },

  methods: {
    ...mapActions({
      fetchFleets: 'fleets/fetchFleets',
    }),
    onCancel() {
      this.loading = false;
      this.show = false;
    },

    onShow() {
      this.loading = false;
    },
    onHide() {
      this.fleets = [];
      this.messageData = {};
    },
    onFleetDone() {
      this.$events.$emit('update:fleet', this.fleet);
    },
    onDeviceDone(device) {
      this.$events.$emit('update:device', device);
    },
  },
  mounted() {
    this.$events.$on('dialogs:fleet-devices-manager:open', (data) => {
      if (data.mode === 'fleet') {
        this.mode = 'fleet';
        this.fleet = data.fleet;
      } else {
        this.mode = 'device';
        this.device = data.device;
      }
      this.show = true;
    });
  },
  computed: {},
  watch: {},
};
</script>
