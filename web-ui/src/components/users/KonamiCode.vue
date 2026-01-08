<template> </template>

<script>
export default {
  name: 'KonamiCode',
  mounted() {
    this.startListeningForCode();
  },
  beforeDestroy() {
    this.stopListeningForCode();
  },
  methods: {
    startListeningForCode() {
      let keys = [];
      const konamiCodeTypeA = [
        'ArrowUp', // up
        'ArrowUp', // up
        'ArrowDown', // down
        'ArrowDown', // down
        'ArrowLeft', // left
        'ArrowRight', // right
        'ArrowLeft', // left
        'ArrowRight', // right
        'KeyA', // A
        'KeyB', // B
        // 'Enter', // Enter
      ];
      const konamiCodeTypeB = [
        'ArrowUp', // up
        'ArrowUp', // up
        'ArrowDown', // down
        'ArrowDown', // down
        'ArrowLeft', // left
        'ArrowRight', // right
        'ArrowLeft', // left
        'ArrowRight', // right
        'KeyB', // B
        'KeyA', // A
        // 'Enter', // Enter
      ];
      const match = (a, b) => {
        return a.length === b.length && a.every((c, i) => b[i] === c);
      };
      const checkKey = (e) => {
        if (e.code == 'Escape' || e.code == 'Enter') {
          keys = [];
          return false;
        }
        keys.push(e.code);
        if (match(keys, konamiCodeTypeA) || match(keys, konamiCodeTypeB)) {
          keys = [];
          return true;
        }
        if (keys.length >= 11) {
          // console.info('INVALID KONAMI CODE');
          keys = [];
          return false;
        }
      };
      this.$jq('body').on('keydown', (e) => {
        if (checkKey(e)) {
          this.adminMode = true;
          this.$q.notify({ message: 'Debug mode unlocked', caption: 'You should now see a debug menu item on the left navigation drawer', color: 'blue-grey', position: 'bottom' });
        }
      });
    },
    stopListeningForCode() {
      this.$jq('body').off('keydown');
    },
  },
  computed: {
    adminMode: {
      get() {
        return this.$store.getters['ui/adminMode'];
      },
      set(v) {
        this.$store.commit('ui/setAdminMode', v);
      },
    },
  },
};
</script>
