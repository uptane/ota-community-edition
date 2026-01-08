<template>
  <q-card
    class="device-pagination mt-1 fixed-bottom-right shadow-7"
    :style="{
      'margin-right': paginationRightPadding,
      'z-index': 1000,
    }"
  >
    <q-linear-progress v-if="loading" indeterminate size="2px" color="secondary" class="" />
    <div class="row  q-table__bottom q-table__bottom ">
      <div class="q-table__control">
        <q-spinner-hourglass v-if="loading" size="1.5em" color="secondary" class="q-mr-sm" />
        <span class="q-table__bottom-item">{{ recordsPerPageLabel }}</span>
        <q-select borderless dense options-dense map-options :value="pagination.limit" @input="setLimit" emit-value :options="limitOptionsMap" />
        <span class="q-table__bottom-item">{{ showingFrom }}-{{ showingTo }} of {{ total }}</span>
        <q-btn
          dense
          flat
          icon="first_page"
          :disable="!hasPrevPage"
          :class="{
            faded: !hasNextPage,
          }"
          @click="firstPage"
        ></q-btn>
        <q-btn
          dense
          flat
          icon="keyboard_arrow_left"
          :disable="!hasPrevPage"
          :class="{
            faded: !hasNextPage,
          }"
          @click="prevPage"
        ></q-btn>
        <q-btn
          dense
          flat
          icon="keyboard_arrow_right"
          :disable="!hasNextPage"
          :class="{
            faded: !hasNextPage,
          }"
          @click="nextPage"
        >
        </q-btn>
        <q-btn
          dense
          flat
          icon="last_page"
          :disable="!hasNextPage"
          :class="{
            faded: !hasNextPage,
          }"
          @click="lastPage"
        ></q-btn>
      </div>
    </div>
  </q-card>
</template>

<script>
import { mapGetters } from 'vuex';

export default {
  name: 'ListPagination',

  props: {
    total: {
      type: Number,
      default: 0,
    },
    pagination: {
      type: Object,
      default: () => ({
        sortBy: 'name',
        descending: true,
        page: 1,
        limit: 50,
        offset: 0,
        rowsPerPage: 0,
      }),
    },
    limitOptions: {
      type: Object,
      default: () => null,
    },
    recordsPerPageLabel: {
      type: String,
      default: 'Records per page',
    },
    warningAcknowledged: {
      type: Boolean,
      default: false,
    },
    loading: {
      type: Boolean,
      default: false,
    },
  },

  computed: {
    ...mapGetters({
      currentPageDimensions: 'ui/currentPageDimensions',
      leftMenuSize: 'ui/leftMenuSize',
    }),
    showingFrom() {
      return this.total > 0 ? this.offset + 1 : 0;
    },
    showingTo() {
      let val = this.offset + this.limit;
      if (val > this.total) {
        val = this.total;
      }
      return val;
    },
    hasNextPage() {
      return this.page < this.totalPages;
    },
    hasPrevPage() {
      return this.page > 1;
    },
    isFirstPage() {
      return this.page === 1;
    },
    isLastPage() {
      return this.page === this.totalPages;
    },
    totalPages() {
      return Math.ceil(this.total / this.limit);
    },
    page: {
      get() {
        // convert offset to page
        const page = Math.ceil(this.offset / this.limit) + 1;
        return page > 0 ? page : 1;
      },
      set(v) {
        // convert page to offset
        const offset = (v - 1) * this.limit;
        this.setPaginationProperty('offset', offset);
      },
    },
    limit: {
      get() {
        return this.pagination.limit;
      },
      set(v) {
        this.setPaginationProperty('limit', v);
      },
    },

    offset: {
      get() {
        return this.pagination.offset;
      },
      set(v) {
        this.setPaginationProperty('offset', v);
      },
    },

    paginationRightPadding() {
      let clientWidth = window.innerWidth;
      let dOffset = clientWidth - this.currentPageDimensions.width - this.leftMenuSize.width + 25;
      return dOffset + 'px';
    },

    limitOptionsMap() {
      if (this.limitOptions && this.limitOptions.length > 0) {
        return this.limitOptions;
      }
      let rppo = [{ label: '10', value: 10 }, { label: '20', value: 20 }, { label: '50', value: 50 }, { label: '100', value: 100 }, { label: '200', value: 200 }];
      if (this.total > 200 && this.total <= 500) {
        rppo.push({ label: 'All', value: 0 });
      } else if (this.total > 500) {
        rppo.push({ label: '500', value: 500 });
      }
      return rppo;
    },
  },

  methods: {
    setPaginationProperty(property, value) {
      let pagination = JSON.parse(JSON.stringify(this.pagination)); // deep copy
      pagination[property] = value;
      this.updatePagination(pagination);
    },
    setLimit(limit) {
      this.setPaginationProperty('limit', limit);
    },
    updatePagination(pagination) {
      return new Promise((resolve, reject) => {
        if (pagination.limit === 0 && !this.warningAcknowledged) {
          this.$q
            .dialog({
              title: 'Warning!',
              message: 'Displaying all records at once may impact performance of the app. Are you sure you want to continue?',
              options: {
                type: 'checkbox',
                model: [],
                items: [
                  {
                    label: "Don't show this warning again.",
                    value: true,
                    color: 'default',
                  },
                ],
              },
              ok: {
                flat: true,
                label: 'Yes, I understand',
                color: 'primary',
              },
              cancel: {
                flat: true,
                label: 'No',
                color: 'default',
              },
            })
            .onOk((data) => {
              this.$emit('update:warningAcknowledged', data[0]);
              pagination.offset = 0;
              pagination.limit = this.total;

              this.$emit('update:pagination', { ...pagination, offset: 0, limit });
              resolve(pagination);
            })
            .onCancel(() => {
              reject(pagination);
            })
            .onDismiss(() => {
              reject(pagination);
            });
        } else {
          let offset = pagination.offset || 0;
          // let offset = (pagination.page - 1) * pagination.limit;
          if (offset < 0) offset = 0;
          if (this.total % offset < 1) offset = this.total % offset;
          // if the total is less than offset, set the offset to 0
          if (this.total < pagination.offset) {
            offset = 0;
          }
          // If the total is less than 1 and the offset is greater than 0, set the offset to 0
          if (this.total < 1 && offset > 0) {
            offset = 0;
          }
          this.$emit('update:pagination', { ...pagination, offset });
          resolve(pagination);
        }
      });
    },
    prevPage() {
      this.page--;
      if (this.page < 0) {
        this.page = 0;
      }
    },
    nextPage() {
      this.page++;
      if (this.page > this.totalPages - 1) {
        this.page = this.totalPages - 1;
      }
    },
    firstPage() {
      this.page = 0;
    },
    lastPage() {
      this.page = this.totalPages;
    },
  },

  mounted() {},
};
</script>
