<template>
  <q-btn @click="doAction" flat :loading="busy" class="p-0" :color="color" icon="cached">
    <span class="gt-md">&nbsp;Refresh</span>
    <template v-slot:loading>
      <q-spinner-hourglass class="on-left" />
      <span v-if="$q.screen.gt.md">&nbsp;Loading...</span>
    </template>
    <tooltip class="gt-md">{{ busy ? 'Refreshing ' : 'Refresh' }} current view</tooltip>
  </q-btn>
</template>

<script>
import Tooltip from './Tooltip.vue';
export default {
  components: { Tooltip },
  name: 'ReloadBtnComponent',
  props: {
    busy: {
      type: Boolean,
      default: false,
    },
    globalEvent: {
      type: String,
      default: '',
    },
    color: {
      type: String,
      default: 'secondary',
    },
  },
  methods: {
    doAction(ev) {
      this.$emit('reload-requested', ev);
      if (this.globalEvent) {
        this.$events.$emit(this.globalEvent, ev);
      }
    },
  },
};
</script>
