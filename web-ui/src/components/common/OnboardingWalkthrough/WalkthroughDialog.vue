<template>
  <div class="">
    <q-dialog v-model="showDialog" persistent>
      <q-card class="mxw-40em">
        <q-card-section class="row items-center q-pb-none">
          <div class="text-h6">Welcome to OTA Community Edition</div>
          <q-space />
          <q-btn icon="close" flat round dense v-close-popup @click="triggerClose" />
        </q-card-section>

        <q-card-section>
          <p class="text-body1">
            OTA Community Edition provides secure Over-The-Air software updates for your embedded devices.
          </p>
          
          <div class="text-h6 q-mt-md">Getting Started</div>
          <q-list>
            <q-item>
              <q-item-section avatar>
                <q-icon name="looks_one" color="primary" />
              </q-item-section>
              <q-item-section>
                <q-item-label>Provision Devices</q-item-label>
                <q-item-label caption>
                  Use the device provisioning credentials to connect your devices to OTA CE.
                </q-item-label>
              </q-item-section>
            </q-item>

            <q-item>
              <q-item-section avatar>
                <q-icon name="looks_two" color="primary" />
              </q-item-section>
              <q-item-section>
                <q-item-label>Upload Packages</q-item-label>
                <q-item-label caption>
                  Upload your software packages to the repository for distribution to devices.
                </q-item-label>
              </q-item-section>
            </q-item>

            <q-item>
              <q-item-section avatar>
                <q-icon name="looks_3" color="primary" />
              </q-item-section>
              <q-item-section>
                <q-item-label>Create Updates</q-item-label>
                <q-item-label caption>
                  Select packages and deploy updates to individual devices or fleets.
                </q-item-label>
              </q-item-section>
            </q-item>
          </q-list>
        </q-card-section>

        <q-card-section>
          <q-btn color="primary" label="Go to Devices" @click="goToDevices" class="q-mr-sm" />
          <q-btn flat label="Close" @click="triggerClose" />
        </q-card-section>
      </q-card>
    </q-dialog>
  </div>
</template>

<script>
import { OptionsService } from 'src/services/options.service';

export default {
  name: 'WalkthroughDialog',
  props: {},
  data() {
    return {
      showDialog: true,
    };
  },
  methods: {
    triggerClose() {
      this.$emit('close');
      OptionsService.saveOption('startGuideDisabled', true);
    },
    goToDevices() {
      this.$router.push({ name: 'devices' }).catch(() => {});
      this.triggerClose();
    },
  },
  mounted() {
    // Check if user has already seen the guide
    const guideSeen = OptionsService.getSavedOption('startGuideDisabled');
    if (guideSeen) {
      this.showDialog = false;
      this.$emit('close');
    }
  },
};
</script>
