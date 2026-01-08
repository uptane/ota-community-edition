<template>
  <q-dialog v-model="show" no-backdrop-dismiss @ok="onOk" @cancel="onCancel" @show="onShow" @hide="onHide">
    <q-card class="p-2">
      <q-card-section class="no-shadow" v-if="!loading">
        <h4 class="m-0">
          {{ (device || {}).uuid ? 'Rename' : 'Create new' }} device
          <q-icon name="left"></q-icon>
        </h4>
      </q-card-section>

      <q-card-section class=" pb-2" v-if="!loading">
        <div>
          <q-input class="device-name" @blur="$v.newDeviceData.deviceName.$touch" @keyup.enter="onOk" outlined standout autofocus :error="$v.newDeviceData.deviceName.$error" label="Device name" v-model="newDeviceData.deviceName" />
        </div>
      </q-card-section>
      <q-card-section v-if="!loading">
        <div class="flex justify-end">
          <q-btn icon="close" flat color="default" label="Cancel" class="cancel-btn" v-close-popup @click="onCancel" />
          <q-btn class="continue-btn" icon="check" flat color="secondary" label="Continue" @click="onOk" />
        </div>
      </q-card-section>
      <q-card-section class="p-2" v-if="loading">
        <div class="no-shadow">
          <h6 class="m-0">
            {{ (device || {}).uuid ? 'Saving changes ...' : 'Creating new device ...' }}
            <loader></loader>
          </h6>
        </div>
      </q-card-section>
    </q-card>
  </q-dialog>
</template>

<script>
import { mapActions } from 'vuex';
import { required, minLength } from 'vuelidate/lib/validators';
import Loader from '../loaders/Loader';

export default {
  name: 'DeviceRenameDialog',
  components: {
    Loader,
  },
  props: {},
  data() {
    return {
      newDeviceData: {},
      name: '',
      id: '',
      show: false,
      device: {},
      loading: false,
    };
  },
  validations: {
    newDeviceData: {
      deviceName: { required, minLength: minLength(3) },
    },
  },
  methods: {
    ...mapActions({
      renameDevice: 'devices/renameDevice',
    }),
    onOk() {
      if (!(this.newDeviceData || {}).deviceName || (this.newDeviceData || {}).deviceName.length < 3) {
        this.$v.newDeviceData.deviceName.$touch();
        return this.$q.notify('Device name is required');
      }
      let promise;
      this.loading = true;
      this.$store.commit('ui/setDeviceInProcess', this.device);
      promise = this.renameDevice({
        id: this.newDeviceData.uuid,
        data: {
          deviceName: this.newDeviceData.deviceName,
          deviceType: this.newDeviceData.deviceType,
        },
      });

      promise
        .then((data) => {
          let message = `Device "${this.newDeviceData.deviceName}", has been created`;
          if (this.newDeviceData.uuid) {
            message = `Device has been renamed to "${this.newDeviceData.deviceName}"`;
            this.selectedDevice = this.newDeviceData;
            this.$store.commit('ui/setDeviceInProcess', null);
          }
          this.allDone();
          this.$events.$emit('devices:refresh');
          this.$q.notify({ message, color: 'positive' });
        })
        .catch((err) => {
          let message = '';
          if (this.newDeviceData.uuid) {
            message = `Unable to rename device "${this.device.deviceName}"`;
          } else {
            message = `Device creation failed: "`;
          }
          if (err.response.data.code === 'conflicting_device') {
            message = 'DeviceId or deviceName is already in use';
          }
          this.$q.notify({ message, color: 'negative' });
          this.loading = false;
          this.$store.commit('ui/setDeviceInProcess', null);
        });
    },
    onCancel() {},
    onShow() {
      this.newDeviceData = Object.assign({}, this.device);
    },
    onHide() {
      this.allDone();
    },
    allDone() {
      this.newDeviceData = {};
      this.show = false;
      setTimeout(() => {
        this.loading = false;
      }, 1500);

      this.$v.newDeviceData.deviceName.$reset();
    },
  },
  computed: {},
  mounted() {
    this.$events.$on('dialogs:rename-device:open', (data) => {
      Object.assign(this, data);
    });
  },
};
</script>
