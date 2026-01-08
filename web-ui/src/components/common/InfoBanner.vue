<template>
  <q-banner
    inline-actions
    class="animated slideInTop z-10 p-1 pr-0 info-banner"
    :class="{
      [additionalClasses]: !!additionalClasses,
    }"
  >
    <div class="row items-center">
      <div class="col-auto q-mr-sm">
        <slot name="icon">
          <q-icon name="info" size="sm" class="text-info" />
        </slot>
      </div>
      <div
        class="col mxw-80 "
        :class="{
          ellipsis: singleLine,
          'text-wrap': !singleLine,
        }"
      >
        <slot name="content"> </slot>
      </div>
      <q-space />
      <div class="col col-auto">
        <slot name="action">
          <!-- <q-btn
        flat
        color="primary"
        label="Update now"
      /> -->
        </slot>
        <q-btn v-if="!persistent" flat icon="close" @click="$emit('dismiss', '')">
          <tooltip>Dismiss message</tooltip>
        </q-btn>
      </div>
    </div>
  </q-banner>
</template>

<script>
import Tooltip from 'src/components/common/Tooltip.vue';
export default {
  name: 'InfoBanner',
  components: {
    Tooltip,
  },
  props: {
    singleLine: {
      type: Boolean,
      default: true,
    },
    additionalClasses: {
      type: String,
      default: '',
    },
    persistent: {
      type: Boolean,
      default: false,
    },
  },
};
</script>

<style></style>
