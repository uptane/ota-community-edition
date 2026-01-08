<template>
  <q-chip no-caps color="warning" square class="text-capitalize" :class="{ 'text-white': !$q.dark.isActive }" :outline="$q.dark.isActive" :dense="dense" :size="size">
    <q-icon name="new_releases"></q-icon>
    &nbsp;Beta</q-chip
  >
</template>

<script>
export default {
  name: 'BetaBadge',
  props: {
    dense: {
      type: Boolean,
      default: false,
    },
    size: {
      type: String,
      default: 'md',
    },
  },
};
</script>

<style></style>
