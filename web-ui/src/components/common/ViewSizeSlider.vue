<template>
  <q-card class="shadow-5">
    <div class="pb-0 text-1 text-center">{{ title }}</div>
    <q-list style="width: 15em" class="pl-2 pr-2 bg-transparent">
      <q-item-section>
        <div class="row items-center">
          <span side class="col-auto q-mr-xs" style="font-size: 0.6em">{{ 1500 / 20 }}%</span>
          <div class="col">
            <q-slider v-model="viewSizePercent" :min="1500 / 20" :max="3500 / 20" :step="100 / 20" color="teal" label :label-value="viewSizeLabel" />
          </div>
          <span side class="col-auto  q-ml-xs" style="font-size: 0.6em">{{ 3500 / 20 }}%</span>
        </div>
      </q-item-section>
    </q-list>
  </q-card>
</template>

<script>
export default {
  name: 'ViewSizeSlider',
  props: {
    value: {
      type: Number,
      default: 20,
    },
    title: {
      type: String,
      default: 'Card Width',
    },
  },
  data() {
    return {};
  },
  computed: {
    viewSizeLabel() {
      return `${this.viewSizePercent}%`;
    },
    viewSizePercent: {
      get() {
        return (this.value * 100) / 20;
      },
      set(value) {
        this.$emit('input', (value * 20) / 100);
      },
    },
  },
  methods: {},
};
</script>

<style></style>
