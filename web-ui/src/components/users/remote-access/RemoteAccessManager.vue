<template>
  <div class="row p-1">
    <div class="col-12 mb-1">
      <active-remote-sessions class="q-card p-2 row" />
    </div>
    <div class="col-12 mb-1">
      <ssh-key-entry class="q-card p-2 row" />
    </div>
    <div class="col-12 mb-1">
      <ssh-key-list class="q-card p-2 row" />
    </div>
    <div v-if="!showMore" class="col-12 mb-1">
      <q-btn flat color="primary" label="Show More" @click="showMore = !showMore" class="q-mb-md" />
    </div>
    <template v-if="showMore">
      <div class="col-12">
        <ip-accept-list-manager class="q-card p-2 row" />
      </div>
    </template>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import FormattedDate from '../../common/FormattedDate.vue';
import MaskedText from '../../common/masked-text.vue';
import TextCopy from '../../common/TextCopy.vue';
import Tooltip from '../../common/Tooltip.vue';
import { copyToClipboard } from 'quasar';
import SshKeyEntry from './SshKeyEntry.vue';
import SshKeyList from './SshKeyList.vue';
import ActiveRemoteSessions from './ActiveRemoteSessions.vue';
import IpAcceptListManager from './IpAcceptListManager.vue';

export default {
  components: { FormattedDate, Tooltip, MaskedText, TextCopy, SshKeyEntry, SshKeyList, ActiveRemoteSessions, IpAcceptListManager },
  name: 'ApiClientManagerPanel',
  data() {
    return {
      showMore: false,
    };
  },
  methods: {},
  computed: {},
  watch: {},
  mounted() {},
};
</script>
