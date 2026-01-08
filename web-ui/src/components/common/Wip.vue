<template>
  <div class="p-2 flex flex-center">
    <div class="text-center">
      <div class="text-4 thin-text opacity-30 animated slideInLeft">Work In Progress</div>
      <p class="display-3 opacity-30  animated slideInRight">Check back later</p>
      <q-btn color="primary" @click="$router.push('/')" flat icon="keyboard_arrow_left" size=".8rem">Go back</q-btn>
    </div>
  </div>
</template>

<script>
export default {
  name: 'WipComponent',
  data() {
    return {};
  },
  methods: {},
};
</script>
