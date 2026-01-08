<template>
  <q-dialog v-model="show" @hide="teardown" @show="setup" :content-css="{ minWidth: '20em', maxWidth: '40rem', minHeight: '10vh' }" ref="deviceInfoDlg" :maximized="$q.screen.lt.md" :transition-show="$q.screen.lt.md ? 'slide-up' : 'scale'" transition-hide="$q.screen.lt.md?'slide-down':'scale'">
    <q-card class="mxw-80vw w-100 mnw-30em device-quick-view" :class="{ 'mxw-100vw': $q.screen.lt.md }" style="overflow-y: auto">
      <device-quick-view :device-uuid="device.uuid" @hide="requested = false"></device-quick-view>
    </q-card>
  </q-dialog>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import DeviceDetail from './DeviceDetail';
import Loader from '../loaders/Loader';
import DeviceStatus from './DeviceStatus';
import DeviceQuickView from './DeviceQuickView.vue';

export default {
  name: 'DeviceQuickViewDialog',
  components: {
    Loader,
    DeviceStatus,
    DeviceDetail,
    DeviceQuickView,
  },
  props: {
    // device: {
    //   type: Object,
    //   default: ()=>{
    //     return {}
    //   }
    // }
  },
  data() {
    return {
      requested: false,
      loadingUpdatedData: true,
      deviceUuid: '',
    };
  },
  methods: {
    ...mapActions({
      fetchDevice: 'devices/fetchDevice',
      deleteDevice: 'devices/deleteDevice',
      getNextDevice: 'devices/getNextDevice',
      getPreviousDevice: 'devices/getPreviousDevice',
      updateDeviceData: 'devices/updateDeviceData',
    }),
    nextDevice() {
      const device = this.getNextDevice(this.device.uuid).then((device) => {
        this.deviceUuid = device.uuid;
        this.setup();
      });
    },
    previousDevice() {
      this.getPreviousDevice(this.device.uuid).then((device) => {
        this.deviceUuid = device.uuid;
        this.setup();
      });
    },
    showFullDeviceDatail() {
      this.$router.push({
        name: 'device-detail',
        params: { deviceId: this.device.uuid },
      });
    },
    getDeviceNetworkInfo() {
      this.$router.push({
        name: 'device-detail',
        params: { deviceId: this.device.uuid },
      });
    },
    bindEventBusEvents() {
      this.$events.$on('component:show-device-detail:open', this.showModal);
    },
    showModal(deviceData) {
      this.deviceUuid = deviceData.uuid;
      this.requested = true;
      // this.$store.commit("devices/setSelectedDevice", this.device);
      // this.setup();
    },
    setup() {
      this.loadingUpdatedData = true;
      this.fetchDevice(this.device.uuid)
        .then((data) => {
          this.updateDeviceData(data);
          this.loadingUpdatedData = false;
        })
        .catch((err) => {
          logError('Device fetch err:', err);
          this.loadingUpdatedData = false;
        });
    },
    teardown() {},
    showDeviceDatail() {
      this.$router.push({
        name: 'device-detail',
        params: { deviceId: this.device.uuid },
      });
    },
    showEditDialog() {
      this.$events.$emit(`dialogs:rename-device:open`, {
        show: true,
        device: this.device || {},
      });
    },
    showFleetManager() {
      this.$router.push({ name: 'fleet-manager', query: { deviceId: this.device.uuid } });
    },
    createUpdate() {
      const selectedDevice = this.device;
      this.$events.$emit(`dialogs:create-device-update:open`, {
        show: true,
        isFleetUpdate: false,
        selectedDevice,
        fromDeviceDetail: true,
        update: { devices: [selectedDevice] },
      });
    },
    promptForDelete() {
      this.$events.$emit('dialogs:confirm:open', {
        title: `Delete ${this.device.deviceName}?`,
        message: `This can't be undone.`,
        color: 'default',
        icon: 'delete',
        yesFlat: true,
        yesClass: 'delete',
        yesLabel: 'Yes, please!',
        yesColor: 'negative',
        yesAction: () => {
          this.deviceDeleteInProgress = this.device;
          const name = this.device.deviceName;
          this.deleteDevice(this.device.uuid)
            .then((deleted) => {
              this.show = false;
              this.$q.notify({
                color: 'positive',
                message: `${name} deleted!`,
              });
            })
            .catch((err) => {
              this.deviceDeleteInProgress = null;
              this.$q.notify({
                message: `Unable to delete ${name}!`,
                color: 'negative',
              });
            });
        },
        noFlat: true,
        noLabel: 'No',
        noAction: () => {},
      });
    },
  },
  mounted() {
    this.bindEventBusEvents();
  },
  computed: {
    ...mapGetters({
      devices: 'devices/devices',
    }),
    show: {
      get() {
        return this.requested && this.$q.screen.lt.md;
      },
      set(val) {
        this.requested = val;
      },
    },
    device() {
      return this.devices.find((d) => d.uuid === this.deviceUuid) || {};
    },
    deviceDeleteInProgress: {
      get() {
        return this.$store.getters['ui/deviceDeleteInProgress'];
      },
      set(val) {
        this.$store.commit('ui/setDeviceDeleteInProgress', val);
      },
    },
  },
};
</script>
