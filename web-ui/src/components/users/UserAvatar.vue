<template>
  <div
    class="host-avatar-wrapper"
    :class="{
      'mb-1': !isHostRepoSelected,
      minified: mini,
    }"
  >
    <repo-avatar :repo-data="repoData" :show-border="false" color="warning" :size="repoAvatarSize" v-if="!isHostRepoSelected"></repo-avatar>
    <q-avatar
      color="secondary"
      text-color="white"
      :size="hostAvatarSize"
      class="host-avatar"
      :class="{
        'shadow-2 inactive': !isHostRepoSelected,
        minified: mini,
      }"
    >
      <img v-if="userData.avatar" :src="userData.avatar" />
      <span v-if="!userData.avatar">{{ initial }}</span>
      <q-tooltip
        v-if="mini"
        transition-show="slide-right2"
        transition-hide="slide-left2"
        :content-class="{
          'bg-black text-white': $q.dark.isActive,
          'bg-white text-black': !$q.dark.isActive,
        }"
        anchor="center right"
        self="center left"
        :offset="[0, 10]"
      >
        <strong
          :class="{
            'text-primary': !isAccountPage,
            'text-secondary': isAccountPage,
          }"
          >My Account</strong
        >
        <br />
        <em>View/Edit your account info</em>
      </q-tooltip>
    </q-avatar>
  </div>
</template>

<script>
import { mapGetters } from 'vuex';
import { AuthService } from '../../services/auth.service';
import RepoAvatar from 'src/components/users/organizations/RepoAvatar.vue';
export default {
  components: { RepoAvatar },
  name: 'UserAvatar',
  props: {
    mini: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    ...mapGetters({
      userData: 'users/userData',
      myRepository: 'organizations/myRepository',
    }),
    initial() {
      return ((this.user.name || this.user.email || '')[0] || '').toUpperCase();
    },
    user() {
      return this.$store.getters['ui/user'] || {};
    },
    rnd() {
      return Math.random();
    },
    isAccountPage() {
      return this.$route.name === 'account';
    },
    repoData() {
      return AuthService.getCurrentOrganization();
    },
    isHostRepoSelected() {
      return !this.repoData || this.repoData.id === this.myRepository.id;
    },
    hostAvatarSize() {
      return !this.isHostRepoSelected ? (this.mini ? '1em' : '1.5em') : this.mini ? '2.5em' : '3.5em';
    },
    repoAvatarSize() {
      return this.mini ? '2.5em' : '3em';
    },
  },
  mounted() {},
};
</script>

<style lang="scss" scoped>
.host-avatar-wrapper {
  position: relative;
  display: inline-block;
  max-width: 3.5em;
  max-height: 3.5em;
  &.minified {
    max-width: 2.5em;
    max-height: 2.5em;
  }
}
.host-avatar.inactive {
  position: absolute;
  left: 1.25em;
  top: 1.25em;
  &.minified {
    left: 1.75em;
    top: 1.75em;
  }
}
</style>
