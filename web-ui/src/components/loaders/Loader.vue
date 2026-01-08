<template>
  <q-spinner-hourglass
    class="shg-loader float-right"
    :color="color"
    :size="size"
    :style="{
      height: size,
    }"
  />
</template>

<script>
export default {
  name: 'Loader',
  props: {
    color: {
      type: String,
      default: 'secondary',
    },
    size: {
      type: String,
      default: '2rem',
    },
  },
  data() {
    return {};
  },
  methods: {},
  computed: {},
};
</script>
