<template>
  <div>
    <span v-if="type == 'custom-image'">Custom package</span>
    <span v-else-if="type == 'custom-container'">Custom container package</span>
    <span v-else>Torizon package</span>
  </div>
</template>

<script>
export default {
  name: 'PackageKind',
  props: {
    type: {
      type: String,
      default: '',
    },
  },
};
</script>
