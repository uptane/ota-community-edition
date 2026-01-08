<template>
  <div class="mb-0">
    <p class="p-0 m-0 text-1  opacity-50">Select version</p>
    <div class="row">
      <div class="col">
        <q-select toggle outlined class="pb-1" :option-value="optionValueFn" :option-label="optionLabelFn" v-model="selectedPackage" :options="options" use-input input-debounce="0" @filter="filterFn" hint="Hint: Start typing to search the list of package versions">
          <template v-if="selectedPackage" v-slot:append>
            <q-icon
              name="cancel"
              @click.stop="
                selectedPackage = null;
                $emit('cleared', null);
              "
              class="cursor-pointer"
            />
          </template>
          <template v-slot:selected>
            <div class="mxw-85">
              <div class="row items-center" v-if="selectedPackage && selectedPackage.versionName">
                <div class="col-auto">
                  <package-version-icon :version="selectedPackage" size="1.2rem"></package-version-icon>
                </div>
                <div class="col">
                  <q-item-label dense square color="white" text-color="primary" class="pl-1 ellipsis">
                    {{ selectedPackage.versionName }}
                  </q-item-label>
                  <q-item-label
                    caption
                    :class="{
                      'text-white': $q.dark.isActive,
                      'text-black': !$q.dark.isActive,
                    }"
                  >
                    <span v-if="preselectedPkg.filepath === selectedPackage.filepath">
                      <q-icon name="star" color="positive" size="1.2rem" class="ml-1" />
                      <span class="opacity-40 mr-1"> {{ preselectedText }}</span>
                    </span>
                  </q-item-label>
                  <q-item-label caption v-if="selectedPackage.isExpired" class="ml-1">
                    <q-icon name="warning" color="warning" size="1rem"></q-icon>
                    <span class="text-warning">
                      "This package comes from an expired source, and cannot currently be installed. Check packages page to refresh."
                    </span>
                  </q-item-label>
                </div>
              </div>
            </div>
          </template>
          <template v-slot:option="scope">
            <q-item
              :disable="scope.opt.isExpired"
              class="pl-1 h-divide-top"
              v-bind="scope.itemProps"
              v-on="scope.itemEvents"
              :class="{
                'text-primary active': scope.itemProps.active,
                preselected: preselectedPkg.filepath === scope.opt.filepath,
              }"
            >
              <q-item-section class="m-0 pr-1" side>
                <package-version-icon :version="scope.opt" size="1.2rem" />
              </q-item-section>
              <q-item-section>
                <q-item-label v-html="scope.opt.versionName" />
                <q-item-label caption
                  ><span v-if="scope.opt.isMajor">(Major release)</span> <span v-else>({{ scope.opt.buildType }} build)</span> {{ $date.formatDate(scope.opt.createdAt, 'ddd MMM DD YYYY, h:mm:ss A') }}
                </q-item-label>
                <q-item-label caption>
                  <div>{{ scope.opt.hash }}</div>
                  <span v-if="preselectedPkg.filepath === scope.opt.filepath">
                    <span>
                      <q-icon name="star" color="positive" size="1.2rem" class="" />
                      <span class="opacity-40"> {{ preselectedText }}</span>
                    </span>
                    <span class="ml-1"> </span>
                  </span>
                  <static-delta-info icon-css-class="text-1" text-css-class="opacity-40" :package-hash="preselectedPkg.hash" :to-hash="scope.opt.hash" one-line></static-delta-info>
                </q-item-label>
                <q-item-label caption v-if="scope.opt.isExpired">
                  <q-icon name="warning" color="warning" size="1rem" class=""></q-icon>
                  <span class="text-warning">
                    "This package comes from an expired source, and cannot currently be installed. Check packages page to refresh."
                  </span>
                </q-item-label>
              </q-item-section>
              <q-item-section avatar>
                <q-icon name="check" v-if="scope.itemProps.active" />
                <q-icon v-else name="check" class="opacity-10" />
              </q-item-section>
            </q-item>
          </template>
        </q-select>
      </div>
    </div>
  </div>
</template>

<script>
import _ from 'lodash';
import { mapGetters } from 'vuex';
import PackageVersionIcon from './PackageVersionIcon.vue';
import StaticDeltaInfo from './StaticDeltaInfo.vue';
export default {
  components: { PackageVersionIcon, StaticDeltaInfo },
  name: 'PackageVersionSelector',
  props: {
    value: {
      type: Object,
      default: () => {
        return {};
      },
    },
    versionList: {
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
    this.options = this.versionList;
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
          this.options = this.versionList;
        } else {
          const needle = val.toLowerCase();
          this.options = this.versionList.filter((v) => v.name.toLowerCase().indexOf(needle) > -1 || v.versionName.toLowerCase().indexOf(needle) > -1 || v.hash.indexOf(needle) > -1);
        }
      });
    },
    optionValueFn(opt) {
      return opt;
    },
    optionLabelFn(opt) {
      return opt.versionName;
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
