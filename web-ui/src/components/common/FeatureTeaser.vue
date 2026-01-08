<template>
  <div
    class="feature-teaser-wrapper"
    :class="{
      'opacity-60': !canAccessFeature,
    }"
  >
    <slot></slot>
    <template v-if="!canAccessFeature">
      <tooltip>
        <div class="">
          <q-icon name="block" size="2em" color="negative"></q-icon>
          <span class="text-1">
            You do not have the required permission to use this feature.
          </span>
        </div>
      </tooltip>
      <div class="feature-teaser-overlay" :id="'feature-overlay-' + feature"></div>
    </template>
  </div>
</template>

<script>
import { mapGetters } from 'vuex';
import { canAccessFeature } from 'src/config/feature-toggle.js';
import Tooltip from './Tooltip.vue';
export default {
  components: { Tooltip },
  name: 'FeatureTeaser',
  props: {
    feature: {
      type: String,
      required: true,
    },
    show: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      loading: false,
    };
  },
  computed: {
    ...mapGetters({}),
    canAccessFeature() {
      return canAccessFeature(this.feature);
    },
  },
};
</script>

<style lang="scss" scoped>
.feature-teaser-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0);
  z-index: 1;
  cursor: not-allowed;
}
.feature-teaser-wrapper {
  position: relative;
}
</style>
