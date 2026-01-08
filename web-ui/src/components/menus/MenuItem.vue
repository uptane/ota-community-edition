<template>
  <q-item v-ripple v-close-popup v-if="!menu.hide || (menu.hide && !menu.hide())" @click.native="menu.action ? menu.action() : gotoRoute(menu.route)" :class="{ 'menu-item ': true, active: menu.isActive() }">
    <q-tooltip
      v-if="mini"
      transition-show="slide-right2"
      transition-hide="slide-left2"
      :content-class="{
        'bg-black': true,
      }"
      anchor="center right"
      self="center left"
      :offset="[0, 10]"
    >
      <strong
        :class="{
          'text-primary': !menu.isActive(),
          'text-secondary': menu.isActive(),
        }"
        >{{ menu.label }}</strong
      >
      <br />
      <em>{{ menu.sublabel }}</em>
    </q-tooltip>
    <q-item-section avatar class="col-auto">
      <q-icon :name="menu.icon" />
    </q-item-section>
    <q-item-section>
      <q-item-label>{{ menu.label }}</q-item-label>
      <q-item-label class="opacity-70" caption>{{ menu.sublabel }}</q-item-label>
    </q-item-section>
  </q-item>
</template>

<script>
export default {
  name: 'LeftMenuItem',
  props: {
    menu: {
      type: Object,
      default: () => null,
    },
    mini: {
      type: Boolean,
      default: false,
    },
  },
  methods: {
    gotoRoute(route) {
      (this.$router.push(route) || { catch: () => {} }).catch((e) => {});
    },
  },
};
</script>
