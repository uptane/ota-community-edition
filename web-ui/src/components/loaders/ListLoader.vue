<template>
  <div class="q-item-tile label text-center row flex p-2 items-center justify-center">
    <loader />
    <span class="p-1">{{ label }}</span>
  </div>
</template>

<script>
import Loader from './Loader';

export default {
  name: 'ListLoader',
  components: {
    Loader,
  },
  props: {
    label: {
      type: String,
      default: 'Loading...',
    },
  },
};
</script>
