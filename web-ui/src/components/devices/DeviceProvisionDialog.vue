<template>
  <q-dialog v-model="show" no-backdrop-dismiss @cancel="onCancel" @show="onShow" :maximized="$q.screen.lt.md" transition-show="slide-up" transition-hide="slide-down">
    <q-card
      class="p-2 "
      :class="{
        'w-80em mxw-80vw mxh-90vh': $q.screen.gt.sm,
      }"
    >
      <q-card-section class="no-shadow">
        <h4 class="m-0 ellipsis pr-5">
          Provision Device
        </h4>
        <q-btn icon="close" flat color="default" label="Dismiss" class="dismiss-btn cancel-btn absolute-top-right" @click="onCancel($event)" />
        <device-provision-limit-info></device-provision-limit-info>
      </q-card-section>
      <q-card-section class="">
        <device-provision @reset="show = false" @state-change="stateChanged($event)" ref="prov">
          <div v-if="loading" class="flex items-center justify-center row  p-1"><loader class="mr-1" /> <span>Please wait...</span></div>
        </device-provision>
      </q-card-section>
    </q-card>
  </q-dialog>
</template>

<script>
import Loader from '../loaders/Loader';
import DeviceProvision from './DeviceProvision';
import DeviceProvisionLimitInfo from './DeviceProvisionLimitInfo';

export default {
  name: 'ProvisionDeviceDialog',
  components: {
    Loader,
    DeviceProvision,
    DeviceProvisionLimitInfo,
  },
  props: {},
  data() {
    return {
      show: false,
      loading: true,
    };
  },
  methods: {
    onCancel($ev) {
      this.allDone($ev);
    },
    onShow() {
      this.$refs.prov.getProvisioningToken();
    },
    allDone(ev) {
      this.$refs.prov.resetAutoProvision(ev);
      this.show = false;
    },
    stateChanged(data) {
      this.loading = !data.tokenError && !data.token;
    },
  },
  computed: {
    // loading(){
    //   return (this.$refs.prov || {loading: true}).loading;
    // }
    user() {
      return this.$store.getters['ui/user'] || {};
    },
  },
  mounted() {
    this.$events.$on('dialogs:create-device:open', (data) => {
      Object.assign(this, data);
    });
  },
};
</script>
