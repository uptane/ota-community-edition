<template>
  <q-card
    @click.native="showItemDatail()"
    class=" item-wrapper hoverable clickable overflow-hidden animated fadeIn cursor-pointer non-selectable"
    :class="{
      'for-layout-cards': layoutType === 'cards',
      'for-layout-list': layoutType !== 'cards',
      'for-view-type-thick': viewType === 'thick',
      selected: selected,
      'for-view-type-thin no-border-radius shadow-1': viewType === 'thin',
    }"
    :style="{
      width: $q.screen.lt.md || layoutType !== 'cards' || isThinOrThickItem ? (isThinOrThickItem ? '100%' : 'auto') : viewSize + 'rem',
    }"
  >
    <q-tooltip v-if="viewType === 'thin'" content-class="bg-black" transition-hide="" transition-show="">
      <div class="text-bold">{{ item.name }}</div>
      <div class="opacity-50">Size: {{ item.size }}</div>
      <div class="opacity-50">Hash: {{ item.hash }}</div>
    </q-tooltip>
    <q-item :style="{}" :class="{ 'shadow-0': viewType === 'thin' }">
      <q-item-section :class="{ relaxed: viewType === 'card' }">
        <q-item-label class="">
          <div class="q-item-tile label item-name ellipsis">{{ item.name }}</div>
          <div
            class="q-item-tile sublabel item-id ellipsis"
            :class="{
              ' w-80': isListLayout || isThinItem,
            }"
          >
            hash: {{ item.hash }}
          </div>
          <div v-if="!isThinItem || isListLayout">
            <div class="row q-item-tile text-ellipsis ">
              <div class="col-auto sublabel ">Status: &nbsp;</div>
              <span v-if="item.revoked" class="text-negative">Revoked</span>
              <span v-else-if="item.expired" class="text-warning">Expired</span>
              <span v-else>Active</span>
            </div>
          </div>
          <div v-if="!isThinItem || isListLayout">
            <div v-if="!item.revoked && !item.expired && item.expires" class="row q-item-tile sublabel last-seen text-ellipsis ">
              <div class="col-auto">Expires: &nbsp;</div>
              <timeago class="col w-70 ellipsis" :datetime="item.expires" :auto-update="10"></timeago>
            </div>
          </div>
        </q-item-label>
      </q-item-section>
    </q-item>
  </q-card>
</template>

<script>
import Loader from '../loaders/Loader';
import FormattedDate from '../common/FormattedDate.vue';
import { format } from 'quasar';
const { humanStorageSize } = format;

export default {
  name: 'UpdateItem',
  components: {
    Loader,
    FormattedDate,
  },
  props: {
    item: {
      type: Object,
      default: () => {
        return {};
      },
    },
    layoutType: {
      type: String,
      default: 'list',
    },
    viewType: {
      type: String,
      default: 'thick',
    },
    viewSize: {
      type: Number,
      default: 20,
    },
    selected: {
      type: Boolean,
      default: false,
    },
    hideIcon: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {};
  },
  created() {},
  mounted() {},
  beforeDestroy() {},
  computed: {
    deviceDeleteInProgress() {},
    isThinItem() {
      return this.viewType === 'thin';
    },
    isThickItem() {
      return this.viewType === 'thick';
    },
    isThinOrThickItem() {
      return this.isThinItem || this.isThickItem;
    },
    isListLayout() {
      return this.layoutType === 'list';
    },
    deviceUpdating() {
      return this.$store.getters['ui/devicesWithUpdateInProgress'][(this.item || {}).name];
    },
  },
  methods: {
    showItemDatail() {
      this.$emit('item-click', this.item);
    },
    humanStorageSize,
  },
};
</script>
