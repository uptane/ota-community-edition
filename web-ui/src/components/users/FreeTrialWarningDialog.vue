<template>
  <q-dialog max-width="600px" v-model="deviceLimitWarning">
    <q-card>
      <q-card-section>
        <div class="text-h6">Device Limit Reached</div>
      </q-card-section>

      <q-card-section class="q-pt-none row">
        <div class="col-auto q-pr-md">
          <q-icon name="warning" color="warning" size="lg"></q-icon>
        </div>
        <div class="col">You reached the device limit number for the Maker account. Start your free trial period now and enjoy all the advantages of the Commercial account</div>
      </q-card-section>

      <q-card-actions align="right">
        <q-btn flat label="Remind me later" color="primary" class="q-mr-sm" v-close-popup />
        <request-premium-btn size="md" :label="'Start Free Trial'" v-close-popup Z></request-premium-btn>
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import { mapGetters, mapActions } from 'vuex';
import { FREE_TIER_DEVICE_LIMIT } from '../../constants';
import ApiService from '../../services/api.service';
import RequestPremiumBtn from '../common/RequestPremiumBtn.vue';
import { map } from '@amcharts/amcharts4/.internal/core/utils/Iterator';
export default {
  components: { RequestPremiumBtn },
  name: 'FreeTrialWarningDialog',
  props: {
    value: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      deviceTotal: 0,
    };
  },
  methods: {
    ...mapActions({
      fetchDeviceCount: 'devices/fetchDeviceCount',
    }),
    async getDeviceCount() {
      const deviceTotal = await this.fetchDeviceCount({ additionalQueries: { hibernated: false } });
      this.deviceTotal = deviceTotal;
    },
  },
  computed: {
    ...mapGetters({
      userSettings: 'ui/userSettings',
      user: 'ui/user',
      isCommercialUser: 'users/isCommercialUser',
      isGuestAccess: 'organizations/isGuestAccess',
    }),
    user_settings() {
      return this.userSettings || {};
    },
    deviceLimitWarning: {
      get() {
        return (
          this.show &&
          (this.deviceTotal >= FREE_TIER_DEVICE_LIMIT &&
            !this.isCommercialUser &&
            // also check if user is not in shared account
            !this.isGuestAccess)
        );
      },
      set(val) {
        this.show = val;
      },
    },
    show: {
      get() {
        return this.value;
      },
      set(val) {
        this.$emit('input', val);
      },
    },
  },
  mounted() {
    if (!this.user || !this.user.deviceProvisionData) {
      this.getDeviceCount();
    }
  },
};
</script>

<style></style>
