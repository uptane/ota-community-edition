<template>
  <q-dialog ref="dialog" @hide="onDialogHide" :persistent="persistent">
    <q-card class="q-dialog-plugin">
      <q-card-section class="row items-center">
        <div class="q-ml-sm">{{ title }}</div>
      </q-card-section>
      <q-card-section>
        <div>
          <q-select type="textarea" v-model="type" filled label="Key type" emit-value map-options :options="keyTypes" />
        </div>
        <div class="q-py-md"></div>
        <div>
          <p class="">
            <q-input filled type="textarea" v-model="content" focused tabindex="0" label="Paste signing key here:" />
          </p>
        </div>
      </q-card-section>

      <!-- buttons example -->
      <q-card-actions align="right">
        <q-btn label="Cancel" flat @click="onCancelClick" />
        <q-btn color="primary" label="OK" @click="onOKClick" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
export default {
  name: 'AddDelegationKeyDialog',
  props: {
    keyType: {
      type: String,
      default: 'rsa',
    },
    keyContent: {
      type: String,
      default: '',
    },
    persistent: {
      type: Boolean,
      default: false,
    },
    title: {
      type: String,
      default: 'Add Delegation Key',
    },
  },
  data() {
    return {
      keyTypes: [{ label: 'RSA', value: 'rsa' }, { label: 'ED25519', value: 'ed25519' }],
      type: 'rsa',
      content: '',
    };
  },
  methods: {
    show() {
      this.type = this.keyType;
      this.content = this.keyContent;
      this.$refs.dialog.show();
    },
    hide() {
      this.$refs.dialog.hide();
    },

    onDialogHide() {
      this.$emit('hide');
    },

    onOKClick() {
      //   this.$emit('ok')
      this.$emit('ok', { keyType: this.type, keyContent: this.content });

      // then hiding dialog
      this.hide();
    },

    onCancelClick() {
      // we just need to hide dialog
      this.hide();
    },
  },
};
</script>
