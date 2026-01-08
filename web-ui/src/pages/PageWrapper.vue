<template>
  <q-page-container>
    <q-page>
      <slot></slot>
      <q-resize-observer @resize="onResize" />
    </q-page>
  </q-page-container>
</template>

<script>
export default {
  name: 'PageWrapper',
  methods: {
    onResize(ev) {
      this.$store.commit('ui/setCurrentPageDimensions', ev);
    },
  },
};
</script>
