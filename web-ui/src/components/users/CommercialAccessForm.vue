<template>
  <q-dialog :value="value" @input="$emit('input', $event)" persistent @show="resetForm" @hide="resetForm">
    <q-card class="w-90 mxw-40em p-2">
      <div v-if="success">
        <q-card-section>
          <div class="text-h5 text-center">Free Trial Activated</div>
        </q-card-section>
        <q-card-section>
          <div class="text-1 text-center flex flex-center"><q-icon class="text-positive mr-1" size="2rem" name="check_circle"></q-icon> <span class="opacity-60">Your commercial access free trial is now active.</span></div>
        </q-card-section>
        <q-card-actions class="flex flex-center">
          <q-btn flat v-close-popup>
            Close
          </q-btn>
        </q-card-actions>
      </div>
      <div v-else-if="error">
        <q-card-section>
          <div class="text-h5 text-center"><q-icon class="text-negative mr-1" size="2rem" name="info_outline"></q-icon> Free Trial Activation Failed</div>
        </q-card-section>
        <q-card-section>
          <div class="text-1 text-center flex flex-center"><span class="opacity-60">We are unable to activate your commercial access free trial at the moment, please contact us directly at</span> <a class="text-primary" href="mailto:sales@toradex.com" target="_blank">sales@toradex.com</a></div>
        </q-card-section>
        <q-card-actions class="flex flex-center">
          <q-btn flat v-close-popup>
            Close
          </q-btn>
        </q-card-actions>
      </div>
      <div v-else>
        <q-card-section>
          <div class="text-h5 text-center">Limited Offer</div>
        </q-card-section>
        <q-card-section>
          <div class="error-message text-center p-1 animated fadeIn" v-if="inputError">
            Please select and YES or NO below to continue.
          </div>
          <div class="text-1">
            <span class="opacity-60">{{ question }}</span>
            <span class="col-auto" style="display:inline-block;">
              <q-radio v-model="getInTouch" :val="question + ': YES'" :color="inputError ? 'negative' : 'default'" :keep-color="inputError" label="Yes" />
              <q-radio v-model="getInTouch" :val="question + ': NO'" :color="inputError ? 'negative' : 'default'" :keep-color="inputError" label="No" />
            </span>
          </div>
          <div class="text-center mt-3">
            <q-btn color="primary" class="mr-1" :loading="loading" @click="activatePremiumServiceTrial">
              Activate my 90-day trial
              <template v-slot:loading>
                <div class="row">
                  <div class="col-auto">
                    <q-spinner-hourglass class="on-left" />
                  </div>
                  <div class="col">
                    Processing...
                  </div>
                </div>
              </template>
            </q-btn>
            <q-btn flat v-close-popup v-if="!loading">
              Cancel
            </q-btn>
          </div>
        </q-card-section>
      </div>
    </q-card>
  </q-dialog>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
export default {
  props: {
    value: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      question: `I'd like to speed up my project's development. Get in touch with me for a free 1-hour training session`,
      getInTouch: null,
      loading: false,
      success: false,
      error: false,
      inputError: false,
    };
  },
  computed: {
    ...mapGetters({
      user: 'ui/user',
    }),
  },
  methods: {
    ...mapActions({
      createCommercialAccessLead: 'users/createCommercialAccessLead',
      activateCommercialAccessTrial: 'users/activateCommercialAccessTrial',
    }),
    resetForm() {
      this.getInTouch = null;
      this.success = false;
      this.error = false;
      this.inputError = false;
      this.loading = false;
    },
    activatePremiumServiceTrial() {
      this.loading = true;
      if (!this.getInTouch) {
        this.loading = false;
        this.inputError = true;
        return;
      }
      this.createCommercialAccessLead({
        description: this.getInTouch,
        email: this.user.email,
      })
        .then((a) => {
          this.activateCommercialAccessTrial()
            .then((a) => {
              this.success = true;
            })
            .catch((e) => {
              logError('Unable to activate Commercial Access for UUID ' + this.user.sub, e);
              this.error = true;
            })
            .finally((e) => {
              this.loading = false;
            });
        })
        .catch((e) => {
          logError('Unable to send RFQ for Commercial Access for UUID ' + this.user.sub, e);
          this.error = true;
        });
    },
    close() {
      this.$emit('input', false);
    },
  },
  watch: {
    getInTouch(val) {
      this.inputError = false;
    },
  },
};
</script>

<style></style>
