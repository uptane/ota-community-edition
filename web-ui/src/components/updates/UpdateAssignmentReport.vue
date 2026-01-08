<template>
  <div class="p-0 pl-2 pr-0">
    <div class="row">
      <div class="col text-h5 text-center mb-1" v-if="!isLockbox">Update Assignment Report</div>
      <q-space />
      <div class="col-auto">
        <q-btn v-close-popup @click.native="$emit('close', {})" icon="close" flat> </q-btn>
      </div>
    </div>
    <div class="q-mb-md q-pb-md text-1" v-if="isLockbox && !responseData.isError">
      <div class="flex flex-center p-0">
        <q-icon size="3rem" class="q-mr-md" name="check_circle_outlined" color="positive"></q-icon>
        <span class=""> Lockbox Saved Successfully</span>
      </div>
    </div>
    <div class="mt-2 q-mb-sm q-ml-lg text-1" v-else-if="(isLockbox && responseData.isError) || (!isLockbox && responseData.isUnknownError)">
      <div class="flex  p-0">
        <div class="">
          <div class="text-negative text-2"><q-icon size="3rem" class="q-mr-md" name="error" color="negative"></q-icon> Unable to {{ isLockbox ? 'save lockbox' : 'initiate update' }}</div>
          <p class="ellipsis-2-lines w-90">
            <span class="opacity-40">Reported error: </span> <span class="text-body2">{{ responseData.errorMessage }}</span>
          </p>
        </div>
      </div>
    </div>
    <div class=" mxh-70vh overflow-y-auto" v-else>
      <div class="row" :key="deviceUuid + '_failed'" v-for="(ecus, deviceUuid) in failedEcusByDevices">
        <div class="col-12">
          <div class="text-1">
            <span class="">
              <q-icon size="1.3rem" class="q-mr-sm0" name="error" color="negative"></q-icon>
              &mdash; </span
            ><strong>{{ (deviceMap[deviceUuid] || {}).deviceName }}</strong>
          </div>

          <div class="q-mb-sm q-ml-lg" :key="ecu.id + '_failed'" v-for="ecu in ecus">
            <div class="">
              <span class="opacity-40">Component: </span><span>{{ ecu.hardwareId || ecu.deviceHardwareId || ecu.packageHardwareIds[0] || 'unknown' }}</span>
            </div>
            <div class="row">
              <div class="col-auto"><span class="opacity-40">Summary: </span> <span class=" text-negative" v-html="ecu.message"></span></div>
            </div>
          </div>
        </div>
      </div>
      <div class="row">
        <div class="col-12" :key="deviceUuid + '_successful'" v-for="(ecus, deviceUuid, index) in successfulEcusByDevices">
          <template v-if="index < 1 || showAllSuccessful">
            <div class="text-1">
              <span>
                <q-icon size="1.3rem" class="q-mr-sm0" name="check_circle" color="positive"></q-icon>
                &mdash; </span
              ><strong>{{ deviceMap[deviceUuid].deviceName }}</strong>
            </div>

            <div class="q-mb-sm q-ml-lg" :key="ecu.id + '_successful'" v-for="ecu in ecus">
              <div class="">
                <span class="opacity-40">Component: </span><span>{{ ecu.hardwareId || ecu.packageHardwareIds[0] || 'unknown' }}</span>
              </div>
              <div class="row">
                <div class="col-auto">
                  <span class="opacity-40">Summary: </span>
                  <span class=""> Update queued successfully</span>
                </div>
              </div>
            </div>
          </template>
        </div>
        <div class="col-12" v-if="!showAllSuccessful && getLength(successfulEcus) > 2">
          <q-btn color="positive" no-caps flat @click="showAllSuccessful = true">+ {{ getLength(successfulEcus) - 2 }} more devices</q-btn>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { mapGetters } from 'vuex';
import { UPDATE_ERROR_CODES } from '../../constants';
export default {
  name: 'UpdateAssignmentReport',
  props: {
    isLockbox: {
      type: Boolean,
      default: false,
    },
    responseData: {
      type: Object,
      default: () => ({}),
    },
    devices: {
      type: Array,
      default: () => [],
    },
    selectedEcus: {
      type: Array,
      default: () => [],
    },
  },
  data() {
    return {
      showAllSuccessful: false,
    };
  },
  computed: {
    ...mapGetters({
      //   devices: 'devices/devices'
    }),
    ecuMap() {
      let reduced = _.reduce(
        this.devices,
        (ecus, device) => {
          // Get the ecus that are selected for update
          const selectedEcus = _.intersectionBy(device.ecus, this.selectedEcus, (a) => {
            return a.hardwareId || a.deviceHardwareId || a.packageHardwareIds[0];
          });
          let all = ecus.concat(
            selectedEcus.map((a) => ({
              ...a,
              deviceUuid: device.uuid,
              deviceName: device.deviceName,
            })),
          );
          return all;
        },
        [],
      );
      let keyed = _.keyBy(reduced, 'id');
      return keyed;
    },
    deviceMap() {
      return _.keyBy(this.devices, 'uuid');
    },
    title() {
      const title = `Unable to initiate update${this.isMtu ? ' for some components' : ' '}`;
      return this.isLockbox ? `Unable to create Lockbox` : title;
    },
    totalNumberOfComponents() {
      return _.size(this.failedEcus) + _.size((this.responseData || {}).affected);
    },
    isMtu() {
      return this.totalNumberOfComponents > 1;
    },
    failedEcus() {
      const mapEcu = (ecuArray, deviceId) => {
        let mapped = _.map(ecuArray, (e, id) => {
          // If the ecu id is 'unknown' (which aparently dirrctor returns sometimes) and there's only one ecu in the device, then get the ecu id from the device
          let device = this.deviceMap[deviceId] || {};
          let detail = { ...e, id, ...this.ecuMap[id] };
          if (id === 'unknown') {
            // if there's only one ecu in the device, then get the ecu id from the device
            // otherwise, use the device id
            if ((device.ecus || []).length === 1) {
              id = (device.ecus || [])[0].ecuId;
              detail = { ...e, id, ...this.ecuMap[id] };
            } else {
              const hardwareId = this.selectedEcus.length === 1 ? this.selectedEcus[0].hardwareId : 'unknown';
              detail = { ...e, id: deviceId, deviceUuid: deviceId, deviceName: device.deviceName, hardwareId };
            }
          }
          return detail;
        });
        let keyed = _.keyBy(mapped, (e) => e.id || e.ecuId);
        return keyed;
      };
      const ecus = _.reduce(
        this.responseData.notAffected,
        (allEcus, ecus, deviceId) => {
          let mappedEcus = mapEcu(ecus, deviceId);
          return { ...allEcus, ...mappedEcus };
        },
        {},
      );
      return ecus;
    },
    successfulEcus() {
      return _.differenceBy(Object.values(this.ecuMap), Object.values(this.failedEcus), 'id');
    },
    failedEcusByDevices() {
      const getErrorMessage = (ecu) => {
        let code = ecu.code;
        let errCodeData = UPDATE_ERROR_CODES[code] || UPDATE_ERROR_CODES.other;
        const hardwareId = ecu.hardwareId || ecu.packageHardwareIds[0] || 'unknown';
        let message = errCodeData.summary
          .replace(new RegExp('{device_name}', 'g'), `<strong><u>${ecu.deviceName}</u></strong>`)
          .replace(new RegExp('{hardware_id}', 'g'), `<strong><u>${hardwareId}</u></strong>`)
          .replace(new RegExp('{backend_error_response}', 'g'), `<strong><u>${ecu.description}</u></strong>`);
        return message;
      };
      return _.groupBy(
        _.map(this.failedEcus, (ecu) => {
          return {
            ...ecu,
            message: getErrorMessage(ecu),
          };
        }),
        'deviceUuid',
      );
    },
    successfulEcusByDevices() {
      return _.groupBy(this.successfulEcus, 'deviceUuid');
    },
  },
  methods: {
    getLength(v) {
      return _.size(v);
    },
  },
};
</script>

<style></style>
