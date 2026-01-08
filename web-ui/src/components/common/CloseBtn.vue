<template>
  <q-btn icon-right="close" class="close-btn" flat color="default" :label="label">
    <tooltip>{{ tooltip }}</tooltip>
  </q-btn>
</template>

<script>
import Tooltip from './Tooltip.vue';
export default {
  components: { Tooltip },
  name: 'CloseBtn',
  props: {
    label: {
      type: String,
      default: '',
    },
    tooltip: {
      type: String,
      default: 'Close',
    },
  },
};
</script>
