<template>
  <div class="p-0 pl-2 pr-0">
    <div class="row">
      <div class="col text-h5 text-center mb-1" v-if="!isLockbox">Scheduled Update Report</div>
      <div class="col-auto">
        <q-btn v-close-popup @click.native="$emit('close', {})" icon="close" flat> </q-btn>
      </div>
    </div>
    <div class="q-mb-sm q-ml-lg text-1" v-if="isError">
      <div class="flex p-1">
        <div class=""><q-icon size="1.5rem" class="q-mr-md" name="error" color="negative"></q-icon> <span>Unable to schedule update</span></div>
        <p class="w-90"><span class="opacity-40">Reported error: </span> <span class="text-body2" v-html="errorMessage"></span></p>
      </div>
    </div>
    <div class="q-mb-sm q-ml-lg text-1" v-else>
      <div class="flex  p-1">
        <q-icon size="1.5rem" class="q-mr-md" name="check_circle" color="positive"></q-icon>
        <span class=""> Update scheduled successfully</span>
      </div>
    </div>
  </div>
</template>

<script>
import { UPDATE_ERROR_CODES } from '../../constants';
import { mapMutations } from 'vuex';

export default {
  name: 'UpdateAssignmentReport',
  props: {
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
    isLockbox: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      scheduledUpdateExists: null,
    };
  },
  computed: {
    isError() {
      let err = this.responseData.code && this.responseData.code == 'update_schedule_error';
      if (err) {
        this.checkIfcheduledUpdateExists();
      }
      return err;
    },

    existingUpdateTime() {
      return this.$date.formatDate(this.scheduledUpdateExists && this.scheduledUpdateExists.status && this.scheduledUpdateExists.status.scheduledAt, 'YYYY-MM-DD hh:mm A');
    },

    errorMessage() {
      // The error response data has a format like this:
      /*
      { "code": "update_schedule_error", "description": "Invalid ecu status for scheduled update", "cause": [ { "scheduled_update_exists": { "scheduledUpdateId": "018e0a4e-0abb-7bf9-969a-8d6148113ab2" } } ], "errorId": "cf0bbc7d-6cf8-4bbf-bc2a-4ddbeb701244" }
      */

      const specificErrorCodes = (this.responseData.cause || []).map((cause) => Object.keys(cause)[0]);
      let errCodeData = UPDATE_ERROR_CODES.other;
      let message = '<div class="q-ma-sm">';
      if (specificErrorCodes.length > 0) {
        for (let i = 0; i < specificErrorCodes.length; i++) {
          if (UPDATE_ERROR_CODES[specificErrorCodes[i]]) {
            message += '<div class="ellipsis-3-lines text-negative"><span class="">&mdash;</span> ';
            errCodeData = UPDATE_ERROR_CODES[specificErrorCodes[i]];
            message += errCodeData.summary;
            // if (i < specificErrorCodes.length - 1) {
            //   message += '<br />';
            // }
            message += '</div>';
          } else {
            message += UPDATE_ERROR_CODES.other.summary.replace(new RegExp('{backend_error_response}', 'g'), `<strong>${this.responseData.description}</strong>`);
          }
        }
      } else {
        message += UPDATE_ERROR_CODES.other.summary.replace(new RegExp('{backend_error_response}', 'g'), `<strong>${this.responseData.description}</strong>`);
      }
      message += '</div>';
      return message;
    },
    successfulEcusByDevices() {
      return _.groupBy(this.successfulEcus, 'deviceUuid');
    },
  },
  methods: {
    ...mapMutations({
      updateSingleDevice: 'devices/updateSingleDevice',
    }),
    getLength(v) {
      return _.size(v);
    },
    checkIfcheduledUpdateExists() {
      const scheduledUpdateExists = ((this.responseData.cause || []).find((cause) => cause.scheduled_update_exists) || {}).scheduled_update_exists;
      const updateExists = ((this.responseData.cause || []).find((cause) => cause.ecu_assignment_exists) || {}).ecu_assignment_exists;
      const exists = updateExists || scheduledUpdateExists;
      if (exists) {
        this.$emit('on-existing-update', { isScheduledUpdate: !!scheduledUpdateExists, ...exists });
      }
      this.scheduledUpdateExists = exists;
    },
  },

  mounted() {
    if (!this.isError) {
      this.updateSingleDevice({ uuid: this.devices[0].uuid, updateScheduled: true });
    }
  },
};
</script>

<style></style>
