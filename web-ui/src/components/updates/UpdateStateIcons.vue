<template>
  <div class="" style="width: 20em;">
    <div class="row items-center">
      <div class="col-auto" @mouseout="stageMouseOver(1)" @mouseover="stageMouseOut(1)">
        <q-btn dense :outline="stage1Outline" round icon="check" size="1em" :color="stage1Color" loading class="flex flex-center">
          <template v-slot:loading>
            <q-icon name="schedule" size="2em" v-if="updateStatus.key === 'scheduled'" />
            <q-spinner-hourglass v-else-if="updateStatus.key === 'queued'" />
            <q-icon v-else-if="updateStatus.key === 'failed'" name="error" size="2em" />
            <q-icon v-else-if="updateStatus.key === 'success' || updateStatus.key === 'pending' || updateStatus.key === 'updating'" name="check" size="2em" />
          </template>
        </q-btn>
      </div>
      <div class="col-3">
        <q-linear-progress size="1px" stripe :indeterminate="updateStatus.key === 'pending'" color="positive" :value="linearProgress1Value" />
      </div>
      <div class="col-auto" @mouseout="stageMouseOver(2)" @mouseover="stageMouseOut(2)">
        <q-btn
          dense
          :outline="stage2Outline"
          round
          icon="check"
          size="1em"
          loading
          :color="stage2Color"
          :class="{
            'opacity-40': stage2Faded,
          }"
        >
          <template v-slot:loading>
            <q-spinner-hourglass v-if="updateStatus.key === 'pending'" />
            <q-icon v-else-if="updateStatus.key === 'failed'" name="error" size="2em" />
            <q-icon v-else-if="updateStatus.key === 'success' || updateStatus.key === 'updating'" name="check" size="2em" />
            <q-icon v-else name="more_horiz" size="2em" />
          </template>
        </q-btn>
      </div>
      <div class="col-3">
        <q-linear-progress size="1px" stripe :indeterminate="updateStatus.key === 'updating'" color="positive" :value="linearProgress2Value" />
      </div>
      <div class="col-auto" @mouseout="stageMouseOver(3)" @mouseover="stageMouseOut(3)">
        <q-btn
          dense
          :outline="stage3Outline"
          round
          icon="verified"
          size="1em"
          loading
          :color="stage3Color"
          :class="{
            'opacity-40': stage3Faded,
          }"
        >
          <template v-slot:loading>
            <q-spinner-hourglass v-if="updateStatus.key === 'updating'" />
            <q-icon v-else-if="updateStatus.key === 'failed'" name="error" size="2em" />
            <q-icon v-else-if="updateStatus.key === 'success'" name="check" size="2em" />
            <q-icon v-else name="more_horiz" size="2em" />
          </template>
        </q-btn>
      </div>
    </div>
  </div>
</template>

<script>
import Tooltip from '../common/Tooltip.vue';

export default {
  name: 'UpdateStatusIndicator',
  components: { Tooltip },
  props: {
    updateStatus: {
      type: Object,
      required: true,
    },
    device: {
      type: Object,
      required: true,
    },
    minimized: {
      type: Boolean,
      default: false,
    },
    size: {
      type: String,
      default: '1.5em',
    },
  },
  data() {
    return {
      stage1Outline: true,
      stage2Outline: true,
      stage3Outline: true,
      //   updateStatus: {
      //     summary: "Pending",
      //     key: "pending",
      //     message: "The update server is notified of the update and it is queued for the device.",
      //   },
    };
  },
  computed: {
    stage1Color() {
      if (this.updateStatus.key === 'queued') {
        return this.$q.dark.isActive ? 'grey-6' : 'grey-10';
      } else if (this.updateStatus.key === 'failed') {
        return 'negative';
      } else if (this.updateStatus.key === 'success' || this.updateStatus.key === 'pending' || this.updateStatus.key === 'updating') {
        return 'positive';
      } else if (this.updateStatus.key === 'scheduled') {
        return 'info';
      }
    },
    stage2Color() {
      if (this.updateStatus.key === 'queued' || this.updateStatus.key === 'pending') {
        return this.$q.dark.isActive ? 'grey-6' : 'grey-10';
      } else if (this.updateStatus.key === 'failed') {
        return 'negative';
      } else if (this.updateStatus.key === 'success' || this.updateStatus.key === 'updating') {
        return 'positive';
      }
    },
    stage3Color() {
      if (this.updateStatus.key === 'queued' || this.updateStatus.key === 'pending' || this.updateStatus.key === 'updating') {
        return this.$q.dark.isActive ? 'grey-6' : 'grey-10';
      } else if (this.updateStatus.key === 'failed') {
        return 'negative';
      } else if (this.updateStatus.key === 'success') {
        return 'positive';
      }
    },
    stage2Faded() {
      if (['queued', 'scheduled'].includes(this.updateStatus.key)) {
        return true;
      } else {
        return false;
      }
    },
    stage3Faded() {
      if (['queued', 'pending', 'scheduled'].includes(this.updateStatus.key)) {
        return true;
      } else {
        return false;
      }
    },

    linearProgress1Value() {
      if (['queued', 'failed', 'scheduled'].includes(this.updateStatus.key)) {
        return 0;
      } else {
        return 1.0;
      }
    },
    linearProgress2Value() {
      if (['queued', 'pending', 'failed', 'scheduled'].includes(this.updateStatus.key)) {
        return 0;
      } else {
        return 1.0;
      }
    },
  },
  methods: {
    stageMouseOut(num) {
      this[`stage${num}Outline`] = false;
    },
    stageMouseOver(num) {
      this[`stage${num}Outline`] = true;
    },
  },
};
</script>

<style></style>
