<template>
  <q-dialog ref="dialog" @hide="onDialogHide">
    <q-card class="q-dialog-plugin">
      <q-card-section>
        <div class="text-h6">
          {{ title }}
        </div>
        <div class="q-py-md">
          Select the remote access session duration. The session will automatically end after the selected duration. The default duration is 90 minutes and the session can be ended manually at any time.
        </div>
      </q-card-section>
      <q-card-section>
        <div class="q-pb-md">
          <div class="text-subtitle2 ">Remote session duration ({{ durationLabel }})</div>
          <q-slider v-model="duration" :min="300" :max="43200" :step="300" snap label track-size="1.2em" thumb-size="2.2em" switch-label-side :label-value="durationLabel" color="primary" dense />
          <div class="row">
            <div class="col-auto">
              5 minutes
            </div>
            <div class="col"></div>
            <div class="col-auto">
              12 hours
            </div>
          </div>
        </div>
      </q-card-section>
      <q-card-actions align="right">
        <q-btn color="primary" label="Cancel" @click="onCancelClick" flat />
        <q-btn color="primary" label="Initiate Session" @click="onOKClick" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
export default {
  props: {
    title: {
      type: String,
      default: 'Dialog',
    },
    message: {
      type: String,
      default: 'Dialog message',
    },
    defaultDuration: {
      type: Number,
      default: 5400,
    },
  },

  data() {
    return {
      duration: this.defaultDuration,
    };
  },

  computed: {
    durationLabel() {
      const hours = Math.floor(this.duration / 3600);
      const minutes = Math.floor((this.duration % 3600) / 60);
      const hourLabel = hours > 1 ? 'hours' : 'hour';
      const hourLabelSpace = minutes > 0 ? ', ' : '';
      return (hours > 0 ? `${hours} ${hourLabel}${hourLabelSpace}` : '') + (minutes > 0 ? `${minutes} minutes` : '');
    },
  },

  methods: {
    show() {
      this.$refs.dialog.show();
    },
    hide() {
      this.$refs.dialog.hide();
    },

    onDialogHide() {
      this.$emit('hide');
    },

    onOKClick() {
      // this.$emit('ok')
      this.$emit('ok', { duration: this.duration });
      this.hide();
    },

    onCancelClick() {
      // we just need to hide dialog
      this.hide();
    },
  },
};
</script>
