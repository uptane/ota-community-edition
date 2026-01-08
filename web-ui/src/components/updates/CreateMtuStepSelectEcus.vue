<template>
  <ecus-selector :value="value" :devices="devices" :lockbox="lockbox" :packageUpload="packageUpload" :allowComponentCreation="allowComponentCreation" :title="title" @input="$emit('input', $event)"></ecus-selector>
</template>

<script>
import _ from 'lodash';
import { mapActions, mapGetters } from 'vuex';
import EcusSelector from 'src/components/common/EcusSelector.vue';
export default {
  components: { EcusSelector },
  name: 'CreateMtuStepSelectEcus',
  props: {
    value: {
      type: Array,
      default: () => [],
    },
    devices: {
      type: Array,
      default: () => [],
    },
    lockbox: {
      type: Boolean,
      default: false,
    },
    packageUpload: {
      type: Boolean,
      default: false,
    },
    allowComponentCreation: {
      type: Boolean,
      default: false,
    },
    title: {
      type: String,
      default: 'Select components',
    },
  },
  data() {
    return {};
  },
};
</script>
