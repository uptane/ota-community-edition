<template>
  <div class="p-1 flex flex-center">
    <div class="text-center">
      <div :class="{ faded: !noFade }" class="row  items-center justify-center">
        <div v-if="!noIcon" class="col-auto">
          <q-icon class="mr-1  animated slideInDown" :size="iconSize" :name="icon" />
        </div>
        <div class="col">
          <div v-if="!oneLine" class="text-4 thin-text animated slideInLeft">{{ title }}</div>
          <p class="text-1 animated slideInRight">{{ message }}</p>
        </div>
      </div>
      <q-btn :color="actionColor" @click="doAction" flat :icon="actionIcon" size=".8rem" v-if="!noAction">{{ actionText }}</q-btn>
    </div>
  </div>
</template>

<script>
export default {
  name: 'EmptyComponent',
  data() {
    return {};
  },
  props: {
    title: {
      type: String,
      default: 'Empty',
    },
    icon: {
      type: String,
      default: 'warning',
    },
    iconSize: {
      type: String,
      default: '2.8em',
    },
    message: {
      type: String,
      default: 'Nothing here yet',
    },
    noAction: {
      type: Boolean,
      default: false,
    },
    noIcon: {
      type: Boolean,
      default: false,
    },
    oneLine: {
      type: Boolean,
      default: false,
    },
    actionText: {
      type: String,
      default: 'Go back',
    },
    actionIcon: {
      type: String,
      default: 'keyboard_arrow_left',
    },
    actionColor: {
      type: String,
      default: 'primary',
    },
    noFade: {
      type: Boolean,
      default: false,
    },
  },
  methods: {
    doAction(e) {
      this.$emit('on-action', e);
    },
  },
};
</script>
