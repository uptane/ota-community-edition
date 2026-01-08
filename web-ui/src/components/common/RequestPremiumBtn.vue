<template>
  <div>
    <q-dialog content-class="shadow-5" v-model="showPremiumPromptSuccess">
      <q-card class="lighter-card p-3 w-100 mxw-45em">
        <div class="row flex-center">
          <div class="col-auto mr-1 text-positive">
            <q-icon size="4rem" name="check_circle" />
          </div>
          <div class="col-auto">
            We have received your request and we will contact soon with next steps
          </div>
        </div>
        <div class="text-center">
          <q-btn v-close-popup flat color="primary" @click="$emit('dismiss', true)"> Close</q-btn>
        </div>
      </q-card>
    </q-dialog>
    <q-dialog content-class="shadow-5" v-model="showPremiumPromptError">
      <q-card class="lighter-card p-3 w-100 mxw-35em">
        <div class="row flex-center">
          <div class="col-auto mr-1 text-warning">
            <q-icon size="4rem" name="warning" />
          </div>
          <div class="col-auto">
            We are unable to send your request at this time. <br />
            Please try again in a moment
          </div>
        </div>
        <div class="text-center">
          <q-btn v-close-popup flat @click="$emit('dismiss', true)" color="primary"> Close</q-btn>
        </div>
      </q-card>
    </q-dialog>
    <q-btn @click="requestPremiumService" color="primary" :flat="flat" :size="size" :disabled="sendingPremiumRequest" :loading="sendingPremiumRequest"
      >{{ label }}
      <template v-slot:loading>
        <q-spinner-hourglass class="on-left" />
        Sending your request...
      </template>
    </q-btn>
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex';
export default {
  name: 'RequestPremiumBtn',
  props: {
    size: {
      type: String,
      default: 'md',
    },
    flat: {
      type: Boolean,
      default: false,
    },
    label: {
      type: String,
      default: 'Activate my commercial tier free trial',
    },
  },
  data() {
    return {
      sendingPremiumRequest: false,
      showPremiumPromptError: false,
      showPremiumPromptSuccess: false,
    };
  },
  computed: {
    ...mapGetters({
      user: 'ui/user',
    }),
  },
  methods: {
    ...mapActions({
      createLead: 'users/createLead',
    }),
    async requestPremiumService() {
      this.$events.$emit(`dialogs:commercial-request-form:open`, {});
    },
  },
};
</script>
