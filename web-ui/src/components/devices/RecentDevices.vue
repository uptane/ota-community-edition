<template>
  <div class="devices-wrapper">
    <!-- BEGIN RENDER FOR DASHBOARD DEVICE LIST -->
    <q-card class="device-card-item">
      <q-card-section class="row text-center justify-between items-center">
        <h5 class="col-12 mt-0 mb-0">Recent Devices</h5>
        <feature-teaser class="col-12" feature="provision-device">
          <q-btn @click="showDeviceCreateDialog" flat class="" color="secondary">&nbsp;Provision device</q-btn>
        </feature-teaser>
      </q-card-section>
      <q-linear-progress v-if="loading" indeterminate color="secondary" size="2px" class="" />
      <q-separator v-else class="p-0 m-0" />
      <q-list class="pl-0 pr-0" dense>
        <template v-if="devices && devices.length">
          <template v-for="(device, index) of sortedDevices">
            <template v-if="index < limit">
              <q-separator :key="index + 't'" />
              <drag
                :transfer-data="device"
                :style="{
                  width: 'auto',
                }"
                :key="index"
              >
                <device-item class="shadow-0 w-100" :device="device" @item-click="showDeviceDatail(device, true)"></device-item>
              </drag>
            </template>
          </template>
          <more-indicator v-if="!loading" href="#/devices" text="View all devices" :data-length="devices.length"></more-indicator>
        </template>
        <template v-if="!loading && (!devices || devices.length < 1)">
          <q-separator />
          <q-item>
            <q-item-label>
              <div class="q-item-tile label text-center p-2 opacity-30"><q-icon class="mr-1" name="info" size="1.2em"></q-icon> Nothing here yet</div>
            </q-item-label>
          </q-item>
        </template>
        <template v-if="loading">
          <template v-for="index in 7">
            <q-separator :key="index + '_recent_dvc_'" class="p-0 m-0" />
            <q-item :key="index + '_recent_dvc'" class="mnh-5em q-my-md">
              <q-item-section avatar>
                <q-skeleton type="QAvatar" />
              </q-item-section>

              <q-item-section>
                <q-item-label>
                  <q-skeleton height="17px" type="text" />
                </q-item-label>
                <q-item-label caption>
                  <q-skeleton height="10px" type="text" />
                </q-item-label>
                <q-item-label caption>
                  <q-skeleton height="15px" type="text" />
                </q-item-label>
              </q-item-section>
            </q-item>
          </template>
        </template>
      </q-list>
    </q-card>
    <!-- END RENDER FOR DASHBOARD DEVICE LIST -->
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex';
import ListLoader from '../loaders/ListLoader.vue';
import MoreIndicator from '../common/MoreIndicator.vue';
import DeviceItem from './DeviceItem.vue';
import { Drag } from 'vue-drag-drop';
import gtm from 'src/services/gtm.service';

export default {
  components: { ListLoader, MoreIndicator, DeviceItem, Drag },
  name: 'RecentDevices',
  props: {
    limit: {
      type: Number,
      default: 5,
    },
  },
  data() {
    return {
      loading: false,
      devices: [],
    };
  },
  computed: {
    ...mapGetters({}),
    deviceDeleteInProgress: {
      get() {
        return this.$store.getters['ui/deviceDeleteInProgress'];
      },
      set(val) {
        this.$store.commit('ui/setDeviceDeleteInProgress', val);
      },
    },
    sortedDevices: {
      get() {
        return _.sortBy(this.devices, 'lastSeen').reverse();
      },
    },
  },
  mounted() {
    this.getDevices();
  },
  methods: {
    ...mapActions({
      fetchDevices: 'devices/fetchDevices',
    }),
    getDevices() {
      return new Promise((resolve, reject) => {
        this.loading = true;
        this.fetchDevices({ filter: this.query, limit: this.limit + 2, offset: 0, storeResult: false })
          .then((devices) => {
            this.loading = false;
            this.devices = devices.values;
            resolve(devices);
          })
          .catch((err) => {
            reject(err);
            this.loading = false;
          });
      });
    },
    showDeviceDatail(device, full = false) {
      if (!full) {
        this.$events.$emit('component:show-device-detail:open', device);
        this.selectedDevice = device;
      } else {
        this.$router.push({ name: 'device-detail', params: { deviceId: device.uuid } });
      }
    },
    showDeviceCreateDialog() {
      this.$events.$emit('dialogs:create-device:open', {
        show: true,
      });
      gtm.logEvent('Dashboard', 'click', 'Create Device', null);
    },
  },
};
</script>
