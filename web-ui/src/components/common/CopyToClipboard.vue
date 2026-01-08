<template>
  <span class="">
    <q-icon class="" name="content_copy" @click.stop="copyToClipboard()" />
    <tooltip :content-class="copyIndicator ? 'text-white bg-positive' : ''">
      <span v-if="!copyIndicator">Copy to clipboard</span>
      <span v-else class="">Copied!</span>
    </tooltip>
  </span>
</template>

<script>
import { copyToClipboard } from 'quasar';
import Tooltip from './Tooltip.vue';
export default {
  components: { Tooltip },
  name: 'CopyToClipboard',
  props: ['text'],
  data() {
    return {
      copyIndicator: false,
    };
  },
  methods: {
    copyToClipboard() {
      copyToClipboard(this.text);
      this.copyIndicator = true;
      setTimeout(() => {
        this.copyIndicator = false;
      }, 1000);
    },
  },
};
</script>

<style></style>
