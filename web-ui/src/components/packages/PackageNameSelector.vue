<template>
  <div>
    <p class="p-0 m-0 text-1 opacity-50">Select package</p>
    <q-select toggle :option-value="(item) => item" :option-label="(item) => item.name" outlined class="pb-1" use-input input-debounce="0" v-model="selectedPackage" @filter="filterFn" :options="options || []" hint="Hint: Start typing to search the list of packages">
      <template v-slot:no-option>
        <q-item>
          <q-item-section class="text-grey">
            No results
          </q-item-section>
        </q-item>
      </template>

      <template v-slot:selected>
        <div class="mxw-85">
          <div class="row items-center " v-if="selectedPackage && selectedPackage.name">
            <div class="col-auto">
              <package-icon class="mt-1" size="2rem" v-if="selectedPackage" :package-info="selectedPackage" />
            </div>
            <div class="col pl-1">
              <q-item-label dense square color="white" text-color="primary" class="ellipsis">
                {{ selectedPackage.name }}
              </q-item-label>
              <q-item-label caption v-if="selectedPackage.isExpired">
                <q-icon name="warning" color="warning" size="1rem" class=""></q-icon>
                <span class="text-warning">
                  "This package comes from an expired source, and cannot currently be installed. Check packages page to refresh."
                </span>
              </q-item-label>
              <q-item-label caption>
                <div v-if="preselectedPkg && preselectedPkg.name === selectedPackage.name">
                  <q-icon name="star" color="positive" size="1.2rem" class="" />
                  <span
                    class="opacity-40"
                    :class="{
                      'text-white': $q.dark.isActive,
                      'text-black': !$q.dark.isActive,
                    }"
                  >
                    {{ preselectedText }}</span
                  >
                </div>
              </q-item-label>
            </div>
          </div>
        </div>
      </template>

      <template v-slot:option="scope">
        <template v-if="scope.opt.header">
          <q-item-label header>
            {{ scope.opt.name }}
          </q-item-label>
        </template>
        <template v-else>
          <q-item
            :disable="scope.opt.disable || scope.opt.isExpired"
            class="pl-1 h-divide-top"
            v-bind="scope.itemProps"
            v-on="scope.itemEvents"
            :class="{
              'text-primary active': scope.itemProps.active,
              preselected: preselectedPkg.name === scope.opt.name,
            }"
          >
            <q-item-section avatar>
              <package-icon :package-info="scope.opt" />
            </q-item-section>
            <q-item-section>
              <q-item-label> {{ scope.opt.name }}</q-item-label>
              <q-item-label caption>
                <div v-if="preselectedPkg.name === scope.opt.name">
                  <span class="opacity-40"> {{ preselectedText }}</span>
                  <q-icon name="star" color="positive" size="1.2rem" class="ml-1" />
                </div>
              </q-item-label>
              <div v-if="scope.opt.isExpired">
                <q-icon name="warning" color="warning" size="1rem" class=""></q-icon>
                <span class="text-warning">
                  "This package comes from an expired source, and cannot currently be installed. Check packages page to refresh."
                </span>
              </div>
            </q-item-section>
            <q-item-section avatar>
              <q-icon v-if="scope.itemProps.active" name="check" />
              <q-icon v-else name="check" class="opacity-10" />
            </q-item-section>
          </q-item>
        </template>
      </template>
    </q-select>
  </div>
</template>

<script>
import _ from 'lodash';
import { mapGetters } from 'vuex';
import PackageIcon from 'src/components/packages/PackageIcon.vue';
export default {
  components: { PackageIcon },
  name: 'PackageNameSelector',
  props: {
    value: {
      type: Object,
      default: () => {
        return {};
      },
    },
    packageListOptions: {
      type: Array,
      default: () => {
        return [];
      },
    },
    preselected: {
      type: Object,
      default: () => {
        return {};
      },
    },
    preselectedText: {
      type: String,
      default: () => {
        return 'Currently selected';
      },
    },
  },
  data() {
    return {
      options: [],
    };
  },
  mounted() {
    this.options = this.packageListOptions;
  },
  computed: {
    ...mapGetters({}),
    selectedPackage: {
      get() {
        return this.value;
      },
      set(v) {
        this.$emit('input', v);
      },
    },
    preselectedPkg() {
      return this.preselected || {};
    },
  },
  methods: {
    filterFn(val, update, abort) {
      update(() => {
        if (val.length < 2) {
          this.options = this.packageListOptions;
        } else {
          const needle = val.toLowerCase();
          this.options = this.packageListOptions.filter((v) => v.name.toLowerCase().indexOf(needle) > -1 || v.versionName.toLowerCase().indexOf(needle) > -1);
        }
      });
    },
  },
  watch: {},
};
</script>

<style lang="scss" scoped>
.q-item {
  &.preselected {
    border-left: 0.2rem solid $positive;
  }
  &.active {
    border-left: 0.2rem solid $primary;
  }
}
</style>
