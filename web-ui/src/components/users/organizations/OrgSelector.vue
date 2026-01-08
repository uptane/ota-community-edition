<template>
  <div class="">
    <q-btn-dropdown v-if="guestRepositories && guestRepositories.length && currentRepository" flat no-caps @click="onMainClick" class="full-width">
      <template v-slot:label>
        <div v-if="loading" class="row items-center">
          <q-spinner-gears class="mr-1"></q-spinner-gears>
          <span>Switching repository...</span>
        </div>
        <div v-else class="row items-center">
          <repo-avatar :repo-data="selectedRepository" :mini="true" :show-border="true" class="q-mr-sm"></repo-avatar>
          <div class="text-center  mxw-10em ellipsis">
            {{ selectedRepository.name }}
          </div>
        </div>
      </template>

      <q-list :dark="$q.dark.isActive">
        <q-item
          v-for="item in items"
          :key="item.id"
          clickable
          v-close-popup
          @click="onItemClick(item)"
          class="h-divide-top respository-item"
          :class="{
            'selected-repository': item.name === currentRepository.name,
            'host-repo-item': item.is_host_repo,
          }"
        >
          <q-item-section avatar>
            <repo-avatar :repo-data="item"></repo-avatar>
          </q-item-section>
          <q-item-section>
            <q-item-label class="w-15em ellipsis-2-lines">{{ item.name }}</q-item-label>
            <q-item-label v-if="item.description" caption class="faded w-15em ellipsis">{{ item.description }}</q-item-label>
            <q-item-label v-if="!item.is_host_repo && item.guest_profile && guestProfiles[item.guest_profile]" caption class="faded text-warning w-15em ellipsis-2-lines">{{ guestProfiles[item.guest_profile].friendly_name }}</q-item-label>
          </q-item-section>

          <q-item-section side v-if="item.name === currentRepository.name">
            <q-icon name="check" :color="checkColor" />
          </q-item-section>
        </q-item>
      </q-list>
    </q-btn-dropdown>
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex';
import RepoAvatar from 'src/components/users/organizations/RepoAvatar.vue';
import { AuthService } from 'src/services/auth.service';
export default {
  components: { RepoAvatar },
  name: 'OrgSelector',
  data() {
    return {
      loading: false,
      showRepoSwitchDialog: false,
      currentRepository: null,
      clickedItem: null,
    };
  },
  computed: {
    ...mapGetters({
      hostRepository: 'organizations/hostRepository',
      myRepository: 'organizations/myRepository',
      guestRepositories: 'organizations/guestRepositories',
      guestProfiles: 'organizations/guestProfiles',
      availableRepositories: 'organizations/availableRepositories',
      userData: 'users/userData',
    }),
    guestRepositories() {
      return this.$store.getters['organizations/guestRepositories'];
    },
    itemsByKey() {
      return _.keyBy(this.items, 'id');
    },
    items() {
      return this.availableRepositories;
    },
    selectedRepository() {
      return this.itemsByKey[this.currentRepository.id];
    },
    checkColor() {
      return this.selectedRepository.is_host_repo ? 'secondary' : 'warning';
    },
  },
  methods: {
    onMainClick() {
      // console.log('Clicked on main button')
    },

    onItemClick(item) {
      if (!_.isEqual(this.currentRepository.id, item.id)) {
        this.$events.$emit('dialogs:organizations:set-repo-as-active:confirm', item);
      }
    },
  },
  mounted() {
    const savedRepo = AuthService.getCurrentOrganization();
    this.currentRepository = savedRepo || this.myRepository;
    this.$events.$on(`dialogs:organizations:set-repo-as-active:loading`, (data) => {
      this.loading = data;
    });
    this.$events.$on(`dialogs:organizations:set-repo-as-active:set`, (data) => {
      this.currentRepository = data;
    });
  },
};
</script>

<style lang="scss" scoped>
$color2: $secondary;
$color: $warning;
.respository-item {
  border-left: 5px solid rgba(grey, 0.3);
  border-right: 5px solid rgba(grey, 0.3);
  &:hover {
    border-left: 5px solid rgba($color, 0.4);
    border-right: 5px solid rgba($color, 0.4);
    background-color: rgba($color, 0.05);
  }
  &.host-repo-item {
    &:hover {
      border-left: 5px solid rgba($color2, 0.4);
      border-right: 5px solid rgba($color2, 0.4);
      background-color: rgba($color2, 0.05);
    }
  }
}
.respository-item.selected-repository {
  border-left: 5px solid $color;
  border-right: 5px solid $color;
  &.host-repo-item {
    border-left: 5px solid $color2;
    border-right: 5px solid $color2;
  }
}
</style>
