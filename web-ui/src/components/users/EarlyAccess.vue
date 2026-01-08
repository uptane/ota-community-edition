<template>
  <div class=" mt-0 mb-2 ">
    <h4 class="m-0">Enable Beta Features</h4>
    <div class="mt-1 mxw-50em">
      <div>
        <q-checkbox v-model="enableBetaFeatures" label="Enable beta features" class="ml-0" />
      </div>
      <span class="">
        If you enable this option, you will have access to platform features before they are released to the general public. Note that beta features may change or be withdrawn. We appreciate and encourage you to contact us with any feedback you might have during the beta period.
      </span>
    </div>
  </div>
</template>

<script>
import { mapActions } from 'vuex';
import { OptionsService } from 'src/services/options.service';

export default {
  name: 'EarlyAccess',
  data() {
    return {};
  },
  methods: {
    ...mapActions({}),
  },
  computed: {
    enableBetaFeatures: {
      get() {
        return OptionsService.getSavedOptionOrDefault('enableBetaFeatures', false);
      },
      set(val) {
        OptionsService.saveOption('enableBetaFeatures', val);
      },
    },
  },
};
</script>
