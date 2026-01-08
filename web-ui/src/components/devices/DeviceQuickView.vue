<template>
  <div class="device-quick-view  h-100h pl-1 pt-5  bg-theme-bg">
    <q-card-section class="pt-2 pb-2 p-0">
      <div
        class="p-0 row pt-1 mt-0  bg-theme-bg "
        style="position: fixed;  z-index: 1000;"
        :style="{
          left: divLeft + 'px',
          top: '8em',
          marginLeft: '0',
          width: divWidth + 8 + 'px',
        }"
      >
        <h5 class="m-0 pl-0 pr-2 col-12 pb-1">
          <div class="row">
            <div class="col-auto pr-2">
              <q-btn flat dense class="pr" color="secondary" icon="keyboard_arrow_left" @click="previousDevice">
                <tooltip>View previous device</tooltip>
              </q-btn>
              <q-btn flat dense class="pl" color="secondary" icon="keyboard_arrow_right" @click="nextDevice">
                <tooltip>View next device</tooltip>
              </q-btn>
            </div>
            <div class="gt-xs col-7">
              <div class="row">
                <div class="col-9 ellipsis">
                  {{ device.deviceName }}
                </div>
                <div class="col-auto">
                  <feature-teaser feature="update-device-info">
                    <q-btn flat dense @click="showEditDialog" color="secondary" icon="edit">
                      <tooltip>Rename this device</tooltip>
                    </q-btn>
                  </feature-teaser>
                </div>
              </div>
            </div>
            <q-space />
            <div class="col-auto">
              <q-btn class="absolute-top-right mt-1 mr-1" flat dense @click="hideDetail" icon="close">
                <tooltip>Hide detail</tooltip>
              </q-btn>
            </div>
            <div class="lt-sm col-12 text-center pt-2">
              {{ device.deviceName }}
            </div>
          </div>
        </h5>
        <div class="col-12">
          <q-linear-progress v-if="loadingUpdatedData" size="1px" color="secondary" indeterminate />
          <q-separator v-else />
        </div>
      </div>
      <div
        class="row pl-0 pr-0 mnh-80vh"
        style="overflow-y:auto; "
        :style="{
          width: divWidth + 'px',
        }"
      >
        <div class="col-12 col-xl-8 mb-2">
          <device-information :device.sync="device"></device-information>
        </div>

        <div class="col-12 col-xl-4">
          <div class="row">
            <div class="col-12 col-auto">
              <device-actions @deleted="hideDetail" :device="device" :actions="deviceActions"></device-actions>
            </div>
          </div>
        </div>
      </div>
    </q-card-section>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import Loader from '../loaders/Loader';
import DeviceStatus from './DeviceStatus';
import DevicePackageInformation from './DevicePackageInformation.vue';
import DeviceInformation from './DeviceInformation.vue';
import Tooltip from '../common/Tooltip.vue';
import DeviceActions from './DeviceActions.vue';
import { extend } from 'quasar';

export default {
  name: 'DeviceQuickView',
  components: {
    Loader,
    DeviceStatus,
    DevicePackageInformation,
    DeviceInformation,
    Tooltip,
    DeviceActions,
  },
  props: {
    parentHeight: {
      type: String,
      default: '',
    },
  },
  data() {
    return {
      loadingUpdatedData: true,
      loadingPackageData: false,
      _device: null,
    };
  },
  methods: {
    ...mapActions({
      fetchDevice: 'devices/fetchDevice',
      deleteDevice: 'devices/deleteDevice',
      getNextDevice: 'devices/getNextDevice',
      getPreviousDevice: 'devices/getPreviousDevice',
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
    loadData() {
      this.loadingUpdatedData = true;
      if (!this.packages || ((!this.packages.all || !this.packages.all.length) && (!this.packages.custom || !this.packages.custom.length))) {
        this.loadingPackageData = true;
      }
      this.fetchDevice(this.deviceUuid)
        .then((data) => {
          this.$data._device = data;
          this.loadingUpdatedData = false;
        })
        .catch((err) => {
          logError('Device fetch err:', err);
          this.loadingUpdatedData = false;
        });
    },
    setup() {
      this.$data._device = null;
      this.loadData();
      this.$events.$on('devices:refresh', () => {
        this.loadData();
      });
      this.$events.$on('update:device', (device) => {
        if (device && device.uuid && device.uuid === this.$data._device.uuid) {
          this.$data._device = device;
        }
      });
    },
    showEditDialog() {
      this.$events.$emit(`dialogs:rename-device:open`, {
        show: true,
        device: this.device || {},
      });
    },
    hideDetail() {
      this.$emit('hide', {});
      // Ensure that the route is updated after the view is hidden
      this.$nextTick(() => {
        this.$router.push({
          name: 'devices',
        });
      });
    },
  },
  mounted() {
    this.setup();
  },
  beforeDestroy() {},
  computed: {
    ...mapGetters({
      devices: 'devices/devices',
    }),
    deviceUuid: {
      get() {
        return this.$route.params.deviceId;
      },
      set(val) {
        this.$router.push({
          name: 'device-quick-view',
          params: { deviceId: val },
        });
      },
    },

    deviceActions() {
      let actions = ['update', 'fleet', 'rename', 'view', 'delete'];
      if (!this.loadingUpdatedData) {
        actions = ['update', 'fleet', 'rename', 'view', 'remoteAccess', 'hibernate', 'delete'];
      }
      return actions;
    },
    device: {
      get() {
        const fromList = (this.$store.getters['devices/devicesByUuid'] || {})[this.deviceUuid] || {};
        return extend(true, {}, this.$data._device, fromList);
      },
      set(val) {
        this.$data._device = val;
      },
    },
    deviceDeleteInProgress: {
      get() {
        return this.$store.getters['ui/deviceDeleteInProgress'];
      },
      set(val) {
        this.$store.commit('ui/setDeviceDeleteInProgress', val);
      },
    },
    divLeft() {
      return this.$store.getters['ui/deviceQuickViewLeft'];
    },
    divWidth() {
      return this.$store.getters['ui/deviceQuickViewWidth'];
    },
  },
  watch: {
    deviceUuid(n) {
      if (n) {
        this.$emit('device-uuid-change', { uuid: this.deviceUuid });
        this.setup();
      }
    },
  },
};
</script>
