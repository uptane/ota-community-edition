<template>
  <q-card class="mxw-40em mxw-60vw mxh-60vh mnw-30em overflow-y-auto p-1" v-touch-hold:5000.mouse="changeUserPath">
    <q-btn class="absolute-top-right m-1 z-top" flat round icon="close" @click="$emit('triggerClose', 'Closed via top close button')" />
    <q-card-section class="mb-0 pb-0">
      <div class="flex flex-center">
        <img src="statics/svg/welcome.svg" alt="Quasar Logo" class="q-mb-md rounded  p-1" style="border-radius: 50%;" />
      </div>
      <div class="text-h5 text-center pt-1">Let's get started!</div>
      <div class="text-center pt-2">
        <div class="text-1">
          Explore our guide or jump right into provisioning your first device.
        </div>
      </div>
    </q-card-section>
    <q-card-actions align="center" class="justify-center pt-2">
      <q-btn color="primary" no-caps class="q-px-lg" :label="startGuigeButtonLabel" @click="switchView('steps')" />
    </q-card-actions>
    <div class="text-center pt-1">
      <p class="text">
        * You can always resume this guide in the Account Information Page.
      </p>
    </div>
  </q-card>
</template>

<script>
export default {
  name: 'WalkthroughLanding',
  props: {},
  data() {
    return {
      useAltPath: false,
    };
  },
  methods: {
    switchView(view) {
      this.$emit('switchView', view);
    },
    changeUserPath() {
      this.useAltPath = !this.useAltPath;
      this.$emit('changeUserPath', this.useAltPath);
    },
  },
  computed: {
    startGuigeButtonLabel() {
      return this.useAltPath ? 'Start the guide **' : 'Start the guide';
    },
  },
  mounted() {},
};
</script>

<style></style>
