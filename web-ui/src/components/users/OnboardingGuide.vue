<template>
  <div class=" mt-0 mb-2 ">
    <h4 class="m-0">Onboarding Guide</h4>
    <p class="mt-1">Click the button below to continue last step.</p>
    <div class="row q-item-tile label">
      <div class="col-auto text-bold">
        <q-btn color="primary" @click="continueLastStep">
          Continue Last Step
        </q-btn>
      </div>
    </div>
    <p class="mt-1">Click the button below to restart onboarding guide.</p>
    <div class="row q-item-tile label">
      <div class="col-auto text-bold">
        <q-btn color="primary" @click="restartOnboardingGuide">
          Restart Onboarding Guide
        </q-btn>
      </div>
    </div>
  </div>
</template>

<script>
import { mapActions } from 'vuex';
import { OptionsService } from 'src/services/options.service';

export default {
  name: 'OnboardingGuide',
  data() {
    return {};
  },
  methods: {
    ...mapActions({}),
    continueLastStep() {
      this.showOnboardingWalkthrough = true;
    },
    restartOnboardingGuide() {
      OptionsService.saveOption('onboardingGuideStep', 'landing');
      this.showOnboardingWalkthrough = true;
    },
  },
  computed: {
    showOnboardingWalkthrough: {
      get() {
        return this.$store.getters['ui/showOnboardingWalkthrough'];
      },
      set(val) {
        this.$store.commit('ui/setShowOnboardingWalkthrough', val);
      },
    },
  },
};
</script>
