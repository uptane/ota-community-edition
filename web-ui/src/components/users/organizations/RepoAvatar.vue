<template>
  <div
    :style="{
      width: wrapperWidth,
      height: wrapperHeight,
      padding: '0.1em',
      'border-radius': '50%',
      background: wrapperBg,
    }"
  >
    <q-avatar :color="avatarColor" :text-color="avatarTextColor" :size="avatarSize">
      <img v-if="repoData.avatar" :src="repoData.avatar" />
      <span v-if="!repoData.avatar">{{ initial }}</span>
    </q-avatar>
  </div>
</template>

<script>
import { mapGetters } from 'vuex';
export default {
  name: 'RepoAvatar',
  props: {
    repoData: {
      type: Object,
      default: () => {},
    },
    mini: {
      type: Boolean,
      default: false,
    },
    size: {
      type: String,
      default: '2.5em',
    },
    color: {
      type: String,
      default: 'warning',
    },
    hostColor: {
      type: String,
      default: 'secondary',
    },
    showBorder: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    ...mapGetters({}),
    initial() {
      return ((this.repoData.name || this.repoData.email || '')[0] || '').toUpperCase();
    },
    rnd() {
      return Math.random();
    },
    wrapperWidth() {
      return this.mini ? '2.2em' : '2.7em';
    },
    wrapperHeight() {
      return this.mini ? '2.2em' : '2.7em';
    },
    avatarColor() {
      return this.mini ? 'white' : this.repoData.is_host_repo ? this.hostColor : this.color;
    },
    avatarSize() {
      return this.mini ? '2em' : this.size;
    },
    avatarTextColor() {
      return this.mini ? 'black' : 'white';
    },
    wrapperBg() {
      return this.showBorder ? '#fff' : 'transparent';
    },
  },
  mounted() {},
};
</script>
