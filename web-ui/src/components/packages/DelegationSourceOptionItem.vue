<template>
  <div class="row items-center justify-center">
    <div class="col-auto" style="max-height:2.5rem">
      <q-spinner-hourglass v-if="loading" color="primary" size="1.5rem" class="q-ma-sm" />
      <q-checkbox v-else ref="checkbox" :val="source.val" v-model="delegationSources"> </q-checkbox>
    </div>
    <div
      class="col cursor-pointer"
      @click="$refs.checkbox.toggle()"
      :class="{
        'mr-1': !vertical,
      }"
    >
      <div
        class=" p-0"
        :class="{
          'mt-1': !vertical,
        }"
      >
        {{ source.label }}
      </div>
      <div>
        <small v-if="source.source !== 'user' && canRefresh">
          <div v-if="delegationUpdateFailed" class="text-negative text-caption p-0 m-0">
            <q-icon name="error" class="q-mr-xs"></q-icon>
            Could not refresh from source
            <q-btn no-caps flat dense color="primary" class="text-caption q-ml-xs" icon="sync" padding="0" :loading="syncingDelegation" @click.stop="refreshDelegationSource(source)">
              <tooltip> Refresh again</tooltip>
            </q-btn>
          </div>
          <div v-else-if="delegationUpdateFinished" class="text-positive text-caption p-0 m-0">
            <q-icon name="check_circle" class="q-mr-xs"></q-icon>
            Refresh complete
          </div>
          <span v-else-if="lastFetchedMiliseconds">
            <span class="text-caption p-0 m-0 opacity-50">
              <span v-if="dense"> <q-icon name="refresh"></q-icon>: </span>
              <span v-else>Last refreshed: </span>
              <timeago v-if="now - (lastFetchedMiliseconds || defaultDelegationSyncTimestamp) < tenDays" :refresh="60" :datetime="lastFetchedMiliseconds || defaultDelegationSyncTimestamp" tooltip auto-update></timeago>
              <span v-else>
                <span v-if="!dense">on</span>
                <formatted-date :withTime="false" :date="lastFetchedMiliseconds || defaultDelegationSyncTimestamp"></formatted-date>
              </span>

              <tooltip> Last refreshed <formatted-date :withTime="false" :date="lastFetchedMiliseconds || defaultDelegationSyncTimestamp"></formatted-date> (<timeago :refresh="60" :datetime="lastFetchedMiliseconds || defaultDelegationSyncTimestamp" tooltip></timeago>) </tooltip>
            </span>
            <q-icon v-if="source.isExpired" name="warning" color="warning" size="1.2em">
              <tooltip>
                This package source's metadata has expired. Packages from this source are no longer valid for installation. Click refresh to update.
              </tooltip>
            </q-icon>
            <q-btn no-caps flat dense color="primary" class="text-caption q-ml-xs" icon="sync" padding="0" :loading="syncingDelegation" @click.stop="refreshDelegationSource(source)">
              <tooltip> Refresh now</tooltip>
            </q-btn>
          </span>
          <span v-else class="text-caption">
            <span v-if="!syncingDelegation" @click.stop="refreshDelegationSource(source)" class="text-primary"
              >Refresh now
              <tooltip>
                You may not see any package from this source until you refresh.
              </tooltip>
            </span>
            <span v-else class="faded">Loading ... </span>
          </span>
        </small>
        <template v-else>
          <small v-if="!vertical">&nbsp;</small>
        </template>
      </div>
    </div>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import FormattedDate from '../common/FormattedDate.vue';
import Tooltip from '../common/Tooltip.vue';
import { canAccessFeature } from 'src/config/feature-toggle';
export default {
  components: { Tooltip, FormattedDate },
  name: 'DelegationSourceOptionItem',
  props: {
    source: {
      type: Object,
      required: true,
    },
    dense: {
      type: Boolean,
      default: false,
    },
    vertical: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      tenDays: 10 * 60 * 60 * 24 * 1000,
      syncingDelegation: false,
      delegationUpdateFinished: false,
      delegationUpdateFailed: false,
      loading: false,
    };
  },
  computed: {
    ...mapGetters({
      userSettings: 'ui/userSettings',
      selectedDelegationSources: 'packages/selectedDelegationSources',
    }),
    now() {
      return Date.now();
    },
    defaultDelegationSyncTimestamp() {
      return 1643299577306; // Jan 27, 2022 at 10:06AM CST (nothing special just Date.now() at that very minute)
    },
    delegationSyncTimestamps: {
      get() {
        return this.getUserOptionOrDefault('delegationSyncTimestamps', {});
      },
      set(v) {
        this.setUserOption({ delegationSyncTimestamps: v });
      },
    },
    delegationSources: {
      get() {
        return this.selectedDelegationSources;
      },
      set(v) {
        this.loading = true;
        this.$nextTick(() => {
          // Wait for the checkbox to update
          setTimeout(() => {
            this.saveSelectedDelegations(v).finally(() => {
              this.loading = false;
              // When user selects a source, if there is a url query, remove it
              if (this.$route.query['delegation-sources']) {
                this.$router.replace({ path: this.$route.path, query: { ...this.$route.query, 'delegation-sources': null } });
              }
            });
          }, 50);
        });
      },
    },
    lastFetchedMiliseconds() {
      return this.source.lastFetched ? new Date(this.source.lastFetched).getTime() : null;
    },

    canRefresh() {
      return canAccessFeature('modify-package');
    },
  },
  methods: {
    ...mapActions({
      setUserOption: 'ui/setUserOption',
      refreshDelegations: 'packages/refreshDelegations',
      saveSelectedDelegations: 'packages/saveSelectedDelegations',
    }),
    getUserOptionOrDefault(optionName, defaultValue) {
      const userSettings = this.userSettings[optionName];
      return typeof userSettings !== 'undefined' ? userSettings : defaultValue;
    },
    refreshDelegationSource() {
      this.delegationUpdateFailed = false;
      this.syncingDelegation = true;
      this.refreshDelegations({ delegation: this.source })
        .then((result) => {
          this.$set(this.delegationSyncTimestamps, this.source.name, Date.now());
          this.delegationSyncTimestamps = this.delegationSyncTimestamps;
          this.delegationUpdateFinished = true;
          setTimeout(() => {
            this.delegationUpdateFinished = false;
          }, 3000);
        })
        .catch((err) => {
          this.delegationUpdateFailed = true;
        })
        .finally(() => {
          this.syncingDelegation = false;
        });
    },
  },
};
</script>

<style></style>
