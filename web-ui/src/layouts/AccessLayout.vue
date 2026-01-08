<template>
  <q-layout
    view="lHh Lpr lFf"
    :class="{
      'is-dashboard': isDashboardPage,
      'left-menu-open': leftDrawerOpen,
      'mini-bar': !leftDrawerOpen && miniBar,
    }"
  >
    <q-page-container>
      <!-- <no-scrollbar-component> -->
      <router-view />

      <!-- </no-scrollbar-component> -->
      <!-- <div class="mt-5">&nbsp;</div> -->
    </q-page-container>
  </q-layout>
</template>

<script>
export default {
  name: 'MainLayout',
  components: {},
  data() {
    return {
      miniBar: true,
      miniState: true,
      drawerState: true,
      rightDrawerOpen: true, //this.$q.platform.is.desktop,
    };
  },
  mounted() {
    this.darkTheme = false;
  },
  beforeDestroy() {
    this.darkTheme = 'auto';
  },
  watch: {
    miniBar(newVal) {
      this.toggleLeftMenu();
    },
  },
  computed: {
    darkTheme: {
      get() {
        return this.$q.dark.isActive;
      },
      set(val) {
        this.$q.dark.set(val);
      },
    },
    pageTitle() {
      return this.$store.getters['ui/currentPageTitle'];
    },
    isDashboardPage: {
      get() {
        return this.$store.getters['ui/isDashboardPage'];
      },
      set(val) {
        this.$store.commit('ui/setIsDashboardPage', val);
      },
    },
    leftDrawerOpen: {
      get() {
        return this.$store.getters['ui/isLeftDrawerOpen'];
      },
      set(v) {
        return this.$store.commit('ui/setIsLeftDrawerOpen', v);
      },
    },
  },
  methods: {
    toggleBodyClass(val) {
      if (val) {
        this.$jq('body').addClass('dark-theme');
      } else {
        this.$jq('body').removeClass('dark-theme');
      }
    },
    toggleLeftMenu() {
      if (this.miniBar) {
        this.drawerState = true;
        this.miniState = !this.miniState;
      } else {
        this.miniState = false;
        this.drawerState = !this.drawerState;
      }
    },

    // initJQuery() {
    //   this.$jq("body ").on(
    //     "mouseenter",
    //     ".mini-bar:not(.left-menu-open) .q-layout-drawer",
    //     () => {
    //       this.leftDrawerOpen = true;
    //       this.openedOnHover = true;
    //     }
    //   );
    //   this.$jq("body").on(
    //     "mouseleave",
    //     " .left-menu-open-on-hover .q-layout-drawer",
    //     () => {
    //       this.leftDrawerOpen = false;
    //     }
    //   );
    // }
  },
};
</script>
