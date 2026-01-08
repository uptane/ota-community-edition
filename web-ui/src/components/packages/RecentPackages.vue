<template>
  <div>
    <q-card class="list-view">
      <q-card-section class=" row text-center   justify-between items-center">
        <h5 class="col-12 mt-0 mb-0">{{ title }}</h5>
        <feature-teaser class="col-12" feature="create-package">
          <q-btn class="" flat color="secondary" @click="createPackage()">
            <span v-if="$q.screen.gt.sm">Add new package</span>
          </q-btn>
        </feature-teaser>
      </q-card-section>
      <q-linear-progress v-if="loading" indeterminate color="secondary" size="2px" class="" />
      <q-separator v-else class="p-0 m-0" />
      <q-list class="pl-0 pr-0" dense>
        <template v-if="quickViewPackages">
          <template v-for="(pkg, index) of quickViewPackages">
            <template v-if="index < limit">
              <q-separator :key="index + '_'" v-if="index !== 0" class="p-0 m-0" />
              <q-item
                class="pl-1 pr-1 hoverable clickable
              dash-list-item"
                :key="index"
                @click.native="viewPackageDetail(pkg)"
              >
                <package-info :pkg="pkg" truncate>
                  <template slot="additional-content">
                    <div class="q-item-tile sublabel  ellipsis">
                      <strong>Date Uploaded: </strong>
                      <formatted-date :date="pkg.createdAt" :format="'MMM DD, YYYY'"></formatted-date>
                    </div>
                  </template>
                </package-info>
              </q-item>
            </template>
          </template>
          <more-indicator href="#/packages" text="View all packages" :data-length="(quickViewPackages || []).length"></more-indicator>
        </template>
        <template v-if="!loading && (!quickViewPackages || quickViewPackages.length < 1)">
          <q-item>
            <q-item-label>
              <div class="q-item-tile label text-center p-2 opacity-30 "><q-icon class="mr-1" name="info" size="1.3em"></q-icon> Nothing here yet</div>
            </q-item-label>
          </q-item>
        </template>
        <template v-if="loading">
          <template v-for="index in 7">
            <q-separator :key="index + '_recent_pkg_'" class="p-0 m-0" />
            <q-item :key="index + '_recent_pkg'" class="mnh-5em q-my-md">
              <q-item-section avatar>
                <q-skeleton type="QAvatar" />
              </q-item-section>

              <q-item-section>
                <q-item-label>
                  <q-skeleton height="17px" type="text" />
                </q-item-label>
                <q-item-label caption>
                  <q-skeleton height="10px" type="text" />
                </q-item-label>
                <q-item-label caption>
                  <q-skeleton height="15px" type="text" />
                </q-item-label>
              </q-item-section>
            </q-item>
          </template>
        </template>
      </q-list>
    </q-card>
  </div>
</template>

<script>
import Empty from '../common/Empty.vue';
import { mapActions, mapGetters } from 'vuex';
import MoreIndicator from '../common/MoreIndicator';
import PackageVersionIcon from '../packages/PackageVersionIcon';
import FormattedDate from '../common/FormattedDate.vue';
import gtm from '../../services/gtm.service';
import PackageInfo from './PackageInfo.vue';

export default {
  name: 'ComponentPackages',
  components: {
    Empty,
    MoreIndicator,
    PackageVersionIcon,
    FormattedDate,
    PackageInfo,
  },
  props: {
    title: {
      type: String,
      default: 'Package List',
    },
    limit: {
      type: Number,
      default: 50,
    },
  },

  data() {
    return {};
  },
  created() {},
  mounted() {},
  watch: {},
  computed: {
    ...mapGetters({
      packagesByIdAndHash: 'packages/packagesByIdAndHash',
      packagesByHash: 'packages/packagesByHash',
      torizonPackagesByHash: 'packages/torizonPackagesByHash',
      userPackagesByHash: 'packages/userPackagesByHash',
      loading: 'ui/loadingPackages',
    }),
    quickViewPackages() {
      return _.sortBy(this.packagesByIdAndHash, (s) => s.createdAt).reverse();
    },
  },
  methods: {
    ...mapActions({}),
    createPackage(pkg) {
      this.$events.$emit('dialogs:create-package:open', { show: true, existing: pkg });
      gtm.logEvent('Dashboard', 'click', 'Create Package', null);
    },

    viewPackageDetail(pkg) {
      this.$router.push({ name: 'packages', query: { name: pkg.name } });
    },
  },
};
</script>
