<template>
  <q-dialog ref="modal" v-model="show" @ok="onOk" @cancel="onCancel" @show="onShow" @hide="onHide" no-backdrop-dismiss no-route-dismiss>
    <q-card
      class="w-90 p-1"
      :class="{
        'mxw-50em': updateSuccess || updateError || loadingData,
        'mxw-75em': !updateSuccess && !updateError && !loadingData,
      }"
    >
      <q-card-section class="no-shadow m-0 p-0 pt-1">
        <div v-if="loadingData && !loadedData">
          <h5 class="m-0 p-2 row">
            <div class="col-auto">
              <loader></loader>
            </div>
            <div class="col">{{ inProgressMessage }}</div>
            <div class="col-auto">
              <close-btn
                v-close-popup
                @click.native="onCancel"
                :class="{
                  'full-width  h-divide-top-dotted': $q.screen.lt.md,
                }"
              >
              </close-btn>
            </div>
          </h5>
        </div>
        <div class="row">
          <div class="col">
            <h5
              class="m-0 mb-2"
              :class="{
                'text-h5': $q.screen.gt.sm,
                'text-h6': $q.screen.lt.md,
              }"
              v-if="!loadingData && !updateError && !updateSuccess"
            >
              <div class="text-center ellipsis" v-if="isLockbox">
                <div v-if="isExistingUpdate"><span class="faded">Modify</span> {{ updateName }} <span class="faded"> Lockbox</span></div>
                <div v-else>Define Lockbox</div>
              </div>
              <div class="text-center ellipsis" v-else>
                <span class="faded">Initiate Update for </span>
                <span v-if="isFleetUpdate">{{ (selectedFleet || {}).groupName }} <span class="faded">&nbsp;Fleet</span> </span>

                <span v-if="!isFleetUpdate"
                  >{{ (selectedDevice || {}).deviceName }}
                  <span class="faded">&nbsp;Device</span>
                </span>
              </div>
            </h5>
          </div>

          <div v-if="!loadingData && !updateError && !updateSuccess && !updateResult" class="col-auto">
            <close-btn
              v-close-popup
              @click.native="onCancel"
              :class="{
                'full-width  h-divide-top-dotted': $q.screen.lt.md,
              }"
            >
            </close-btn>
          </div>
        </div>
        <template v-if="!loadingData && updateResult">
          <template v-if="startImmediately">
            <update-assignment-report :devices="updateDevices" :selected-ecus="selectedEcus" :response-data="updateResult" :is-lockbox="isLockbox" @close="onCancel"></update-assignment-report>
          </template>
          <template v-else>
            <scheduled-update-report :response-data="updateResult" :devices="updateDevices" :selected-ecus="selectedEcus" :is-lockbox="isLockbox" @close="onCancel" @on-existing-update="onExistingUpdate"></scheduled-update-report>
          </template>
        </template>
        <update-validation-hash v-if="hashWarningPackages.length > 0" :hashWarningPackages="hashWarningPackages" @cancel="resetHashWarning()" @ok="acceptHashWarning()"></update-validation-hash>

        <div class="row w-100" v-if="updateError">
          <q-space />
          <div class="col-auto">
            <q-btn
              v-if="scheduledUpdateExists"
              icon="replay"
              flat
              color="primary"
              label="Cancel Scheduled Update and Try Again"
              @click="
                cancelExistingScheduledUpdate();
                clearUpdateResult();
              "
              :class="{
                'full-width  h-divide-top-dotted': $q.screen.lt.md,
              }"
            />
            <q-btn
              v-else-if="(devicesWithPendingUpdate && devicesWithPendingUpdate.length > 0) || activeUpdateExists"
              icon="replay"
              flat
              color="primary"
              label="Cancel Pending Update and Try Again"
              @click="cancelPendingUpdateAndRetry"
              :class="{
                'full-width  h-divide-top-dotted': $q.screen.lt.md,
              }"
            />
            <q-btn
              v-else
              icon="replay"
              flat
              color="primary"
              label="Try again"
              @click="retry"
              :class="{
                'full-width  h-divide-top-dotted': $q.screen.lt.md,
              }"
            />
          </div>
          <div v-if="updateResult && onUpdateCompleteUserActionData" class="col-auto">
            <q-btn
              @click="callOnUpdateCompleteAction()"
              :class="{
                'full-width  h-divide-top-dotted': $q.screen.lt.md,
              }"
              color="primary"
            >
              {{ onUpdateCompleteUserActionData.label }}
            </q-btn>
          </div>
        </div>
        <update-validation-hash v-if="hashWarningPackages.length > 0" :hashWarningPackages="hashWarningPackages" @cancel="resetHashWarning()" @ok="acceptHashWarning()"></update-validation-hash>
      </q-card-section>

      <q-card-section v-if="loadingDeviceTemplate" class="w-100 mxw-30em mxh-60vh m-auto p-5"> <loader></loader> Please wait... </q-card-section>
      <q-card-section v-if="!loadingData && !updateError && !updateResult && hashWarningPackages.length === 0" class="m-0 p-0 h-auto">
        <q-banner class="p-1 text-center bg-negative text-white mb-1" v-if="message">{{ message }}</q-banner>
        <q-stepper v-model="currentStep" ref="stepper" header-class="text-bold" active-color="info" done-color="primary" flat class="mt-0 pt-0">
          <q-step :name="1" prefix="1" title="" :done="currentStep > 1">
            <create-mtu-step-select-ecus v-if="!loadingDevices" :devices="updateDevices" :lockbox="isLockbox" v-model="selectedEcus"></create-mtu-step-select-ecus>
            <div v-else>
              <list-loader></list-loader>
            </div>
          </q-step>
          <q-step :name="2" prefix="2" title="" :done="currentStep > 2">
            <create-mtu-step-select-packages ref="packageSelector" v-model="selectedPackages" :ecus="selectedEcus" :isLockbox="isLockbox" :isFleetUpdate="isFleetUpdate" @showBootloaderWarning="setShowBootloaderWarning"></create-mtu-step-select-packages>
          </q-step>
          <q-step :name="3" prefix="3" title="" :done="currentStep > 3" class="mxh-60vh">
            <template v-if="isLockbox">
              <div v-if="!isExistingUpdate" class="pl-2 pr-2 row justify-center">
                <div class="col mxw-40em">
                  <p class=" ">Enter a name for this update</p>
                  <p class="">
                    <q-input
                      v-model="updateName"
                      outlined
                      class="pb-1 w-100"
                      label="Update Name: "
                      :rules="[(val) => !!val || 'Update name cannot be empty', (val) => val.length <= 254 || 'Update name cannot be longer than 254 characters', (val) => val.match(/[^A-Za-z0-9_-]/) && 'Update name can only be alphanumeric with hyphen (-) and underscore (_)']"
                    />
                  </p>
                </div>
              </div>
              <div class="pl-2 pr-2 row justify-center">
                <div class="col mxw-40em">
                  <p class=" ">Select expiration date</p>
                  <p class="">
                    <q-input v-model="expirationDateView" outlined class="pb-1 w-100" label="Expiration Date: " readonly>
                      <q-popup-proxy>
                        <div>
                          <q-date v-model="expirationDateModel" flat landscape class="light-card" :no-unset="true" mask="M/D/YYYY" />
                        </div>
                      </q-popup-proxy>
                    </q-input>
                  </p>
                </div>
              </div>
            </template>
            <template v-else>
              <create-mtu-step-update-start-time :is-fleet-update="isFleetUpdate" :immediate.sync="startImmediately" :schedule.sync="startTime" @on-error="onErrorMessage"></create-mtu-step-update-start-time>
            </template>
          </q-step>
          <q-step :name="4" prefix="4" title="" :done="currentStep > 4">
            <create-mtu-step-confirm-selection :ecus="selectedEcus" :immediate="startImmediately" :schedule="startTime" :isLockbox="isLockbox" :updateName="updateName"></create-mtu-step-confirm-selection>
          </q-step>
          <template v-slot:navigation>
            <q-separator class="mb-2 opacity-50" />
            <q-stepper-navigation class="w-100">
              <div class="row justify-end">
                <div class="col-auto">
                  <q-btn v-if="currentStep > 1" flat @click="gotoPreviousStep()" label="Back" icon="chevron_left" class="q-mr-md ml-auto" />
                </div>
                <div class="col-auto">
                  <q-btn flat @click="gotoNextStep()" color="primary" :icon-right="nextIcon" :label="nextLabel" />
                </div>
              </div>
            </q-stepper-navigation>
          </template>
        </q-stepper>
      </q-card-section>
    </q-card>
    <q-dialog :value="true" v-if="showIncompatibilityWarning" @hide="compatibilityResult = null" persistent>
      <compatibility-warning @ack="overrideAndContinue" @close="message = 'Selected packages are not compatible.'" :compatibility-result="compatibilityResult"></compatibility-warning>
    </q-dialog>
  </q-dialog>
</template>

<script>
import Vue from 'vue';
import { mapActions, mapGetters } from 'vuex';

import { required } from 'vuelidate/lib/validators';
import PackageIcon from '../packages/PackageIcon';
import PackageKind from '../packages/PackageKind';
import Empty from '../common/Empty';
import { extend } from 'quasar';
import { DEVICE_UPDATE_EVENT_MESSAGE_TYPES } from '../../constants';
import CreateMtuStepSelectEcus from './CreateMtuStepSelectEcus.vue';
import CreateMtuStepSelectPackages from './CreateMtuStepSelectPackages.vue';
import CreateMtuStepConfirmSelection from './CreateMtuStepConfirmSelection.vue';
import CreateMtuStepUpdateStartTime from './CreateMtuStepUpdateStartTime.vue';
import ListLoader from '../loaders/ListLoader.vue';
import Loader from '../loaders/Loader.vue';
import CompatibilityWarning from './CompatibilityWarning.vue';
import UpdateAssignmentReport from './UpdateAssignmentReport.vue';
import ScheduledUpdateReport from './ScheduledUpdateReport.vue';
import UpdateValidationHash from './UpdateValidationHash.vue';
import Tooltip from '../common/Tooltip.vue';
import CloseBtn from '../common/CloseBtn.vue';

export default {
  name: 'CreateMultiTargetUpdate',
  components: {
    Loader,
    PackageIcon,
    PackageKind,
    Empty,
    CreateMtuStepSelectEcus,
    CreateMtuStepSelectPackages,
    CreateMtuStepConfirmSelection,
    CreateMtuStepUpdateStartTime,
    ListLoader,
    CompatibilityWarning,
    UpdateAssignmentReport,
    ScheduledUpdateReport,
    UpdateValidationHash,
    Tooltip,
    CloseBtn,
    Loader,
  },
  data() {
    return {
      currentStep: 1,
      selectedEcus: [],
      selectedPackages: [],
      message: '',
      update: {
        devices: [],
        fleet: null,
        package: null,
      },
      featureIntroOptionKey: 'showFeatureIntro_OTA_738',
      showPackageInfo: false,
      deviceTemplate: {},
      delegationType: 'all',
      selectedVersionBuildType: 'monthly',
      fromDeviceDetail: false,
      fromFleet: false,
      showUploadDetail: false,
      updateFilename: '',
      show: false,
      loadingDeviceTemplate: true,
      loadingDevices: false,
      loadingPackage: false,
      loadingFleet: false,
      loadingData: false,
      loadedData: false,
      updateError: null,
      updateResult: null,
      devicesWithPendingUpdate: null,
      updateSuccess: false,
      isFleetUpdate: false,
      passedDevices: [],
      selectedDevice: null,
      deviceList: [],
      fleetList: [],
      packageSource: null,
      toVersion: null,
      isLockbox: false,
      hardwareIds: [],
      updateName: '',
      isExistingUpdate: false,
      compatibilityResult: null,
      bypassCompatibilityCheck: false,
      expirationDate: new Date(new Date().setMonth(new Date().getMonth() + 12)),
      hashWarningPackages: [],
      hashWarningConfirmed: null,
      showBootloaderWarning: false,
      bootloaderWarningAcknowledged: false,

      onUpdateCompleteUserActionData: null,
      updateRetryActionData: null,
      onUpdateCompleteActionData: null,
      onCancelPendingUpdateSuccessActionData: null,
      startImmediately: true,
      startTime: this.$date.formatDate(new Date(), 'YYYY-MM-DD hh:mm A'),

      scheduledUpdateExists: false,
      existingScheduledUpdate: null,
      activeUpdateExists: null,

      onPackagesLoaded: () => {},
      onFleetsLoaded: () => {},
      onDevicesLoaded: () => {},
    };
  },
  validations: {
    update: {
      package: { required },
      deviceList: { required },
      version: { required },
    },
  },
  methods: {
    ...mapActions({
      fetchPackages: 'packages/fetchPackages',
      fetchDevices: 'devices/fetchDevices',
      fetchDevice: 'devices/fetchDevice',
      getUpdateEvents: 'devices/getUpdateEvents',
      requestMtuUpdate: 'updates/requestMtuUpdate',
      pushDeviceUpdate: 'devices/pushDeviceUpdate',
      fetchDirectorInfo: 'devices/fetchDirectorInfo',
      fetchFleet: 'fleets/fetchFleet',
      fetchFleets: 'fleets/fetchFleets',
      cancelUpdates: 'devices/cancelUpdates',
      cancelScheduledUpdate: 'updates/cancelScheduledUpdate',
      fetchHardwareIds: 'hardware/fetchHardwareIds',
      createLockbox: 'updates/createUpdate',
    }),
    gotoPreviousStep() {
      this.message = '';
      this.$refs.stepper.previous();
    },
    gotoNextStep() {
      // In the first step, we need to make sure at least one component is selected
      if (this.currentStep === 1) {
        if (!this.selectedEcus || !this.selectedEcus.length) {
          this.message = 'You must select at leat one device component to continue';
          return;
        }
      }
      // In the second step, we need to make sure each selected component has a package assigned
      if (this.currentStep === 2) {
        if (this.selectedEcus.some((a) => !a.package)) {
          if (this.$refs.packageSelector.selectNextEcuWithEmptyPackage()) {
            this.message = 'Each selected component must be assigned a package to continue';
          }
          return;
        }
        const compatibilityResult = this.checkPackageCompatibility();
        if (!compatibilityResult.allCompatible) {
          this.compatibilityResult = compatibilityResult;
          return;
        }
      }

      // In the third step and it's a lockbox update, we need to make sure the user has entered a name for the update and that the name is valid
      if (this.currentStep === 3 && this.isLockbox) {
        if (!this.updateName) {
          this.message = 'You must enter a name for this update';
          return;
        }
        if (this.updateName.length > 254) {
          this.message = 'Update name cannot be longer than 254 characters';
          return;
        }
        if (this.updateName.match(/[^A-Za-z0-9_-]/)) {
          return (this.message = 'Update name can only be alphanumeric with hyphen (-) and underscore (_)');
        }
      } else if (this.currentStep === 3 && !this.isLockbox) {
        if (!this.startImmediately) {
          if (new Date(this.startTime).getTime() < Date.now()) {
            return (this.message = 'To schedule an update, the start time must be in the future');
          }
        }
      }

      if (this.showBootloaderWarning && !this.bootloaderWarningAcknowledged) {
        this.showBootloaderWarningDialog();
        return;
      }
      this.continueToNextStep();
    },
    findPackageWithHash(hash) {
      return this.packagesByHash[hash];
    },
    findPackageById(id) {
      return this.packagesById[id];
    },
    checkPackageCompatibility() {
      const compatibilityReport = { allCompatible: true, incompatibilities: {} };
      const secondaries = this.selectedEcus.filter((e) => !e.primary);
      const secondariesWithCompat = secondaries.filter((e) => e.package.compatibleWith && e.package.compatibleWith.length > 0);

      if (secondariesWithCompat.length < 1) {
        return compatibilityReport;
      }
      let primaries = this.selectedEcus.filter((e) => e.primary);

      // flag to indicate if we have a primary component
      let secondaryOnly = primaries.length === 0;
      // get all installed primary packages
      const installedPrimaries = this.updateDevices.reduce((ecus, device, index) => {
        return ecus.concat(
          (device.ecus || [])
            .filter((e) => e.primary)
            .map((m) => {
              return { ...(this.findPackageById(m.image.filepath) || {}), hash: m.image.hash.sha256, deviceInfo: { uuid: device.uuid, name: device.deviceName } };
            }),
        );
      }, []);

      // get all installed primary packages if we have a secondary-only update otherwise get selected primaries
      const primariesPackages = secondaryOnly
        ? installedPrimaries
        : primaries.map((p) => {
            return {
              ...p.package,
              hash: p.package.hashes.sha256,
              hardwareId: p.hardwareId,
            };
          });
      // for each secondary package with compatibility defined, check if it is incompatible with any of the primary packages
      secondariesWithCompat.forEach((sec) => {
        // the signature for _.differenceWith is: (mainArray, otherArray, [iteratee=_.identity])
        const incompatibilities = _.differenceWith(primariesPackages, sec.package.compatibleWith, (a, b) => {
          return (a.sha256 || a.hash) === b.sha256;
        });

        if (incompatibilities.length > 0) {
          compatibilityReport.allCompatible = false;
          compatibilityReport.secondaryOnly = secondaryOnly;
          compatibilityReport.incompatibilities[sec.package.filepath] = incompatibilities;
        }
      });
      return compatibilityReport;
    },
    continueToNextStep() {
      this.message = '';
      const lastStep = 4; // this.isLockbox ? 4 : 3;
      if (this.currentStep === lastStep) {
        return this.beginUpdateRequest();
      }
      this.$refs.stepper.next();
    },
    overrideAndContinue(skippedCheck) {
      this.continueToNextStep();
    },
    showBootloaderWarningDialog() {
      this.$q
        .dialog({
          title: 'Confirm to continue',
          message: 'Are you sure you want to continue this major release update?',
          cancel: {
            label: `I'm aware, I want to proceed`,
            color: 'primary',
            outline: true,
          },
          ok: {
            label: `Cancel`,
            color: 'primary',
          },
          persistent: true,
        })
        .onOk(() => {})
        .onCancel(() => {
          this.acknowledgeBootloaderWarning();
        });
    },
    acknowledgeBootloaderWarning() {
      this.bootloaderWarningAcknowledged = true;
      this.continueToNextStep();
    },
    onErrorMessage(message) {
      this.message = message;
    },
    onOk() {
      this.beginUpdateRequest();
    },
    onCancel() {},
    onShow() {
      if (this.isFleetUpdate) {
        this.fetchFleetList();
      } else {
        this.fetchDeviceList();
      }
      this.fetchDeviceTemplateDetail();
      this.startTime = this.$date.formatDate(new Date(), 'YYYY-MM-DD hh:mm A');
    },
    onHide() {
      this.allDone();
    },
    makeCopy(e) {
      return JSON.parse(JSON.stringify(e || []));
    },

    fetchDeviceList() {
      const prepareDeviceList = () => {
        this.deviceList = this.devices;
        this.onDevicesLoaded(this.deviceList, this);
      };
      if (this.devices && this.devices.length > 0) {
        prepareDeviceList();
        return;
      }
      this.loadingDevices = true;
      this.fetchDevices()
        .then((list) => {
          prepareDeviceList();
          this.loadingDevices = false;
        })
        .catch((err) => {
          this.loadingDevices = false;
        });
    },
    fetchFleetList() {
      const prepareFleetList = () => {
        this.fleetList = this.fleets;
        this.loadingFleet = false;
        this.onFleetsLoaded(this.fleetList, this);
      };
      if (this.fleets && this.fleets.length > 0) {
        prepareFleetList(this.fleetList);
        return;
      }
      this.loadingFleet = true;
      this.fetchFleets()
        .then((list) => {
          prepareFleetList();
        })
        .catch((err) => {
          this.loadingFleet = false;
        });
    },
    checkForPendingUpdate(callback) {
      const latestUpdateEvent = (deviceUuid) => {
        // updateEvents was pre-sorted in decending order by date received before being stored
        let deviceEvents = (this.updateEvents || {})[deviceUuid] || [];
        // getting the first item in the array means getting the most recent event
        let latestEvent = deviceEvents[0];
        return latestEvent;
      };
      const isPendingUpdate = (device) => {
        const event = latestUpdateEvent(device.uuid);
        const eventId = ((event || {}).eventType || {}).id;
        return device.deviceStatus === 'Outdated' && (!eventId || eventId === DEVICE_UPDATE_EVENT_MESSAGE_TYPES.EcuInstallationCompleted);
      };
      const pendingUpdateDevices = [];
      this.fetchAllEventsForReferencedDevices();

      this.update.devices.forEach((device) => {
        if (isPendingUpdate(device)) {
          pendingUpdateDevices.push(device);
        }
      });
      if (pendingUpdateDevices.length) {
        this.promptForPendingUpdate(pendingUpdateDevices, callback);
      } else {
        callback();
      }
    },
    promptForPendingUpdate(pendingUpdateDevices, callback) {
      let title,
        message = '<div class="mb-2 p-0">',
        prompt;
      if (pendingUpdateDevices.length > 1) {
        title = `There are pending updates for the following devices:`;
        prompt = `Do you want to cancel the updates  before you start a new one?`;
      } else {
        prompt = `Do you want to cancel the update before you start a new one?`;
        title = `There's a pending update for this device:`;
      }
      pendingUpdateDevices.forEach((device, index) => {
        if (index < 3) {
          message += `<div> • ` + device.deviceName + `</div>`;
        }
        if (index == 3 && pendingUpdateDevices.length > 4) {
          message += `<div> ... </div>`;
        }
      });
      message += `</div> <p>` + prompt + `</p>`;
      setTimeout(() => {
        this.$events.$emit('dialogs:confirm:open', {
          title,
          message,
          htmlMessage: true,
          color: 'default',
          icon: 'block',
          yesFlat: true,
          yesClass: 'proceed',
          yesLabel: 'Yes',
          yesColor: 'secondary',
          noLabel: 'No',
          yesAction: () => {
            this.cancelUpdates({ deviceUuids: pendingUpdateDevices.map((d) => d.uuid) })
              .then((data) => {})
              .catch((err) => {})
              .finally(() => {
                callback();
              });
          },
          noFlat: true,
          noAction: () => {
            callback();
          },
        });
      }, 1000);
    },
    fetchAllEventsForReferencedDevices() {
      this.updateDevices.forEach((device) => {
        this.getUpdateEvents(device.uuid);
      });
    },
    cancelPendingUpdate() {
      return new Promise((resolve, reject) => {
        let deviceUuids;
        if (this.devicesWithPendingUpdate && this.devicesWithPendingUpdate.length > 0) {
          deviceUuids = this.devicesWithPendingUpdate.map((c) => c.deviceUuid);
        } else if (this.activeUpdateExists) {
          deviceUuids = this.update.devices.map((d) => d.uuid);
        }
        this.cancelUpdates({ deviceUuids })
          .then((data) => {
            this.onCancelPendingUpdateSuccessActionData && this.onCancelPendingUpdateSuccessActionData.action && this.onCancelPendingUpdateSuccessActionData.action(data);
            resolve(data);
          })
          .catch((err) => {
            reject(err);
          });
      });
    },
    cancelExistingScheduledUpdate() {
      this.cancelScheduledUpdate({ deviceUuid: this.update.devices[0].uuid, updateId: this.existingScheduledUpdate.scheduledUpdateId })
        .then((data) => {
          this.onExistingUpdate(null);
        })
        .catch((err) => {});
    },
    isValidUpdateData() {
      const errNotice = { message: '', color: 'negative' };

      if (!(this.update || {}).toPackage || (this.update || {}).toPackage.length < 2) {
        this.$v.update.package.$touch();
        this.$q.notify({ ...errNotice, message: 'You must select a package' });
        return false;
      }
      if (!(this.update || {}).toVersion || (this.update || {}).toVersion.length < 2) {
        this.$v.update.version.$touch();
        this.$q.notify({ ...errNotice, message: 'You must select version to update to' });
        return false;
      }
      if (!this.isFleetUpdate && (!(this.update || {}).devices || (this.update || {}).devices.length < 1)) {
        this.$q.notify({ ...errNotice, message: 'You must select at least one device from the list' });
        return false;
      }
      if (this.isFleetUpdate && (!(this.update || {}).fleet || (this.update || {}).fleet.length < 1)) {
        this.$q.notify({ ...errNotice, message: 'You must select a fleet from the list' });
        return false;
      }

      if (this.isFleetUpdate) {
        const fleet = this.update.fleet;
        this.update.devices = ((fleet.devices || {}).values || []).map((d) => this.devices.find((f) => f.uuid === d));
      }
      // if(!isCompatibleHardware()){
      //   this.$q.notify({ ...errNotice, message: "Selected update is not compatible with this hardware" });
      //   return false;
      // }
      return true;
    },
    getFinalUpdatePayload() {
      if (this.isLockbox) {
        const values = {};

        this.selectedEcus.forEach((ecu) => {
          // If ecu has hardwareIds property, use that, otherwise use the hardwareId property. If the hardwareId is an array, use that, otherwise make it an array
          const hardwareIds = ecu.hardwareIds || Array.isArray(ecu.hardwareId) ? ecu.hardwareId : [ecu.hardwareId];
          values[ecu.package.filepath] = {
            hashes: ecu.package.hashes,
            length: ecu.package.targetLength,
            custom: {
              hardwareIds,
            },
          };
        });
        return { expiresAt: new Date(this.expirationDate).toISOString(), values };
      }
      const deviceEcus = this.update.devices.reduce((ecus, device) => {
        return ecus.concat(
          (device.ecus || []).map((m) => {
            return {
              ...m,
              deviceUuid: device.uuid,
              deviceName: device.deviceName,
            };
          }),
        );
      }, []);

      // If the user has selected specific ecus, use those, otherwise use all ecus for the selected devices. This is needed to handle the use case where the update is initiated from package detail page
      if (!this.selectedEcus || !this.selectedEcus.length) {
        this.selectedEcus = deviceEcus;
      }
      return { ecus: this.selectedEcus, updateDevices: this.update.devices.map((device) => device.uuid) };
    },
    resetHashWarning() {
      this.hashWarningConfirmed = null;
      this.hashWarningPackages = [];
    },
    acceptHashWarning() {
      console.log('acceptHashWarning');
      this.hashWarningConfirmed = true;
      this.hashWarningPackages = [];
      this.beginUpdateRequest();
    },
    validateInstalledPackagesWithSameVersionButDifferentHash() {
      // OTA-1200 - Front-end check against sending an update to a device that will cause problems
      this.hashWarningPackages = [];
      this.update.devices.forEach((device) => {
        device.ecus.forEach((ecu) => {
          const selectedEcu = this.selectedEcus.find((e) => e.id === ecu.id);
          if (selectedEcu && selectedEcu.package.filepath === ecu.image.filepath && selectedEcu.package.hash !== (ecu.image.hash || {}).sha256) {
            this.hashWarningPackages.push({
              deviceName: device.deviceName,
              filepath: selectedEcu.package.filepath,
            });
          }
        });
      });
      this.hashWarningConfirmed = this.hashWarningPackages.length === 0;
      return this.hashWarningConfirmed;
    },
    beginUpdateRequest() {
      const done = () => {
        this.loadedData = true;
        this.loadingData = false;
        this.updateSuccess = true;
        this.onUpdateCompleteActionData && this.onUpdateCompleteActionData.action && this.onUpdateCompleteActionData.action(this.updateResult, this);
        this.$events.$emit('update:request-successful', {});
      };
      const updateData = this.getFinalUpdatePayload();
      let finalActionPromise;
      if (this.isLockbox) {
        finalActionPromise = this.createLockbox({ updateName: this.updateName, update: updateData });
      } else {
        if (this.hashWarningConfirmed === null) {
          if (!this.validateInstalledPackagesWithSameVersionButDifferentHash()) {
            return;
          }
        }
        finalActionPromise = this.requestMtuUpdate({
          updateData,
          schedulingData: {
            scheduled: this.startImmediately ? null : this.startTime,
            deviceUuid: this.isFleetUpdate ? null : this.update.devices[0].uuid,
            fleetId: this.isFleetUpdate ? this.update.fleet.id : null,
          },
        });
      }
      this.loadingData = true;
      finalActionPromise
        .then((updateResult) => {
          this.updateResult = updateResult;
          done();
        })
        .catch((error) => {
          logError('Device update err: ', error);
          this.loadingData = false;
          this.updateError = error;
          const isUnknownError = (error.data || {}).code === 'invalid_entity' || (error.status > 400 && error.status < 500);
          if (isUnknownError || _.isString(error.data)) {
            if (_.isString(error.data)) {
              this.updateResult = { isError: true, isUnknownError, errorMessage: `Status ${error.status}: ${error.data}` };
            } else {
              this.updateResult = { ...error.data, isError: true, isUnknownError, errorMessage: `Status ${error.status}: ${error.data.description}` };
            }
          } else {
            this.updateResult = error.data;
          }
          this.devicesWithPendingUpdate = _.map(this.updateResult.notAffected || {}, (components, deviceUuid) => ({ ...components, deviceUuid })).filter((components) => {
            return _.some(components, ['code', 'not_affected_running_assignment']);
          });
        })
        .finally(() => {
          this.loadingData = false;
        });
    },
    onUpdateRequest(updateData) {
      this.show = true;
      this.beginUpdateRequest();
    },
    fetchDeviceTemplateDetail() {
      const device = (this.passedDevices || [])[0];
      const fleetDeviceUuid = (((this.selectedFleet || {}).devices || {}).values || [])[0];
      const fleetDevice = this.devices.find((d) => d.uuid === fleetDeviceUuid);
      const sampleDevice = device || this.selectedDevice || fleetDevice;

      if (sampleDevice && !sampleDevice.hardwareType) {
        this.loadingDeviceTemplate = true;
        this.fetchDevice(sampleDevice.uuid, false)
          .then((device) => {
            this.deviceTemplate = device;
            this.loadingDeviceTemplate = false;
          })
          .catch((err) => {
            this.loadingDeviceTemplate = false;
          });
      } else {
        this.deviceTemplate = sampleDevice;
        this.loadingDeviceTemplate = false;
      }
    },
    formatVersion(l) {
      const maxLength = 35;
      const shortVersion = l.id.version.length > maxLength ? l.id.version.substring(0, maxLength) + '...' : l.id.version;
      return {
        label: l.commitSubject ? l.commitSubject : shortVersion,
        shortVersion: shortVersion,
        ...l,
      };
    },
    mapVersions(versions) {
      return (versions || []).map((l) => {
        return this.formatVersion(l);
      });
    },
    readFile(ev) {
      this.updateFilename = (this.$refs.updateFile.files[0] || {}).name;
    },
    allDone() {
      this.show = false;
      // setTimeout(() => {
      this.fromDeviceDetail = false;
      this.fromFleet = false;
      this.updateFilename = '';
      this.loadingDevices = false;
      this.progressBuffer = 10;
      this.loadingData = false;
      this.loadedData = false;
      this.updateError = null;
      this.updateResult = null;
      this.updateSuccess = false;
      this.selectedDevice = null;
      this.passedDevices = null;
      this.onPackagesLoaded = () => {};
      this.onFleetsLoaded = () => {};
      this.onDevicesLoaded = () => {};

      this.$v.update.package.$reset();
      this.$v.update.version.$reset();
      this.currentStep = 1;
      this.selectedEcus = [];
      this.message = '';
      this.updateName = null;
      this.isLockbox = false;
      this.isExistingUpdate = false;
      this.toVersion = null;
      this.hashWarningConfirmed = null;
      this.hashWarningPackages = [];
      this.showBootloaderWarning = false;
      this.bootloaderWarningAcknowledged = false;
      this.onUpdateCompleteUserActionData = null;
      this.updateRetryActionData = null;
      this.onUpdateCompleteData = null;
      this.onCancelPendingUpdateSuccessActionData = null;
      this.scheduledUpdateExists = false;
      this.existingScheduledUpdate = null;
      this.activeUpdateExists = null;

      this.startImmediately = true;
      this.startTime = this.$date.formatDate(new Date(), 'YYYY-MM-DD hh:mm A');

      this.update = {
        devices: [],
        fleet: null,
        package: {},
      };
      // }, 1500);
    },

    clearUpdateResult() {
      this.updateError = null;
      this.updateResult = null;
      this.currentStep = 2;
    },

    cancelPendingUpdateAndRetry() {
      this.cancelPendingUpdate().finally(() => {
        this.retry();
      });
    },
    retry() {
      this.clearUpdateResult();
      this.callRetryAction();
    },
    callRetryAction() {
      // Check if there is any addition action to call on retry
      if (this.updateRetryActionData && this.updateRetryActionData.action) {
        this.updateRetryActionData.action(this);
      }
    },
    setShowBootloaderWarning(show) {
      this.showBootloaderWarning = show;
    },
    callOnUpdateCompleteAction() {
      if (this.onUpdateCompleteUserActionData) {
        this.$router.push(this.onUpdateCompleteUserActionData.routeData);
        this.close();
      }
    },
    close() {
      this.show = false;
      this.onHide();
    },

    onExistingUpdate(existingUpdate) {
      if (existingUpdate.isScheduledUpdate) {
        this.scheduledUpdateExists = !!existingUpdate;
        this.existingScheduledUpdate = existingUpdate;
      } else {
        this.scheduledUpdateExists = false;
        this.existingScheduledUpdate = null;
        this.activeUpdateExists = existingUpdate;
      }
    },
  },
  mounted() {
    this.$events.$on('dialogs:create-lockbox:open', (data) => {
      extend(this, data);
      this.isLockbox = true;
      this.isExistingUpdate = !!data.updateName;
      this.fetchHardwareIds()
        .then((hids) => {
          this.hardwareIds = (hids || {}).values || [];
        })
        .catch((e) => {
          log('Lockboxes: Unable to fetch hardware ids');
        })
        .finally(() => {});
    });
    this.$events.$on('dialogs:create-device-update:open', async (data) => {
      extend(this, data);
      this.$events.$emit('dialogs:featureIntro:request', { key: this.featureIntroOptionKey });
      const fetchDirectorInfo = async () => {
        try {
          const ecuInfoArray = await Promise.all(
            this.update.devices.map((device, index) => {
              return this.fetchDirectorInfo(device.uuid);
            }),
          );
          this.update.devices = this.update.devices.map((device, index) => {
            return { ...this.update.devices[index], ecus: ecuInfoArray[index] };
          });
        } catch (err) {
          log('Device info error', err);
        } finally {
          this.loadingDevices = false;
        }
      };
      this.loadingDevices = true;
      if (this.isFleetUpdate) {
        try {
          const fleet = await this.fetchFleet({ id: this.update.fleet.id, forceFetch: true });
          this.$set(this.update, 'fleet', fleet);
        } catch (err) {
          log('Fleet info error', err);
        }
        try {
          const devices = await Promise.all(
            // Fetch each device with uuid that's in fleet.devices.values
            this.update.fleet.devices.map((uuid) => {
              return this.fetchDevice(uuid, false);
            }),
          );
          this.$set(this.update, 'devices', devices);
        } catch (err) {
          log('Device info error', err);
        } finally {
          this.checkForPendingUpdate(() => {
            this.show = true;
            fetchDirectorInfo();
          });
        }
      } else {
        this.show = true;
        this.checkForPendingUpdate(() => {
          fetchDirectorInfo();
        });
      }
    });
    this.$events.$on('dialogs:create-device-update:request', (data) => {
      extend(this, data);
      this.checkForPendingUpdate(() => {
        this.onUpdateRequest(data);
      });
    });
  },
  computed: {
    ...mapGetters({
      packages: 'packages/packages',
      packagesById: 'packages/packagesById',
      packagesByHash: 'packages/packagesByHash',
      devices: 'devices/devices',
      fleets: 'fleets/fleets',
      delegationTypeData: 'packages/delegationTypes',
      userSettings: 'ui/userSettings',
      updateEvents: 'devices/updateInstallationEvents',
    }),
    expirationDateView() {
      return this.expirationDate.toLocaleDateString();
    },
    expirationDateModel: {
      get() {
        return this.expirationDate.toLocaleString('en-US', { month: 'numeric', day: 'numeric', year: 'numeric' });
      },
      set(value) {
        this.expirationDate = new Date(value);
      },
    },
    showIncompatibilityWarning() {
      return this.compatibilityResult && !this.compatibilityResult.allCompatible && !this.bypassCompatibilityCheck;
    },
    inProgressMessage() {
      return this.isLockbox ? 'Saving lockbox...' : this.startImmediately ? 'Initiating update...' : 'Scheduling update...';
    },
    successMessage() {
      return this.isLockbox ? 'Lockbox created' : 'Update initiated';
    },
    failureMessage() {
      return this.isLockbox ? 'Unable to save Lockbox' : 'Unable to initiate update';
    },
    failureMessageDetail() {
      return this.isLockbox ? 'Please make sure the selected package is compatible with the selected component types' : `Please make sure the selected package is compatible with the selected ${!this.isFleetUpdate ? 'device' : 'fleet'}`;
    },
    updateDevices() {
      if (this.isLockbox) {
        const devices = this.hardwareIds
          .filter((e) => {
            return !(this.update.devices || []).find((f) => f.ecus[0].hardwareId == e);
          })
          .map((e) => {
            return { ecus: [{ hardwareId: e }] };
          })
          .concat(this.update.devices || []);
        return devices;
      }
      return this.makeCopy(this.update.devices);
    },
    delegationTypes() {
      return this.delegationTypeData.filter((a) => a.value != 'custom');
    },
    versionList() {
      const device = (this.passedDevices || [])[0];
      let versionList = null;
      if (!this.isFleetUpdate && device) {
        versionList = this.mapVersions(
          device.updates.map((a) => {
            return { ...a.custom, id: a.custom };
          }),
        );
      }
      const unfiltered = versionList || (this.update.toPackage || {}).versionList || [];
      const filtered = unfiltered.filter((a) => {
        const hw = a.hardwareIds || [];
        return hw.indexOf(this.deviceTemplate.hardwareType) !== -1 || hw.indexOf('docker-compose') !== -1;
      });
      if (filtered && filtered.length == 1) {
        this.toVersion = filtered[0];
      }
      return filtered;
    },
    selectedFleet() {
      return this.update.fleet;
    },
    nextIcon() {
      const lastStep = 4; // this.isLockbox ? 4 : 3;
      return this.currentStep === lastStep ? 'check' : 'chevron_right';
    },
    nextLabel() {
      const lastStep = 4; // this.isLockbox ? 4 : 3;
      return this.currentStep === lastStep ? 'Finish' : 'Continue';
    },
  },
  watch: {
    toVersion(n) {
      if (n) {
        Vue.set(this.update, 'toVersion', { ...this.packagesById[n.id] });
      }
    },
    selectedDevice(n) {
      if (n) {
        Vue.set(this.update, 'devices', [n]);
      } else {
        Vue.set(this.update, 'devices', []);
      }
    },
    selectedEcus(n) {
      this.message = '';
    },
    startTime(n) {
      if (!this.startImmediately) {
        // If the date is less than now, show an error message
        if (new Date(n).getTime() < Date.now()) {
          this.message = 'Start time cannot be in the past';
        } else {
          this.message = '';
        }
      }
    },
  },
};
</script>
