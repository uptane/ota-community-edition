<template>
  <q-layout class>
    <q-page-container class>
      <q-page>
        <div class="w-100 mxw-60em">
          <div class="p-2 text-h3">About OTA Community Edition</div>
          <p class="p-2 text-1">
            OTA Community Edition is an open-source Over-The-Air (OTA) software update solution for embedded devices.
            Built on the Uptane security framework, it provides secure, reliable software updates for IoT and embedded systems.
          </p>
          <p class="p-2 text-1">
            Key features:
          </p>
          <ul class="pl-4 text-1">
            <li>Uptane-compliant security for automotive-grade update protection</li>
            <li>TUF (The Update Framework) based package signing and verification</li>
            <li>Delta updates to minimize bandwidth usage</li>
            <li>Fleet management with device grouping</li>
            <li>Package versioning and rollback support</li>
          </ul>
          <p class="p-2 text-1">
            <q-btn color="primary" type="a" no-caps flat dense target="_blank" size="1em" href="https://github.com/uptane/ota-community-edition">
              View on GitHub
            </q-btn>
            <q-btn color="primary" type="a" no-caps flat dense target="_blank" size="1em" href="https://uptane.github.io/">
              Learn about Uptane
            </q-btn>
          </p>
          <div class="p-2 text-h5 mt-4">License</div>
          <p class="p-2 text-1">
            OTA Community Edition is released under the Mozilla Public License 2.0 (MPL-2.0).
          </p>
        </div>
      </q-page>
    </q-page-container>
  </q-layout>
</template>

<script>
import { mapGetters } from 'vuex';

export default {
  name: 'PageAbout',
  components: {},
  data() {
    return {};
  },
  created() {},
  mounted() {
    this.setup();
  },
  updated() {},
  computed: {
    ...mapGetters({}),
    pageTitle: {
      get() {
        return this.$store.getters['ui/currentPageTitle'];
      },
      set(val) {
        return this.$store.commit('ui/setCurrentPageTitle', val);
      },
    },
  },
  methods: {
    setTitle() {
      this.pageTitle = 'About OTA CE';
    },
    setup() {
      this.setTitle();
    },
  },
};
</script>
