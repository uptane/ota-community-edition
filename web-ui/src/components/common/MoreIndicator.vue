<template>
  <div v-if="dataLength > max" class="absolute-bottom  w-100" style="height:3rem">
    <div
      class=" h-100  w-100"
      :class="{
        'bg-black opacity-60': $q.dark.isActive,
        'bg-secondary opacity-80': !$q.dark.isActive,
      }"
      style="position:absolute"
    ></div>
    <q-btn
      type="a"
      flat
      :href="href"
      :color="$q.dark.isActive ? 'secondary' : 'white'"
      class="h-100
    no-border-radius
    w-100 text-center"
      >{{ text }}</q-btn
    >
  </div>
</template>

<script>
export default {
  name: 'MoreIndicator',
  props: {
    max: {
      type: Number,
      default: 5,
    },
    dataLength: {
      type: Number,
      default: 0,
    },
    href: {
      type: String,
      default: '',
    },
    text: {
      type: String,
      default: 'More',
    },
  },
};
</script>
