<template>
  <div>{{ maskedText }} <q-btn flat color="default" dense v-if="text.length > 0 && !noReveal" size="md" :icon="maskOn ? 'visibility' : 'visibility_off'" @click="maskOn = !maskOn" class="ml-1"></q-btn></div>
</template>

<script>
export default {
  name: 'MaskedText',
  props: {
    text: {
      type: String,
      default: '',
    },
    mask: {
      type: String,
      default: '*',
    },
    noReveal: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      maskOn: true,
    };
  },
  computed: {
    maskedText() {
      return this.maskOn ? this.text.replace(/./g, this.mask) : this.text;
    },
  },
};
</script>

<style></style>
