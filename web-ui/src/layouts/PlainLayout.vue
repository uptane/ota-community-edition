<template>
  <q-layout
    view="lHh Lpr lFf"
    :class="{
      'dark-theme': false, //darkTheme,
      'light-theme': true, //!darkTheme,
    }"
  >
    <q-page-container>
      <router-view />
    </q-page-container>
  </q-layout>
</template>

<script>
export default {
  name: 'PlainLayout',
  components: {},
  data() {
    return {};
  },
};
</script>
