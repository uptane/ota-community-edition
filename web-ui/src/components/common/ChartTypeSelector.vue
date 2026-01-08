<template>
  <div class="q-card">
    <div class="row mnw-30em p-1">
      <div class="v-divide col-auto">
        <h6 class="h-divide-bottom m-0 pl-1 pr-1">Granularity</h6>
        <q-option-group :options="breakDownOptions" type="radio" v-model="granularityModel" />
      </div>
      <div class="col-auto v-divide-left-dashed">
        <h6 class="h-divide-bottom m-0   pl-2 pr-2 ">Options</h6>
        <div>
          <q-checkbox :value="showLegends" @input="$emit('update:show-legends', $event)" label="Show legends" :color="'primary'"></q-checkbox>
        </div>
      </div>
    </div>
    <div class="h-divide-top">
      <q-btn flat class="w-100" color="primary" @click="updateData" v-close-popup>
        Update Chart
      </q-btn>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ChartTypeSelector',
  props: {
    cores: {
      type: Array,
      default: () => {
        return ['cpu0_p_cpu', 'cpu1_p_cpu', 'cpu2_p_cpu', 'cpu3_p_cpu'];
      },
    },
    processes: {
      type: Array,
      default: () => {
        return ['cpu_p', 'user_p', 'system_p'];
      },
    },
    coreType: {
      type: String,
      default: 'cpu_p',
    },
    granularity: {
      type: String,
      default: 'processes',
    },
    showLegends: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      coreOptions: [{ label: 'CPU0', value: 'cpu0' }, { label: 'CPU1', value: 'cpu1' }, { label: 'CPU2', value: 'cpu2' }, { label: 'CPU3', value: 'cpu3' }],
      breakDownOptions: [
        {
          label: 'Break down by process',
          value: 'processes',
        },
        {
          label: 'Break down per-core',
          value: 'cores',
        },
      ],
      options: [
        {
          label: 'Total CPU Usage',
          value: 'cpu_p',
          subOptions: [{ label: 'CPU0', value: 'cpu0_p_cpu' }, { label: 'CPU1', value: 'cpu1_p_cpu' }, { label: 'CPU2', value: 'cpu2_p_cpu' }, { label: 'CPU3', value: 'cpu3_p_cpu' }],
        },
        {
          label: 'Usage By User',
          value: 'user_p',
          subOptions: [{ label: 'CPU0', value: 'cpu0_p_user' }, { label: 'CPU1', value: 'cpu1_p_user' }, { label: 'CPU2', value: 'cpu2_p_user' }, { label: 'CPU3', value: 'cpu3_p_user' }],
        },
        {
          label: 'Usage By System',
          value: 'system_p',
          subOptions: [{ label: 'CPU0', value: 'cpu0_p_system' }, { label: 'CPU1', value: 'cpu1_p_system' }, { label: 'CPU2', value: 'cpu2_p_system' }, { label: 'CPU3', value: 'cpu3_p_system' }],
        },
      ],
    };
  },
  computed: {
    coreOptionsParsed() {
      const suffixes = {
        cpu_p: '_p_cpu',
        user_p: '_p_user',
        system_p: '_p_system',
      };
      const suffix = suffixes[this.coreType];
      return this.coreOptions.map((m) => {
        return { ...m, value: m.value + suffix };
      });
    },
    coresModel: {
      get() {
        return this.cores;
      },
      set(v) {
        this.$emit('update:cores', v);
      },
    },
    processesModel: {
      get() {
        return this.processes;
      },
      set(v) {
        this.$emit('update:processes', v);
      },
    },
    coreTypeModel: {
      get() {
        return this.coreType;
      },
      set(v) {
        this.$emit('update:core-type', v);
      },
    },
    granularityModel: {
      get() {
        return this.granularity;
      },
      set(v) {
        this.$emit('update:granularity', v);
      },
    },
  },
  methods: {
    updateData() {
      this.$emit('update', true);
      this.$emit('update:cores', this.coresModel);
      this.$emit('update:processes', this.processesModel);
      this.$emit('update:granularity', this.granularityModel);
    },
    updateCore() {
      this.$emit('input', this.chartModel);
    },
    updateCores() {
      this.$emit('input', this.chartModel);
    },
    updateProcess() {
      this.$emit('input', this.chartModel);
    },
  },
  watch: {},
};
</script>
