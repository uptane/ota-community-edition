<template>
  <div class="q-card lighter-card">
    <q-btn-group no-caps unelevated flat class="q-card">
      <template v-if="showView('table')">
        <q-btn-dropdown v-if="viewType === 'table'" icon="view_column" :flat="viewType !== 'table'" :color="viewType === 'table' ? 'secondary' : 'default'" :label="$q.screen.lt.lg || forceIconOnly ? '' : 'table'" @click="viewTypeChanged('table')" split>
          <q-card class="shadow-5 mnw-10em shadow-10">
            <q-card-section class="text-1 m-0 pb-0">Column Order &amp; Visibility</q-card-section>
            <q-separator />
            <draggable v-model="sortedColumns" handle=".handle" class="q-pa-md pt-0 mt-0  visible-column-parent" ghost-class="bg-grey-5">
              <div
                class="q-gutter-sm "
                :class="{
                  'visible-column-item': !col.required,
                }"
                style="touch-action: none"
                :key="i"
                v-for="(col, i) in columns"
              >
                <div class="row w-100 items-center">
                  <div
                    :class="{
                      handle: !col.required,
                    }"
                    :style="{
                      cursor: col.required ? 'not-allowed' : 'ns-resize',
                    }"
                    class="col-auto opacity-40"
                  >
                    <q-icon name="menu"></q-icon>
                  </div>
                  <div class="col">
                    <q-checkbox v-model="visibleCols" :val="col.name" :label="col.label" :color="col.required ? 'teal-2' : 'teal'" :disable="col.required" />
                  </div>
                </div>
              </div>
            </draggable>
          </q-card>
        </q-btn-dropdown>

        <q-btn v-else icon="fas fa-table" :flat="viewType !== 'table'" :color="viewType === 'table' ? 'secondary' : 'default'" :label="$q.screen.lt.lg || forceIconOnly ? '' : 'table'" @click="viewTypeChanged('table')">
          <tooltip>View as table</tooltip>
        </q-btn>
      </template>
      <template v-if="showView('dense')">
        <q-btn icon="view_list" :color="viewType === 'dense' ? 'secondary' : 'default'" :flat="viewType !== 'dense'" :label="$q.screen.lt.lg || forceIconOnly ? '' : 'Dense'" @click="viewTypeChanged('dense')">
          <tooltip>View as fixed sized cards</tooltip>
        </q-btn>
      </template>
      <template v-if="showView('relaxed')">
        <q-btn-dropdown v-if="viewType === 'relaxed'" auto-close icon="featured_play_list" :flat="viewType !== 'relaxed'" :color="viewType === 'relaxed' ? 'secondary' : 'default'" :label="$q.screen.lt.lg || forceIconOnly ? '' : 'Relaxed'" @click="viewTypeChanged('relaxed')" split>
          <view-size-slider v-model="viewSizeValue" @input="viewSizeSliderChanged" />
          <tooltip>View as resizable cards</tooltip>
        </q-btn-dropdown>

        <q-btn v-else icon="featured_play_list" :color="viewType === 'relaxed' ? 'secondary' : 'default'" :label="$q.screen.lt.lg || forceIconOnly ? '' : 'Card'" :flat="viewType !== 'relaxed'" @click="viewTypeChanged('relaxed')">
          <tooltip>View as resizable cards</tooltip>
        </q-btn>
      </template>
    </q-btn-group>
  </div>
</template>

<script>
import _ from 'lodash';
import Tooltip from './Tooltip.vue';
import draggable from 'vuedraggable';
import { visibleColumns } from '../../store/devices/getters';
import ViewSizeSlider from './ViewSizeSlider.vue';

export default {
  components: { Tooltip, draggable, ViewSizeSlider },
  name: 'ViewTypeSelector',
  props: {
    value: {
      type: String,
      default: 'table',
    },
    viewSize: {
      type: Number,
      default: 20,
    },
    columns: {
      type: Array,
      default: () => [],
    },
    visibleColumns: {
      type: Array,
      default: () => [],
    },
    views: {
      type: Array,
      default: () => ['table', 'dense', 'relaxed'],
    },
    forceIconOnly: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      _visibleCols: [],
      visibleCols: [],
    };
  },
  mounted() {
    this.visibleCols = this.visibleColumns;
  },
  computed: {
    sortedColumns: {
      get() {
        return this.columns;
      },
      set(v) {
        this.$emit('update:columns', v);
      },
    },
    viewSizeValue: {
      get() {
        return this.viewSize;
      },
      set(v) {
        this.$emit('update:viewSize', v);
      },
    },
    viewType: {
      get() {
        return this.value;
      },
      set(v) {
        this.$emit('input', v);
      },
    },
  },
  methods: {
    viewTypeChanged(type) {
      this.$emit('input', type);
    },
    viewSizeSliderChanged(size) {
      this.$emit('update:viewSize', size);
    },
    showView(view) {
      return this.views && this.views.indexOf(view) > -1;
    },
  },
  watch: {
    visibleCols(n) {
      const cols = _.uniq(
        this.columns
          .filter((f) => f.required)
          .map((m) => m.name)
          .concat(n),
      );
      if (cols.length !== this.visibleCols.length) {
        this.visibleCols = [...cols];
      }
      this.$emit('update:visibleColumns', this.visibleCols);
    },
  },
};
</script>
