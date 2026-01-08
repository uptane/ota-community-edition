<template>
  <div>
    <div class="mt-1 text-h6 opacity-80 text-blue-grey" v-if="user.deviceProvisionData">
      <q-icon name="info" />
      <span v-if="user.deviceProvisionData.provisionedDevices && user.deviceProvisionData.provisionedDevices > 0">You have provisioned {{ user.deviceProvisionData.provisionedDevices === 1 ? 'one device' : user.deviceProvisionData.provisionedDevices + ' devices' }} so far.</span>
      <span v-else>You have not provisioned any device yet.</span>
      <!-- <span v-else>You have provisioned {{user.deviceProvisionData.provisionedDevices}} of {{userDeviceLimit}} devices allocated to you.</span> -->
    </div>
  </div>
</template>

<script>
export default {
  name: 'DeviceProvisionLimitInfo',
  components: {},
  data() {
    return {};
  },
  methods: {},
  computed: {
    user() {
      return this.$store.getters['ui/user'] || {};
    },
    userDeviceLimit() {
      const data = (this.user || {}).deviceProvisionData || {};
      return data.isCommercialUser ? 'unlimited' : data.standardDeviceLimit;
    },
  },
  mounted() {},
};
</script>
