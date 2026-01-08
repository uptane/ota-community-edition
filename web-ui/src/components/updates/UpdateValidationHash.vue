<template>
  <div class="pl-2 pr-2 pb-2">
    <div class="text-h5 text-center">Error</div>
    <div class="mxh-70vh overflow-y-auto mb-2">
      <div class="row mt-2" :key="'hash-warning-' + idx" v-for="(warningPackage, idx) in hashWarningPackages">
        <div class="text-negative">
          Device <strong>{{ warningPackage.deviceName }}</strong> reports that <strong>{{ warningPackage.filepath }}</strong> is already installed, but the contents of the package on the device do not match the contents in the repository. This scenario is likely to trigger a bug in Torizon Core
          where the update client gets stuck in a loop. Recommended workaround: send a different update, then update it "back” to this package once the other update completes successfully.
        </div>
      </div>
    </div>
    <div class="row justify-end">
      <div class="col-auto">
        <q-btn flat color="primary" label="Back" icon="chevron_left" class="q-mr-md ml-auto" @click="$emit('cancel')" />
      </div>
      <div class="col-auto">
        <q-btn color="negative" icon-right="check" label="Ignore errors and continue" @click="acceptHashWarning()" />
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'UpdateValidationHash',
  props: {
    hashWarningPackages: {
      type: Array,
      default: () => [],
    },
  },
  methods: {
    acceptHashWarning() {
      this.$q
        .dialog({
          title: 'Confirm',
          message: 'Are you sure? This is likely to make your device impossible to update remotely.',
          ok: {
            color: 'negative',
            label: "Yes, I'm sure",
          },
          cancel: true,
          persistent: true,
        })
        .onOk(() => {
          this.$emit('ok');
        });
    },
  },
};
</script>
