<template>
  <q-dialog v-model="show" persistent>
    <q-card class="mnw-50em">
      <template v-if="loadingDeviceCount">
        <q-card-section class="">
          <div class=" items-center q-pa-md">
            <div class="q-pt-xl faded text-h3 thin-text text-center">Loading...</div>
          </div>
        </q-card-section>
        <q-card-actions align="right" class="q-ma-md">
          <q-btn label="Cancel" flat color="primary" @click="close" />
        </q-card-actions>
      </template>
      <template v-else>
        <q-card-section class="">
          <div class="row items-center">
            <div class="col ">
              <h4 class="q-ma-none">{{ title }}</h4>
            </div>
            <div class="col-auto"><q-btn icon="close" flat dense @click="close"></q-btn></div>
          </div>
        </q-card-section>
        <q-separator />
        <template v-if="noDevicesProvisioned">
          <p class="q-ma-md q-px-md q-py-xl text-center">You have haven’t provisioned any devices yet! <br />Go to the devices tab and add at least one device, then come back to install this package.</p>
        </template>
        <template v-else>
          <div class="text-white bg-warning text-center q-pa-sm" v-if="warningMessage"><q-icon name="warning" class="q-mr-sm" size="1.2rem" />{{ warningMessage }}</div>
          <div class="text-white bg-negative text-center q-pa-sm" v-if="errorMessage"><q-icon name="error" class="q-mr-sm" size="1.2rem" />{{ errorMessage }}</div>
          <q-card-section v-if="view === 'selection'" class="q-mx-md q-my-sm mxh-30em overflow-y-auto">
            <div>
              <p>
                You are about to install <b>{{ package.name }}</b> version <b>{{ package.version }}</b
                >.
              </p>
              <p>Select the device or fleet below to install this package.</p>
            </div>
            <div class="mxh-30em  position-sticky">
              <q-tabs v-model="tab" @input="tabChanged" indicator-color="primary" active-class="text-primary">
                <q-tab name="selectDevice">Select Device</q-tab>
                <q-tab name="selectFleet">Select Fleet</q-tab>
              </q-tabs>
              <q-tab-panels v-model="tab">
                <q-tab-panel name="selectDevice">
                  <div class="column q-gutter-md" style="overflow-y:auto">
                    <div class="row items-center q-pa-md" style="position: sticky; top:0; z-index: 1;">
                      <div class="col">
                        <filter-input v-model="deviceFilter" placeholder="Filter devices" :rounded="false" @input="queryDevices" />
                      </div>
                      <div class="col q-pl-md">
                        Showing <b>{{ filteredDevices.length }}</b>
                        <!-- of <b>{{ totalDevices }}</b>  -->
                        devices
                      </div>
                      <div v-if="loadingDevices" class="col-12 q-mt-sm">
                        <q-linear-progress size="1px" indeterminate />
                      </div>
                    </div>
                    <q-list separator class="mxh-20em">
                      <q-item v-for="device in filteredDevices" :key="device.id" clickable v-ripple @click.stop="deviceClicked(device)" dense :active="selectedDevice && selectedDevice.uuid == device.uuid" active-class="text-primary">
                        <q-item-section side top>
                          <q-icon v-if="selectedDevice && selectedDevice.uuid == device.uuid" name="check_circle_outline" color="primary" />
                          <q-icon v-else name="radio_button_unchecked" />
                        </q-item-section>
                        <q-item-section>
                          <q-item-label>{{ device.deviceName }}</q-item-label>
                        </q-item-section>
                      </q-item>
                    </q-list>
                  </div>
                </q-tab-panel>
                <q-tab-panel name="selectFleet">
                  <div class="column q-gutter-md" style="overflow-y:auto">
                    <div class="row items-center q-py-md" style="position: sticky; top:0; z-index: 1;">
                      <div class="col">
                        <filter-input v-model="fleetFilter" placeholder="Filter fleets" :rounded="false" @input="queryFleets" />
                      </div>
                      <div class="col q-pl-md">
                        <div>
                          <q-checkbox dense v-model="hideFleetsWithoutAnyDevice" label="Hide fleets with no device" />
                        </div>
                        Showing <b>{{ filteredFleets.length }}</b> of <b>{{ totalFleets }}</b>
                        fleets
                      </div>
                      <div v-if="loadingFleets" class="col-12 q-mt-sm">
                        <q-linear-progress size="1px" indeterminate />
                      </div>
                    </div>
                    <q-list separator class="mxh-20em">
                      <q-item v-for="fleet in filteredFleets" :key="fleet.id" clickable v-ripple @click.stop="fleetClicked(fleet)" dense :active="selectedFleet && selectedFleet.id == fleet.id" active-class="text-primary" :disable="fleet.devices.length < 1">
                        <q-item-section side top>
                          <q-icon v-if="selectedFleet && selectedFleet.id == fleet.id" name="check_circle_outline" color="primary" />
                          <q-icon v-else name="radio_button_unchecked" />
                        </q-item-section>
                        <q-item-section>
                          <q-item-label>{{ fleet.groupName }}</q-item-label>
                        </q-item-section>
                        <q-item-section side top>
                          <q-item-label caption class="faded">{{ fleet.devices.length }} devices</q-item-label>
                        </q-item-section>
                      </q-item>
                    </q-list>
                  </div>
                </q-tab-panel>
              </q-tab-panels>
            </div>
          </q-card-section>

          <q-card-section v-else-if="view === 'confirmation'" class="q-mx-md q-my-sm">
            <div>
              <h5 class="text-center q-ma-none">
                Summary of your selection for this update.
              </h5>
              <br />
              <div class="text-white bg-warning q-pa-sm text-center" v-if="warningMessage"><q-icon name="warning" class="q-mr-sm" size="1.2rem" />{{ warningMessage }}</div>
              <div class="text-negative text-center" v-if="errorMessage"><q-icon name="error" class="q-mr-sm" size="1.2rem" />{{ errorMessage }}</div>
              <div class="q-mt-md">
                <q-item v-if="selectedDevice">
                  <q-item-section>
                    <q-item-label class="faded">Selected device</q-item-label>
                    <q-item-label class="text-bold">
                      <q-icon name="check_circle_outline" class="q-mr-sm" color="primary" size="1.3rem" />
                      {{ selectedDevice.deviceName }}</q-item-label
                    >
                  </q-item-section>
                </q-item>
                <q-item v-else-if="selectedFleet">
                  <q-item-section>
                    <q-item-label class="faded">Selected fleet</q-item-label>
                    <q-item-label class="text-bold">
                      <q-icon name="check_circle_outline" class="q-mr-sm" color="primary" size="1.3rem" />
                      {{ selectedFleet.groupName }}</q-item-label
                    >
                  </q-item-section>
                </q-item>
                <div class="q-ml-md q-mt-md">
                  <q-item-label class="faded">Package version to install:</q-item-label>
                  <package-info :pkg="package" />
                </div>
              </div>
            </div>
          </q-card-section>
        </template>

        <q-separator />
        <q-card-actions align="right" class="q-ma-md">
          <q-btn v-if="view === 'selection'" label="Cancel" flat color="primary" @click="close" />
          <q-btn v-else-if="view === 'confirmation'" label="Go back" flat color="primary" @click="goBack" />
          <template v-if="noDevicesProvisioned">
            <q-btn label="Go to devices" color="primary" @click="gotoDevicePairing" />
          </template>
          <template v-if="selectedDevice || selectedFleet">
            <q-btn v-if="view == 'selection'" label="Confirm Selection" color="primary" @click="confirmSelection" :disable="this.loadingDevices || this.loadingFleets" />
            <q-btn v-else-if="view == 'confirmation'" label="Submit" color="primary" @click="submit" />
          </template>
        </q-card-actions>
      </template>
    </q-card>
  </q-dialog>
</template>

<script>
import { mapActions } from 'vuex';
import FilterInput from 'src/components/common/FilterInput.vue';
import PackageInfo from 'src/components/packages/PackageInfo.vue';
import { loadingDevices } from '../../store/ui/getters';
import Empty from '../common/Empty.vue';

export default {
  name: 'PackageQuickInstallDialog',
  components: {
    FilterInput,
    PackageInfo,
    Empty,
  },
  props: {
    packageVersion: {
      type: Object,
      default: () => ({}),
    },
  },
  data() {
    return {
      show: true,
      tab: 'selectDevice',
      filteredDevices: [],
      fleets: [],
      deviceFilter: '',
      fleetFilter: '',
      selectedDevice: null,
      selectedFleet: null,
      totalDevices: 0,
      totalFleets: 0,
      view: 'selection',
      loadingDevices: false,
      loadingFleets: false,
      hideFleetsWithoutAnyDevice: true,
      errorMessage: '',
      warningMessage: '',
      payload: {},
      installedTargetsResponse: [],
      userDeviceCount: 0,
      loadingDeviceCount: true,
    };
  },
  computed: {
    title() {
      return this.userDeviceCount > 0 ? `Install this package version` : `Device not found`;
    },
    package() {
      return this.packageVersion || {};
    },
    filteredFleets() {
      return this.hideFleetsWithoutAnyDevice ? this.fleets.filter((fleet) => fleet.devices.length > 0) : this.fleets;
    },
    noDevicesProvisioned() {
      return this.userDeviceCount < 1 && !this.loadingDevices && !this.loadingFleets;
    },
  },
  methods: {
    ...mapActions({
      fetchDevices: 'devices/fetchDevices',
      fetchFleets: 'fleets/fetchFleets',
      getInstalledTargets: 'devices/getInstalledTargets',
      fetchDeviceCount: 'devices/fetchDeviceCount',
    }),
    close() {
      this.$emit('close', false);
    },
    deviceClicked(device) {
      this.selectedDevice = device;
      this.selectedFleet = null;
      this.errorMessage = '';
      this.warningMessage = '';
    },
    fleetClicked(fleet) {
      this.selectedFleet = fleet;
      this.selectedDevice = null;
      this.errorMessage = '';
      this.warningMessage = '';
    },
    tabChanged(tab) {
      this.selectedDevice = null;
      this.selectedFleet = null;
      this.errorMessage = '';
    },
    confirmSelection() {
      if (!this.selectedDevice && !this.selectedFleet) {
        this.errorMessage = 'Please select either a device or a fleet to proceed';
        return;
      }
      if (this.selectedDevice) {
        this.loadingDevices = true;
      } else {
        this.loadingFleets = true;
      }
      this.validateSelection()
        .then(() => {
          if (!this.errorMessage) {
            this.view = 'confirmation';
          }
        })
        .catch((error) => {
          this.errorMessage = error.message || error;
        })
        .finally(() => {
          this.loadingDevices = false;
          this.loadingFleets = false;
        });
    },
    goBack() {
      this.view = 'selection';
    },

    gotoDevicePairing() {
      this.$router.push({ name: 'devices' });
      this.close();
    },

    async validateSelection() {
      let devices;
      if (this.selectedDevice) {
        devices = [{ ...this.selectedDevice }];
      } else if (this.selectedFleet) {
        const fleet = this.selectedFleet;
        devices = fleet.devices.values;
      }
      let installedTargets = [];
      let deviceUuids = devices.map((device) => device.uuid);
      try {
        const response = await this.getInstalledTargets(deviceUuids, { storeResult: false });
        if (!response.values || Object.keys(response.values).length < 1) {
          throw this.selectedDevice ? 'The selected device does not have any valid component' : 'The selected fleet does not have any valid component';
        }
        this.installedTargetsResponse = response.values;
        devices.forEach((device, index) => {
          let deviceTargets = response.values[device.uuid];

          // Filter out targets that are not compatible with the package
          deviceTargets = deviceTargets.filter((target) => {
            return this.package.hardwareIds.includes(target.hardwareId);
          });

          if (!deviceTargets || deviceTargets.length < 1) {
            if (this.selectedDevice) {
              throw `The selected device does not have any updatable component compatible with this package`;
            }
          } else {
            device.installedTargets = deviceTargets;

            device.ecus = device.installedTargets.map((target) => {
              return {
                ...target,
                id: target.ecuId,
                image: target,
                package: this.package,
              };
            });
            installedTargets = installedTargets.concat(device.ecus);
          }
        });
        if (!installedTargets.length) {
          throw `There are no updatable components compatible with this package in the selected fleet`;
        }
        // Filter out devices that do not have any valid component
        devices = devices.filter((device) => device.installedTargets && device.installedTargets.length > 0);

        if (!devices.length) {
          throw this.selectedDevice ? 'The selected device does not have any compatible component' : 'It appears that the selected fleet does not have any compatible component';
        }
      } catch (error) {
        throw error;
      }
    },

    // Prepare the payload to be sent to the update component
    preparePayload() {
      let installedTargets = [];
      this.installedTargetsResponse = this.installedTargetsResponse || [];
      let devices;
      if (this.selectedDevice) {
        devices = [{ ...this.selectedDevice }];
      } else if (this.selectedFleet) {
        const fleet = this.selectedFleet;
        devices = fleet.devices.values;
      }
      devices.forEach((device, index) => {
        // Filter out targets that are not compatible with the package
        device.installedTargets = this.installedTargetsResponse[device.uuid].filter((target) => {
          return this.package.hardwareIds.includes(target.hardwareId);
        });
        device.ecus = device.installedTargets.map((target) => {
          const deviceHardwareId = target.hardwareId;
          const packageHardwareIds = this.package.hardwareIds;
          return {
            ...target,
            id: target.ecuId,
            image: target,
            package: this.package,
            deviceHardwareId,
            packageHardwareIds,
          };
        });
        installedTargets = installedTargets.concat(device.ecus);
      });
      // Filter out devices that do not have any valid component
      devices = devices.filter((device) => device.installedTargets && device.installedTargets.length > 0);

      // Set the package to the selected ecus
      let selectedEcus = installedTargets.map((ecu) => {
        const deviceHardwareId = ecu.hardwareId;
        delete ecu.hardwareId; // Remove the hardwareId from the ecu
        return {
          ...ecu,
          image: ecu,
          package: this.package,
          deviceHardwareId,
          packageHardwareIds: this.package.hardwareIds,
          hardwareIds: this.package.hardwareIds,
        };
      });

      let actionRouteData, actionLabel;
      if (this.selectedDevice) {
        actionRouteData = {
          name: 'device-detail',
          params: {
            deviceId: this.selectedDevice.uuid,
          },
        };
        actionLabel = 'Go to device';
      } else {
        actionRouteData = {
          name: 'fleet-detail',
          params: {
            fleetId: this.selectedFleet.id,
          },
        };
        actionLabel = 'Go to fleet';
      }
      this.payload = {
        update: {
          devices,
        },
        selectedEcus,
        onUpdateCompleteActionData: {
          action: () => {
            this.close();
          },
        },
        onCancelPendingUpdateSuccessActionData: {
          action: () => {
            // Set the deviceStatus of the selected device to UpToDate
            if (this.selectedDevice) {
              this.selectedDevice.deviceStatus = 'UpToDate';
            } else if (this.selectedFleet) {
              this.selectedFleet.devices.values.forEach((device) => {
                device.deviceStatus = 'UpToDate';
              });
            }
          },
        },
        updateRetryActionData: {
          action: (updateViewComponent) => {
            updateViewComponent.close && updateViewComponent.close();
          },
        },
        onUpdateCompleteUserActionData: {
          routeData: actionRouteData,
          label: actionLabel,
        },
      };
    },
    async getUserDeviceCount() {
      this.loadingDeviceCount = true;
      const deviceTotal = await this.fetchDeviceCount({});
      this.userDeviceCount = deviceTotal;
      this.loadingDeviceCount = false;
      return deviceTotal;
    },
    queryDevices() {
      this.loadingDevices = true;
      this.fetchDevices({
        filter: this.deviceFilter,
        sort: {
          CreatedAt: 'asc',
        },
        limit: 5,
        offset: 0,
        storeResult: false,
        withPaginationResponse: true,
      })
        .then((data) => {
          this.filteredDevices = data.values;
          this.totalDevices = data.total;
        })
        .finally(() => {
          this.loadingDevices = false;
        });
    },
    queryFleets() {
      this.loadingFleets = true;
      this.fetchFleets({
        filter: this.fleetFilter,
        sort: {
          CreatedAt: 'asc',
        },
        limit: 5,
        offset: 0,
        storeResult: false,
        withPaginationResponse: true,
      })
        .then((data) => {
          this.fleets = data.values;
          this.totalFleets = data.total;
        })
        .finally(() => {
          this.loadingFleets = false;
        });
    },

    submit() {
      this.preparePayload();
      this.$events.$emit('dialogs:create-device-update:request', this.payload);
    },
  },
  mounted() {
    this.getUserDeviceCount().then(() => {
      if (this.userDeviceCount > 0) {
        this.queryDevices();
        this.queryFleets();
      }
    });
  },
};
</script>
