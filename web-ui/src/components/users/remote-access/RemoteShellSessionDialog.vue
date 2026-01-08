<template>
  <q-dialog ref="dialog" @hide="onDialogHide">
    <q-card class="q-dialog-plugin">
      <q-card-section class="">
        <div class="text-h6">Remote shell session</div>
      </q-card-section>
      <q-card-section class="">
        Copy the following command to your terminal to connect to the device:
        <div class="text-center">
          <span
            class="q-pa-sm rounded-borders"
            :class="{
              'bg-grey-3 ': !$q.dark.isActive,
              'bg-black ': $q.dark.isActive,
            }"
            >{{ sshCommand }} <copy-to-clipboard class="text-primary text-2 ml-1" :text="sshCommand"
          /></span>
        </div>
      </q-card-section>
      <q-card-actions align="right">
        <q-btn v-bind="cancel" @click="onCancelClick" />
        <q-btn v-bind="ok" @click="onOKClick" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import CopyToClipboard from '../../common/CopyToClipboard.vue';
export default {
  name: 'RemoteShellSessionDialog',
  components: { CopyToClipboard },
  props: {
    sshCommand: {
      type: String,
      default: '',
    },
    ok: {
      type: Object,
      default: () => ({}),
    },
    cancel: {
      type: Object,
      default: () => ({}),
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
      this.$emit('ok');
      this.hide();
    },

    onCancelClick() {
      this.hide();
    },
  },
};
</script>
