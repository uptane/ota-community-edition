<template>
  <q-tooltip
    :content-class="{
      'text-white bg-black': $q.dark.isActive && !contentClass,
      'text-black bg-grey-4': !$q.dark.isActive && !contentClass,
      [contentClass]: contentClass,
    }"
    :hide-delay="hideDelay"
    color=""
  >
    <slot></slot>
  </q-tooltip>
</template>

<script>
export default {
  name: 'Tooltip',
  props: {
    contentClass: {
      type: String,
      default: '',
    },
    hideDelay: {
      type: Number,
      default: 0,
    },
  },
};
</script>
