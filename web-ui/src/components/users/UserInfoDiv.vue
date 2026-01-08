<template>
  <!-- CE mode: Simplified user info - no sign out, no account management -->
  <div header class="row user-div menu-item">
    <div class="row w-100">
      <div class="col-auto flex justify-center user-avatar">
        <q-avatar color="primary" text-color="white" size="40px">
          <q-icon name="admin_panel_settings" />
        </q-avatar>
      </div>
      <div class="col user-texts" v-if="!showMiniContent">
        <h6 class="m-0 ml-2 ellipsis">OTA CE Admin</h6>
        <p class="m-0 ml-2 ellipsis opacity-60">Community Edition</p>
      </div>
    </div>
  </div>
</template>

<script>
import { mapGetters } from 'vuex';

export default {
  components: {},
  name: 'UserInfoDiv',
  data() {
    return {};
  },
  methods: {},
  computed: {
    ...mapGetters({
      user: 'ui/user',
    }),
    showMiniContent() {
      return this.miniBar && this.miniState && this.$q.screen.gt.sm;
    },
    miniState: {
      get() {
        if (this.$q.screen.gt.lg) {
          return false;
        }
        return this.$store.getters['ui/isMiniState'];
      },
      set(v) {
        this.$store.commit('ui/setIsMiniState', v);
      },
    },
    miniBar: {
      get() {
        return this.$store.getters['ui/isMiniBar'];
      },
      set(v) {
        this.$store.commit('ui/setIsMiniBar', v);
      },
    },
  },
};
</script>
