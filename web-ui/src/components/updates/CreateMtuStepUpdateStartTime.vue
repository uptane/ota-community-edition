<template>
  <div>
    <div class="text-h6 text-center pb-1">
      Select start time for this update
    </div>
    <div>
      <div class="flex flex-center">
        <div>
          <q-radio v-model="startImmediately" :val="true" label="Start immediately" class="q-mr-lg"></q-radio>
          <span>
            <q-radio v-model="startImmediately" :val="false" :disable="isFleetUpdate" label="Schedule for later"></q-radio>
            <tooltip v-if="isFleetUpdate">
              Scheduled update is not available for fleets at this time.
            </tooltip>
          </span>
        </div>
      </div>

      <div v-if="!startImmediately" class="flex flex-center q-pa-md q-mt-md">
        <div class="q-gutter-md row items-start">
          <q-date v-model="startTime" flat square :mask="mask" color="primary" class="q-ma-none" />
          <q-time v-model="startTime" flat square :mask="mask" color="primary" class="q-ma-none" />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { date as dateUtil } from 'quasar';
import Tooltip from '../common/Tooltip.vue';
export default {
  name: 'CreateMtuStepUpdateStartTime',
  components: { Tooltip },
  props: {
    immediate: {
      type: Boolean,
      required: true,
    },
    schedule: {
      type: String,
      default: dateUtil.formatDate(new Date(), 'YYYY-MM-DD hh:mm A'),
    },
    isFleetUpdate: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      mask: 'YYYY-MM-DD hh:mm A',
    };
  },
  computed: {
    startImmediately: {
      get() {
        return this.immediate;
      },
      set(value) {
        this.$emit('update:immediate', value);
      },
    },
    startTime: {
      get() {
        return dateUtil.formatDate(this.schedule, 'YYYY-MM-DD hh:mm A');
      },
      set(value) {
        this.$emit('update:schedule', value);
      },
    },
  },
};
</script>
