<template>
  <q-layout class>
    <q-page-container class="fleet-manager">
      <q-page class="p-1">
        <q-card class="animated zoomIn p-2 m-1 text-center opacity-50" v-if="!showCurrentFleet"> <q-icon name="info" size="1.8em" class="pr-1" />Select a fleet to add devices. </q-card>
        <q-card class="animated zoomIn p-2 m-1 text-center opacity-50" v-if="showCurrentFleet">
          <q-icon name="info" size="1.8em" class="pr-1"></q-icon>Click [<span class="text-secondary">{{ $q.screen.gt.sm ? '←' : '↑' }}&nbsp;<q-icon color="secondary" size="1.6rem" name="add_circle"></q-icon></span>] to add devices to currently selected fleet
          <strong v-if="selectedFleet.groupName.length < 20">({{ selectedFleet.groupName }})</strong>
          <strong v-else>({{ selectedFleet.groupName.substring(0, 19) + '...' }})</strong>
          [<span class="text-negative"> <q-icon color="negative" size="1.6rem" name="remove_circle"></q-icon>&nbsp;{{ $q.screen.gt.sm ? '→' : '↓' }} </span>] to remove devices from it.
        </q-card>
        <q-card class="animated zoomIn p-2 m-1 text-center opacity-50" v-if="showCurrentDevice">
          <q-icon name="info" size="1.8em" class="pr-1"></q-icon>Click <strong class="text-secondary text-uppercase p-1">add to this fleet</strong> to add currently selected device
          <strong v-if="selectedDevice.deviceName.length < 20">({{ selectedDevice.deviceName }})</strong>
          <strong v-else>({{ selectedDevice.deviceName.substring(0, 19) + '...' }})</strong> to one or more fleets or <strong class="text-negative text-uppercase p-1">Remove from this fleet</strong> to remove it from fleets.
        </q-card>
        <div class="p-0 row" style>
          <div
            class="p-1"
            :class="{
              'col-xs-12 col-sm-12 col-md-8 col-lg-8': threeColumns,
              'col-xs-12 col-sm-12 col-md-6 col-lg-6': !threeColumns,
            }"
            style="padding-right:.55rem"
            id="available-fleets"
            v-if="showAvailableFleets"
          >
            <div class="mt-0 animated zoomIn">
              <q-card class="mt-0 p-1">
                <div class="row">
                  <div
                    :class="{
                      'col-xs-12 col-sm-12 col-md-6 col-lg-6': threeColumns,
                      'col-12': !threeColumns,
                    }"
                  >
                    <div class="p-1">
                      <h6 class="m-0 p-0 row">
                        <span class="col-auto">Fleets</span>
                        <span class="col pl-1">
                          <q-input dense rounded outlined class="m-0" placeholder="Search" inverted v-model="fleetFilter" color="none">
                            <template v-slot:prepend>
                              <q-icon name="search" />
                            </template>
                          </q-input>
                        </span>
                        <span class="col-auto">
                          <feature-teaser feature="create-fleet">
                            <q-btn flat color="secondary" @click="showAddNewDialog" class="float-right" icon="add">Add fleet</q-btn>
                          </feature-teaser>
                        </span>
                      </h6>
                    </div>
                    <div v-if="emptyFleetList" class="text-center opacity-40 p-2 h-divide-top-dotted">Nothing here</div>
                    <q-list v-else>
                      <template v-for="(fleet, key) in fleetDictionary">
                        <q-item
                          class="hoverable-2 pl-2 pr-2 h-divide-top-dotted"
                          :class="{
                            'selected-entity active-2 selected': selectedFleet && selectedFleet.id === key,
                          }"
                          :key="key + '' + randomKey"
                          v-if="!selectedDevice || !selectedDevice.fleets || !selectedDevice.fleets.length || !selectedDevice.fleets[key]"
                          @click.native="selectFleet(fleet.id)"
                        >
                          <q-item-section avatar>
                            <img class="col-auto mr-1" style="max-width: 1.8rem;" src="statics/svg/icons/soc-fleet.svg" />
                          </q-item-section>
                          <q-item-section class="relaxed">
                            <q-item-label class>
                              <div class="q-item-tile2 label device-name">{{ fleet.groupName }}</div>

                              <div class="row pt-0 q-item-tile2 device-id">
                                <div class="col sublabel">
                                  <div class="row">
                                    <div class="col">
                                      Contains
                                      {{ fleet.devices.length }}
                                      {{ fleet.devices.length > 1 ? 'devices' : 'device' }}
                                    </div>
                                  </div>
                                </div>
                                <div class="col-auto">
                                  <div v-if="fleetViewData[key]" class="text-secondary opacity-50">
                                    Saving changes ...
                                    <q-linear-progress indeterminate color="secondary" class="q-mt-sm" />
                                  </div>
                                  <q-btn color="secondary" flat size="sm" class="float-right device-id" icon="add" v-if="!(showAvailableFleets && showAvailableDevices) && !fleetViewData[key]" @click="onAddDeviceToFleet(selectedDevice.uuid, fleet.id, key, $event)">&nbsp; Add to this fleet</q-btn>
                                </div>
                              </div>
                              <div class="absolute-top-right">
                                <q-btn
                                  flat
                                  color="secondary"
                                  class
                                  @click="
                                    $event.stopPropagation();
                                    showOptionsDialog(fleet);
                                  "
                                  icon="settings"
                                ></q-btn>
                              </div>
                            </q-item-label>
                          </q-item-section>
                        </q-item>
                      </template>
                    </q-list>
                  </div>

                  <div class="p-1 col-xs-12 col-sm-12 col-md-6 col-lg-6 q-card shadow-0" id="fleet-in-view" v-if="showCurrentFleet">
                    <div
                      class="selected-entity2 no-shadow h-100"
                      :class="{
                        'v-divider-inset-card pl-2': $q.screen.gt.sm,
                        'h-divider-inset-card pt-3': $q.screen.lt.md,
                      }"
                    >
                      <div class="info-div">
                        <q-btn @click="clearSelections" flat icon="close" class="m-1 float-right" />
                        <q-item class="pt-3 pb-3">
                          <!-- v-if="viewType!='card'" -->
                          <q-item-section avatar>
                            <img class style="max-width: 2.5rem; top: .5rem; left: .5rem" src="statics/svg/icons/soc-fleet.svg" />
                          </q-item-section>

                          <q-item-section class="relaxed">
                            <q-item-label class>
                              <!-- <h6 class="thin-text m-0">Current fleet</h6> -->
                              <div class="q-item-tile label device-name">
                                <h5 class="m-0 ellipsis">{{ selectedFleet.groupName }}</h5>
                              </div>

                              <!-- <div class="q-item-tile label device-id mt-1 mb-0 opacity-50">ID: {{ fleet.groupId }}</div> -->
                              <div class="q-item-tile sublabel device-id mt-1">Contains {{ selectedFleet.devices.length }} {{ selectedFleet.devices.length > 1 ? 'devices' : 'device' }}</div>
                              <div v-if="selectedFleet.hardwareType" class="row pt-0 q-item-tile2 device-id">
                                <!-- <div class="col sublabel">
                                  <div class="row">
                                    <q-chip
                                      dense
                                      class="col-auto"
                                    >{{selectedFleet.hardwareType}}</q-chip>
                                  </div>
                                </div> -->
                              </div>
                            </q-item-label>
                          </q-item-section>
                        </q-item>
                      </div>
                      <div class="p-0 text-center">
                        <h6 v-if="selectedFleet.devices.length" class="m-0">
                          <small v-if="selectedFleet.devices.length > 1">Contains the following devices</small>
                          <small v-else>Contains one device</small>
                        </h6>
                        <h6 v-else class="m-0 opacity-50">No device here yet</h6>
                      </div>
                      <q-list>
                        <template v-for="(device, key) in deviceDictionary">
                          <q-item
                            v-if="selectedFleet.devices[key]"
                            class="pl-0 pr-0 h-divide-top-dotted q-card shadow-0"
                            :class="{
                              animateItemInRight: $q.screen.gt.sm && animateItemToFleet[key],
                              animateItemInUp: $q.screen.lt.md && animateItemToFleet[key],
                            }"
                            :key="key + '_key'"
                            @click.native="selectDevice(device.uuid)"
                            style="overflow: hidden"
                          >
                            <div class="w-100 row">
                              <div class="col-9">
                                <div class="row">
                                  <div class="col-auto p-1">
                                    <img class="col-auto mr-1" style="max-width: 1.5rem;" src="statics/svg/icons/som.svg" />
                                  </div>
                                  <div class="col">
                                    <div class="q-item-tile2 label device-name">{{ device.deviceName }}</div>
                                    <div class="row pt-0 q-item-tile device-id">
                                      <div class="col sublabel opacity-40">
                                        <div class="row">
                                          <div class="col">ID: {{ device.deviceId }}</div>
                                        </div>
                                      </div>
                                    </div>
                                  </div>
                                </div>
                              </div>
                              <div class="col-3">
                                <q-btn color="negative" flat full-width size="0.8rem" class="full-width" icon="remove_circle" v-if="showCurrentFleet && !fleetViewData[key]" @click="onRemoveDeviceFromFleet(device.uuid, selectedFleet.id, key, $event)">&nbsp;{{ $q.screen.gt.sm ? '→' : '↓' }}</q-btn>
                              </div>

                              <div v-if="fleetViewData[key]" class="col-12 text-center">
                                <div class="text-secondary opacity-50">
                                  Saving changes ...
                                  <q-linear-progress indeterminate color="secondary" class="q-mt-sm" />
                                </div>
                              </div>
                            </div>
                            <device-online-badge :device="device"></device-online-badge>
                          </q-item>
                        </template>
                      </q-list>
                    </div>
                  </div>
                </div>
              </q-card>
            </div>
          </div>

          <div
            class="p-1"
            :class="{
              'col-xs-12 col-sm-12 col-md-4 col-lg-4': threeColumns,
              'col-xs-12 col-sm-12 col-md-6 col-lg-6': !threeColumns,
            }"
            id="available-devices"
            v-if="showAvailableDevices"
          >
            <div class="mt-0">
              <q-card class="mt-0 p-0">
                <div class="p-1">
                  <h6 class="m-0 row ml-1 mr-1">
                    <span class="col-auto">Available Devices</span>
                    <span class="col pl-1">
                      <q-input dense rounded outlined class="m-0" placeholder="Search" inverted v-model="deviceFilter" color="none">
                        <template v-slot:prepend>
                          <q-icon name="search" />
                        </template>
                      </q-input>
                    </span>
                  </h6>
                </div>
                <div v-if="emptyDeviceList" class="text-center opacity-40 p-2 h-divide-top-dotted">Nothing here</div>
                <q-list v-else>
                  <template v-for="(device, key) in filteredDeviceDictionary">
                    <q-item
                      class="pr-2 h-divide-top-dotted q-card shadow-0"
                      :class="{
                        animateItemInLeft: $q.screen.gt.sm && animateItemToDevices[key],
                        animateItemInDown: $q.screen.lt.md && animateItemToDevices[key],
                      }"
                      :key="key"
                      v-if="!selectedFleet || !selectedFleet.devices || !selectedFleet.devices.length || !selectedFleet.devices[key]"
                      @click.native="selectDevice(device.uuid)"
                      style="overflow: hidden;"
                    >
                      <!-- <q-item-section avatar class="p-1 mr-1">
                        
                      </q-item-section>-->
                      <div class="w-100  row">
                        <div class="col-9">
                          <div class="relaxed row">
                            <div avatar class="col-auto p-1">
                              <img class="col-auto mr-1" style="max-width: 1.5rem;" src="statics/svg/icons/som.svg" />
                            </div>
                            <div class="col">
                              <div class="q-item-tile2 label device-name">{{ device.deviceName }}</div>
                              <div class="row pt-0 q-item-tile2 device-id">
                                <div class="col-11 sublabel">
                                  <div class="row">
                                    <div class="col">Belongs to {{ (device.fleets || []).length }} {{ device.fleets.length > 1 ? 'fleets' : 'fleet' }}</div>
                                  </div>
                                </div>
                                <div class="col-12">
                                  <div v-if="fleetViewData[key]" class="text-secondary opacity-50">
                                    Saving changes ...
                                    <q-linear-progress indeterminate color="secondary" class="q-mt-sm" />
                                  </div>
                                </div>
                              </div>
                              <div v-if="device.hardwareType" class="row pt-0 q-item-tile2 device-id">
                                <div class="col sublabel opacity-50">
                                  <div class="row">
                                    <q-chip dense class="col-auto">{{ device.hardwareType }}</q-chip>
                                  </div>
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                        <div class="col">
                          <q-btn color="secondary" flat size="0.8rem" class="full-width" icon-right="add_circle" v-if="showCurrentFleet && !fleetViewData[key]" @click="onAddDeviceToFleet(device.uuid, selectedFleet.id, key, $event)">{{ $q.screen.gt.sm ? '←' : '↑' }}&nbsp;</q-btn>
                        </div>
                      </div>
                      <device-online-badge :device="device"></device-online-badge>
                    </q-item>
                  </template>
                </q-list>
              </q-card>
            </div>
          </div>
        </div>

        <q-dialog v-model="showFleetOptions">
          <q-card class="w-80 mxw-50em p-2">
            <div class="row">
              <div
                class="col-md-6 col-lg-6 col-xl-6 col-xs-12 col-sm-12 pl-1 pr-1"
                :class="{
                  'text-center pb-2': $q.screen.lt.md,
                }"
              >
                <h6 class="m-0 pb-1">{{ (fleetInOption || {}).groupName }}</h6>
                <div class="col sublabel">
                  <div class="row">
                    <div class="col">
                      Contains
                      {{ (fleetInOption || { devices: [] }).devices.length }}
                      <!-- <em>{{(fleetInOption || {}).hardwareType}}</em> -->
                      {{ (fleetInOption || { devices: [] }).devices.length > 1 ? 'devices' : 'device' }}
                    </div>
                  </div>
                </div>
                <!-- <div
                  v-if="(fleetInOption || {devices:[]}).hardwareType 
                && $q.screen.gt.sm"
                  class="row pt-0 q-item-tile2 device-id"
                >
                  <div class="col sublabel">
                    <div class="row">
                      <q-chip
                        dense
                        class="col-auto"
                      >{{(fleetInOption || {devices:[]}).hardwareType}}</q-chip>
                    </div>
                  </div>
                </div> -->
              </div>
              <div
                class="col-md-6 col-lg-6 col-xl-6 col-xs-12 col-sm-12"
                :class="{
                  'v-divide-left-dotted': $q.screen.gt.sm,
                }"
              >
                <q-list>
                  <q-item
                    :class="{
                      'h-divide-top-dotted': $q.screen.lt.md,
                    }"
                    @click="showEditDialog(fleetInOption)"
                    clickable
                    v-close-popup
                    v-ripple
                  >
                    <q-item-section avatar>
                      <q-icon color="secondary" name="edit" />
                    </q-item-section>

                    <q-item-section>Rename this fleet</q-item-section>
                    <q-item-section avatar>
                      <q-icon name="keyboard_arrow_right" />
                    </q-item-section>
                  </q-item>

                  <q-item class="h-divide-top-dotted" @click="promptForDelete(fleetInOption)" clickable v-close-popup v-ripple>
                    <q-item-section avatar>
                      <q-icon color="negative" name="delete" />
                    </q-item-section>

                    <q-item-section>Delete this fleet</q-item-section>
                    <q-item-section avatar>
                      <q-icon name="keyboard_arrow_right" />
                    </q-item-section>
                  </q-item>
                </q-list>
              </div>
            </div>
          </q-card>
        </q-dialog>
      </q-page>
    </q-page-container>
  </q-layout>
</template>

<script>
import DeviceItem from '../devices/DeviceItem';
import DeviceOnlineBadge from '../devices/DeviceOnlineBadge';
import shortid from 'shortid';
import Vue from 'vue';
import { mapActions } from 'vuex';
import gtm from '../../services/gtm.service';

export default {
  name: 'FleetForDevice',
  components: {
    DeviceItem,
    DeviceOnlineBadge,
  },
  data() {
    return {
      selectedDevice: null,
      _selectedFleet: null,
      _fleetDictionary: {},
      _deviceDictionary: {},
      fleetsLoaded: false,
      devicesLoaded: false,
      fleetViewData: {},
      fleetFilter: '',
      deviceFilter: '',
      randomKey: shortid(),
      animateItemToFleet: {},
      animateItemToDevices: {},
      _showFleetOptions: true,
      fleetInOption: null,
    };
  },
  watch: {
    fleetList(n, o) {
      this.prepareFleets();
    },
    deviceList(n, o) {
      this.prepareDevices();
    },
    fleetFilter(value) {
      value && gtm.logEvent('Fleets Page', 'filter', 'Fleets', value);
    },
    deviceFilter(value) {
      value && gtm.logEvent('Fleets Page', 'filter', 'Avalable Devices', value);
    },
  },
  computed: {
    showFleetOptions: {
      get() {
        return !!this.fleetInOption;
      },
      set(v) {
        if (!v) {
          this.fleetInOption = null;
        }
        // this.$data._showFleetOptions = v;
      },
    },
    showAvailableDevices() {
      return true;
      //   return (
      //     (!this.selectedDevice && this.selectedFleet) ||
      //     (!this.selectedDevice && !this.selectedFleet)
      //   );
    },
    showAvailableFleets() {
      return true;
      //   return (
      //     (this.selectedDevice && !this.selectedFleet) ||
      //     (this.selectedDevice && this.selectedFleet) ||
      //     (!this.selectedDevice && !this.selectedFleet)
      //   );
    },
    showCurrentDevice() {
      return this.selectedDevice || (this.selectedDevice && this.selectedFleet);
    },
    showCurrentFleet() {
      return this.selectedFleet && !this.selectedDevice;
    },
    threeColumns() {
      return this.showCurrentDevice || this.showCurrentFleet;
    },
    deviceDictionary() {
      return this.$data._deviceDictionary;
    },
    filteredDeviceDictionary() {
      const data = {};
      const regex = new RegExp(`${this.deviceFilter}`, 'gi');
      Object.values(this.deviceDictionary)
        .filter((f) => f.uuid.match(regex) || f.deviceName.match(regex) || f.deviceId.match(regex))
        .forEach((x) => {
          data[x.uuid] = x;
        });
      return data;
    },
    fleetDictionary() {
      const data = {};
      const regex = new RegExp(`${this.fleetFilter}`, 'gi');
      Object.values(this.$data._fleetDictionary)
        .filter((f) => f.id.match(regex) || f.groupName.match(regex))
        .forEach((x) => {
          data[x.id] = x;
        });
      return data;
    },
    emptyFleetList() {
      return this.showAvailableFleets && Object.keys(this.fleetDictionary).filter((a) => !((this.selectedDevice || {}).fleets || {})[a]).length < 1;
    },
    emptyDeviceList() {
      return this.showAvailableDevices && Object.keys(this.filteredDeviceDictionary).filter((a) => !((this.selectedFleet || {}).devices || {})[a]).length < 1;
    },
    devices() {
      return (this.deviceList || []).filter((a) => {
        const isInSelectedDevice = ((this.selectedFleet || {}).devices || {})[a.uuid];
        return (this.showAvailableDevices && this.showAvailableFleets) || !isInSelectedDevice;
      });
    },
    fleets() {
      return (this.fleetList || []).filter((a) => {
        return (this.showAvailableDevices && this.showAvailableFleets) || !((this.selectedDevice || {}).fleets || {})[a.id];
      });
    },
    fleetList() {
      return this.$store.getters['fleets/fleets'];
    },
    deviceList() {
      return this.$store.getters['devices/devices'];
    },
    fleetDeleteInProgress: {
      get() {
        return this.$store.getters['ui/fleetDeleteInProgress'];
      },
      set(val) {
        this.$store.commit('ui/setFleetDeleteInProgress', val);
      },
    },

    selectedFleet: {
      get() {
        return this.fleetDictionary[this.$data._selectedFleet];
      },
      set(v) {
        this.$data._selectedFleet = (v || {}).id;
      },
    },
  },
  mounted() {
    //   this.pageTitle = "Fleet Manager"
    this.getFleets();
    this.getDevices();
  },
  created() {},
  methods: {
    ...mapActions({
      fetchDevices: 'devices/fetchDevices',
      fetchFleets: 'fleets/fetchFleets',
      deleteFleet: 'fleets/deleteFleet',
      removeDeviceFromFleet: 'fleets/removeDeviceFromFleet',
      addDeviceToFleet: 'fleets/addDeviceToFleet',
    }),
    getFleets() {
      this.loading = true;
      this.fetchFleets()
        .then((fleets) => {
          this.fleetsLoaded = true;
          if (this.devicesLoaded) {
            this.prepareFleets();
            this.prepareDevices();
          } else {
            this.prepareFleets();
          }
        })
        .catch((err) => {
          //   this.onPageLoad();
          //   this.loading = false;
        });
      // this.getUnfleeted();
    },
    getDevices() {
      //   this.loading = true;
      this.fetchDevices()
        .then((devices) => {
          this.devicesLoaded = true;
          if (this.fleetsLoaded) {
            this.prepareDevices();
            this.prepareFleets();
          } else {
            this.prepareDevices();
          }
        })
        .catch((err) => {
          //   this.loading = false;
        });
    },
    prepareFleets() {
      this.fleetList.forEach((f) => {
        const devices = {};
        f.devices.values.forEach((f) => {
          devices[f] = f;
        });

        Vue.set(this.$data._fleetDictionary, f.id, {
          ...f,
          devices: devices,
        });
        this.setFleetDevicesLength(f.id);
      });
      if (this.$route.query.fleetId) {
        this.selectFleet(this.$route.query.fleetId);
      }
    },
    prepareDevices() {
      this.deviceList.forEach((d) => {
        const fleets = {};
        this.fleetList
          .filter((f) => (f.devices || { values: [] }).values.find((a) => d.uuid === a))
          .forEach((m) => {
            fleets[m.id] = m.id;
          });
        Vue.set(this.$data._deviceDictionary, d.uuid, {
          ...d,
          fleets: fleets,
        });
        this.setDeviceFleetsLength(d.uuid);
      });
      if (this.$route.query.deviceId) {
        this.selectDevice(this.$route.query.deviceId);
      }
    },
    selectDevice(deviceId) {
      //   const device = this.deviceDictionary[deviceId];
      //   if (!device) return;
      //   this.selectedDevice = device;
      //   this.selectedFleet = null;
    },
    selectFleet(fleetId) {
      const fleet = this.fleetDictionary[fleetId];
      if (!fleet) return;
      this.$data._selectedFleet = fleetId;
    },
    clearSelections() {
      this.selectedDevice = null;
      this.selectedFleet = null;
    },
    setDeviceFleetsLength(deviceUuid) {
      const device = this.deviceDictionary[deviceUuid] || {};
      device.fleets = device.fleets || {};

      Vue.set(device.fleets, 'length', Object.keys(device.fleets).filter((f) => f !== 'length').length);
    },
    setFleetDevicesLength(fleetId) {
      const fleet = this.fleetDictionary[fleetId];
      if (!fleet) return;
      fleet.devices = fleet.devices || {};
      // setTimeout(() => {
      //   this.setFleetHardwareType(fleetId);
      // }, 500);
      const hardwareType = this.getFleetHardwareType(fleetId);
      Vue.set(fleet, 'hardwareType', hardwareType);
      Vue.set(fleet.devices, 'length', Object.keys(fleet.devices).filter((f) => f !== 'length').length);
      this.$forceUpdate();
    },
    getFleetHardwareType(fleetId) {
      const fleet = this.fleetDictionary[fleetId] || {};
      fleet.devices = fleet.devices || {};
      return (this.$data._deviceDictionary[Object.keys(fleet.devices || [])[0]] || {}).hardwareType || '';
    },
    setFleetHardwareType(fleetId) {
      const hardwareType = this.getFleetHardwareType(fleetId);
      Vue.set(this.$data._fleetDictionary[fleetId] || {}, 'hardwareType', hardwareType);
      this.$forceUpdate();
    },
    /** Adds a device to fleet */
    onAddDeviceToFleet(deviceUuid, fleetId, viewId, event = {}) {
      event.stopPropagation();
      Vue.set(this.animateItemToFleet, viewId, true);
      setTimeout(() => {
        Vue.set(this.animateItemToFleet, viewId, false);
      }, 1500);
      if (!deviceUuid) return;
      if (!fleetId) return;
      const device = this.deviceDictionary[deviceUuid];
      const fleet = this.fleetDictionary[fleetId];
      if (!fleet) return;
      if (!device) return;
      Vue.set(fleet.devices, deviceUuid, deviceUuid);
      Vue.set(device.fleets, fleetId, fleetId);
      this.setFleetDevicesLength(fleetId);
      this.setDeviceFleetsLength(deviceUuid);
      this.deviceAdded(fleet, device, viewId);
    },
    /** Remove a device from fleet */
    onRemoveDeviceFromFleet(deviceUuid, fleetId, viewId, event = {}) {
      event.stopPropagation();
      Vue.set(this.animateItemToDevices, viewId, true);
      setTimeout(() => {
        Vue.set(this.animateItemToDevices, viewId, false);
      }, 1500);
      if (!deviceUuid) return;
      if (!fleetId) return;
      const device = this.deviceDictionary[deviceUuid];
      const fleet = this.fleetDictionary[fleetId];
      if (!fleet) return;
      if (!device) return;
      this.$delete(fleet.devices, deviceUuid, deviceUuid);
      this.$delete(device.fleets, fleetId, fleetId);
      this.setFleetDevicesLength(fleetId);
      this.setDeviceFleetsLength(deviceUuid);
      this.deviceRemoved(fleet, device, viewId);
    },
    // deleteItemFromArray(arr, itemProperty, searchProperty) {
    //   if (!arr || !arr.length) return;
    //   arr.splice(arr.findIndex(a => a[searchProperty] === itemProperty), 1);
    // },

    deviceAdded(fleet, device, viewId) {
      Vue.set(this.fleetViewData, viewId, true);
      this.addDeviceToFleet({ fleetId: fleet.id, deviceUuid: device.uuid })
        .then((data) => {
          //   this.$q.notify({
          //       message: `${device.deviceName} was successfully added to ${
          //           fleet.groupName
          //     }.`,
          //     color: "positive"
          //   });
          Vue.set(this.fleetViewData, viewId, false);
        })
        .catch((err) => {
          this.$delete(fleet.devices, device.uuid, device.uuid);
          this.$delete(device.fleets, fleet.id, fleet.id);
          this.setFleetDevicesLength(fleet.id);
          this.setDeviceFleetsLength(device.uuid);
          this.$set(this.fleetViewData, viewId, false);
          console.log(err);
          if (err.code === 'conflicting_entity') {
            this.$q.notify({
              message: `${device.deviceName} was aready added to this fleet.`,
              color: 'warning',
            });
          } else if (err.code === 'device_not_activated') {
            this.$q.notify({
              message: `${device.deviceName} must be activated to add it to this fleet.`,
              color: 'negative',
            });
          } else if (err.code === 'incompatible_hardware') {
            const hardwareId = err.hardwareId;
            this.$q.notify({
              message: `Only ${hardwareId} can be added to this fleet.`,
              color: 'negative',
            });
          }
        });
    },
    deviceRemoved(fleet, device, viewId) {
      this.$set(this.fleetViewData, viewId, true);
      this.removeDeviceFromFleet({ fleetId: fleet.id, deviceUuid: device.uuid })
        .then((data) => {
          this.$set(this.fleetViewData, viewId, false);
        })
        .catch((err) => {
          this.$set(this.fleetViewData, viewId, false);
          this.$set(fleet.devices, device.uuid, device.uuid);
          this.$set(device.fleets, fleet.id, fleet.id);
          this.setFleetDevicesLength(fleet.id);
          this.setDeviceFleetsLength(device.uuid);
          this.$q.notify({
            message: `Unable to remove "${device.deviceName}" from "${fleet.groupName}" fleet. Please try again`,
            color: 'negative',
          });
        });
    },
    showAddNewDialog(existing) {
      this.$events.$emit(`dialogs:create-fleet:open`, {
        show: true,
        fleet: existing || {},
      });
      gtm.logEvent('Fleets Page', 'click', 'Create Fleet', null);
    },
    showOptionsDialog(fleet) {
      this.fleetInOption = fleet;
      // this.$events.$emit(`dialogs:confirm:open`, {
      //   title: '<div class="text-center">'+fleet.groupName + ' Options</div>',
      //   yesLabel: "OK",
      //   noLabel: "Cancel",
      //   yesAction: () => {},
      //   noAction: () => {},
      //   noColor: "default",
      //   noColor: "default",
      //   hideActions: true,
      //   notPersistent: true
      // });
    },
    showEditDialog(fleet) {
      this.$events.$emit(`dialogs:create-fleet:open`, {
        show: true,
        fleet: fleet || {},
      });
    },
    promptForDelete(fleet) {
      this.$events.$emit(`dialogs:confirm:open`, {
        title: `Delete ${fleet.groupName}?`,
        message: `This can't be undone.`,
        color: 'default',
        icon: 'delete',
        yesLabel: 'Yes, please!',
        yesColor: 'negative',
        noLabel: 'No',
        yesAction: () => {
          this.fleetDeleteInProgress = fleet;
          const name = fleet.groupName;
          this.deleteFleet(fleet.id, name)
            .then((deleted) => {
              this.$delete(this.$data._fleetDictionary, fleet.id);
              this.$q.notify({
                color: 'positive',
                message: `${name} deleted!`,
              });
            })
            .catch((err) => {
              this.fleetDeleteInProgress = null;
              this.$q.notify({
                message: `Unable to delete ${name}!`,
                color: 'negative',
              });
            });
        },
      });
    },
  },
};
</script>
