<template>
  <div
    class="h-100 flex flex-center"
    style="wdth: 0.1rem;
            top:0;
            position:absolute;"
    :style="{
      right: right ? '-.65rem' : 'auto',
      left: !right ? '-.65rem' : 'auto',
    }"
  >
    <q-icon size="1.2rem" name="img:statics/svg/resize-icon.svg"></q-icon>
  </div>
</template>

<script>
export default {
  name: 'ResizeHandle',
  props: {
    right: {
      type: Boolean,
      default: false,
    },
  },
};
</script>
