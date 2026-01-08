<template>
  <div class="row  items-center">
    <q-dialog transition-show="scale" transition-hide="scale" v-model="tabbedActive">
      <div style="max-width: 80vw;">
        <q-card>
          <q-tabs v-model="tab" dense class="text-grey" active-color="primary" indicator-color="primary" align="justify" narrow-indicator>
            <q-tab no-caps name="from" :label="'Start: ' + $date.formatDate(fromModel, 'YYYY-MM-DD HH:mm')" />
            <q-tab no-caps name="to" :label="'End: ' + $date.formatDate(toModel, 'YYYY-MM-DD HH:mm')" />
          </q-tabs>
          <q-separator />
          <q-tab-panels v-model="tab" animated>
            <q-tab-panel name="from" class="p-0 w-100">
              <date-time-picker v-model="fromModel"></date-time-picker>
            </q-tab-panel>

            <q-tab-panel name="to" class="p-0">
              <date-time-picker v-model="toModel"></date-time-picker>
            </q-tab-panel>
          </q-tab-panels>
          <div class="h-divide-top">
            <q-btn flat class="w-100" color="primary" @click="updateData">
              {{ doneLabel }}
            </q-btn>
          </div>
        </q-card>
      </div>
    </q-dialog>
    <div v-if="tabbed">
      <q-btn flat dense size="md" icon="event" :active="tabbedActive" class="cursor-pointer col ellipsis" @click="showSelector">
        <tooltip v-if="!tabbedActive">
          <span> Date Range: &nbsp;{{ $date.formatDate(fromModel, 'YYYY-MM-DD HH:mm') }}</span>
          →
          <span>{{ $date.formatDate(toModel, 'YYYY-MM-DD HH:mm') }}</span>
        </tooltip>
        <div v-if="!buttonOnly">
          <span>&nbsp;{{ $date.formatDate(fromModel, 'YYYY-MM-DD HH:mm') }}</span>
          →
          <span>{{ $date.formatDate(toModel, 'YYYY-MM-DD HH:mm') }}</span>
        </div>

        <q-popup-proxy transition-show="scale" transition-hide="scale" v-if="presetRange !== 0">
          <div class="h-divide-bottom mnw-30em">
            <preset-time-picker :customValue="customRange" :value="dateRangePreset" @input="setPresetRange" :options="presetOptions" @custom-picker="showSelector"></preset-time-picker>
          </div>
        </q-popup-proxy>
      </q-btn>
    </div>
    <div v-else>
      <q-btn flat dense size="md" icon="event" class="cursor-pointer col ellipsis" :label="$date.formatDate(fromModel, 'YYYY-MM-DD HH:mm')">
        <q-popup-proxy transition-show="scale" transition-hide="scale">
          <date-time-picker no-unset v-model="fromModel"></date-time-picker>
        </q-popup-proxy>
      </q-btn>
      →
      <q-btn flat dense class="cursor-pointer col  ellipsis" :label="$date.formatDate(toModel, 'YYYY-MM-DD HH:mm')">
        <q-popup-proxy transition-show="scale" transition-hide="scale">
          <date-time-picker no-unset v-model="toModel"></date-time-picker>
        </q-popup-proxy>
      </q-btn>
    </div>
  </div>
</template>

<script>
import { date } from 'quasar';
import DateTimePicker from 'src/components/common/DateTimePicker.vue';
import Tooltip from 'src/components/common/Tooltip.vue';
import PresetTimePicker from 'src/components/common/PresetTimePicker.vue';
const { formatDate } = date;
const parseDateAsStr = (dateVal) => formatDate(dateVal, 'YYYY-MM-DD HH:mm');
const parseDateAsNumber = (dateStr) => new Date(dateStr).getTime();
export default {
  components: { DateTimePicker, Tooltip, PresetTimePicker },
  name: 'DateTimeRangePicker',
  props: {
    buttonOnly: {
      type: Boolean,
      default: false,
    },
    doneLabel: {
      type: String,
      default: 'Save & Update Chart',
    },
    tabbed: {
      type: Boolean,
      default: false,
    },
    from: {
      type: String,
      default: () => parseDateAsStr(new Date(Date.now() - 1 * 60 * 1000)),
    },
    to: {
      type: String,
      default: () => parseDateAsStr(new Date(Date.now())),
    },
    dateRangePreset: {
      type: Number,
      default: 60 * 60 * 1000,
    },
    presetOptions: {
      type: Array,
      default: () => [{ label: 'Last 1 minute', value: 60 * 1000 }, { label: 'Last 30 minutes', value: 30 * 60 * 1000 }, { label: 'Last 1 hour', value: 60 * 60 * 1000 }],
    },
    isCustomDateRange: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      tab: 'from',
      tabbedActive: false,
      presetRange: 60 * 1000,
      customRange: 20,
    };
  },
  computed: {
    fromModel: {
      get() {
        return this.from;
      },
      set(v) {
        this.$emit('update:from', v);
      },
    },
    toModel: {
      get() {
        return this.to;
      },
      set(v) {
        this.$emit('update:to', v);
      },
    },
  },
  methods: {
    setPresetRange(presetValue) {
      if (this.presetOptions.map((a) => a.value).indexOf(presetValue) == -1) {
        this.tabbedActive = true;
        this.customRange = parseDateAsNumber(this.toModel) - parseDateAsNumber(this.fromModel) + 1;
        this.$emit('update:date-range-preset', this.customRange);
        this.$emit('update:is-custom-date-range', true);
      } else {
        this.fromModel = parseDateAsStr(new Date(Date.now() - presetValue));
        this.toModel = parseDateAsStr(new Date(Date.now()));
        this.$emit('update:date-range-preset', presetValue);
        this.$emit('update:is-custom-date-range', false);
      }
    },
    updateData() {
      this.tabbedActive = false;
      setTimeout(() => {
        this.$emit('update:all', this.toModel);
        this.$events.$emit('update:date-range-selector-updated', {
          from: this.fromModel,
          to: this.toModel,
        });
      }, 500);
    },
    showSelector() {
      this.setPresetRange(this.dateRangePreset);
    },
  },
  mounted() {},
  watch: {
    dateRangePreset() {
      if (this.presetOptions.map((a) => a.value).indexOf(this.dateRangePreset) != -1) {
      }
    },
    tabbedActive() {
      this.$events.$emit('update:date-range-selector-active', this.tabbedActive);
    },
  },
};
</script>
