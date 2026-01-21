<!--
  This file is part of the Meeds project (https://meeds.io/).

  Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation; either
  version 3 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
-->
<template>
  <v-data-table
    ref="dataTable"
    :headers="headers"
    :items="items"
    :items-per-page="pageSize"
    :loading="loading"
    :options.sync="options"
    :locale="lang"
    hide-default-footer
    disable-pagination
    disable-filtering
    class="analytics-table border-box-sizing px-2 application-body">
    <template
      v-for="header in headers"
      #[`item.${header.value}`]="{item}">
      <analytics-table-cell
        :key="header.value"
        :header="header"
        :labels="labels"
        :cell-value-extensions="cellValueExtensions"
        :item="item" />
    </template>
    <template v-if="hasMore" slot="footer">
      <v-flex class="d-flex py-2 border-box-sizing">
        <v-btn
          :loading="loading"
          :disabled="loading"
          class="btn mx-auto"
          @click="limit += pageSize">
          {{ $t('analytics.loadMore') }}
        </v-btn>
      </v-flex>
    </template>
  </v-data-table>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: function() {
        return null;
      },
    },
    period: {
      type: Object,
      default: function() {
        return null;
      },
    },
    selectedIdentity: {
      type: Object,
      default: function() {
        return null;
      },
    },
    limit: {
      type: Number,
      default: 20
    },
    pageSize: {
      type: Number,
      default: 20
    },
    userFields: {
      type: Array,
      default: function() {
        return null;
      },
    },
    spaceFields: {
      type: Array,
      default: function() {
        return null;
      },
    },
    retrieveTableDataUrl: {
      type: Array,
      default: function() {
        return null;
      },
    },
  },
  data: () => ({
    lang: eXo.env.portal.language,
    options: {},
    items: [],
    loading: false,
    sortBy: null,
    sortDirection: null,
    extensionApp: 'AnalyticsTable',
    cellValueExtensionType: 'CellValue',
    cellValueExtensions: {},
  }),
  computed: {
    hasMore() {
      return (this.loading && this.limit > this.pageSize) || (this.items.length && this.limit === this.items.length);
    },
    mainFieldName() {
      return this.settings && this.settings.mainColumn && this.settings.mainColumn.valueAggregation
        && this.settings.mainColumn.valueAggregation.aggregation
        && this.settings.mainColumn.valueAggregation.aggregation.field;
    },
    mainFieldValues() {
      return this.items.map(item => item.column0 && item.column0.key);
    },
    headers() {
      if (!this.settings) {
        return [];
      }
      const headers = [];
      if (this.settings.mainColumn) {
        this.addHeader(headers, this.settings.mainColumn, 0);
      }
      if (this.settings.columns) {
        this.settings.columns.forEach((column, index) => {
          this.addHeader(headers, column, (index +1));
        });
      }
      return headers;
    },
    labels() {
      return {
        CancelRequest: this.$t('spacesList.label.profile.CancelRequest'),
        Confirm: this.$t('spacesList.label.profile.Confirm'),
        Connect: this.$t('spacesList.label.profile.Connect'),
        Ignore: this.$t('spacesList.label.profile.Ignore'),
        RemoveConnection: this.$t('spacesList.label.profile.RemoveConnection'),
        StatusTitle: this.$t('spacesList.label.profile.StatusTitle'),
        External: this.$t('UsersManagement.type.external'),
        Disabled: this.$t('UsersManagement.status.disabled'),
        join: this.$t('spacesList.button.join'),
        leave: this.$t('spacesList.button.leave'),
        members: this.$t('peopleList.label.filter.member'),
      };
    },
  },
  watch: {
    selectedIdentity() {
      this.items = [];
      this.refresh();
    },
    period() {
      this.items = [];
      this.$nextTick().then(() => this.refresh());
    },
    limit() {
      this.refresh();
    },
    options: {
      handler () {
        this.items = [];
        this.refresh();
      },
      deep: true,
    },
    settings: {
      immediate: true,
      handler () {
        if (this.settings && !this.sortBy) {
          this.options.sortBy = [`column${this.settings.sortBy}`];
          this.options.sortDesc = [this.settings && this.settings.sortDirection || 'desc'];
        }
      },
    },
  },
  created() {
    document.addEventListener(`extension-${this.extensionApp}-${this.cellValueExtensionType}-updated`, this.refreshCellValueExtensions);
    this.refreshCellValueExtensions();
  },
  methods: {
    refreshCellValueExtensions() {
      const extensions = extensionRegistry.loadExtensions(this.extensionApp, this.cellValueExtensionType);
      let changed = false;
      extensions.forEach(extension => {
        if (extension.type && extension.options && (!this.cellValueExtensions[extension.type] || this.cellValueExtensions[extension.type] !== extension.options)) {
          this.cellValueExtensions[extension.type] = extension.options;
          changed = true;
        }
      });
      // force update of attribute to re-render switch new extension type
      if (changed) {
        this.cellValueExtensions = Object.assign({}, this.cellValueExtensions);
      }
    },
    addHeader(headers, column, index) {
      headers.push({
        text: column.title && this.$t(column.title) || '',
        align: column.align || 'center',
        sortable: column.sortable,
        value: `column${index}`,
        class: 'text-no-wrap',
        width: column.width || 'auto',
        dataType: column.dataType || 'text',
        column
      });
    },
    refresh() {
      if (!this.settings) {
        return;
      }
      if (this.selectedIdentity) {
        this.sortBy = 0;
      } else {
        const { sortBy, sortDesc } = this.options;
        this.sortBy = sortBy.length && this.headers.findIndex(header => header.value === sortBy[0]) || 0;
        if (this.sortBy < 0) {
          this.sortBy = 0;
        }
        this.sortDirection = sortDesc[0] && 'desc' || 'asc';
      }

      if (this.settings.mainColumn) {
        this.loading = true;
        this.refreshColumn(this.sortBy, this.limit, this.sortDirection)
          .then(() => {
            if (this.settings.columns) {
              const columnsLength = this.settings.columns.length + 1;
              for (let i  = 1; i < columnsLength; i++) {
                if (this.sortBy !== i) {
                  this.refreshColumn(i);
                }
              }
            }
          })
          .finally(() => this.loading = false);
      }
    },
    refreshColumn(columnIndex, limit, sort) {
      const column = columnIndex === 0 && this.settings.mainColumn || this.settings.columns[columnIndex-1];
      column.loading = true;
      const templateItem = {};
      this.headers.forEach(header => {
        templateItem[header.value] = {value: 'loadingData'};
      });
      const columnAggregationField = column.valueAggregation && column.valueAggregation.aggregation && column.valueAggregation.aggregation.field;
      const columnAggregationType = column.valueAggregation && column.valueAggregation.aggregation && column.valueAggregation.aggregation.type;
      if (this.selectedIdentity && columnIndex === 0 && columnAggregationField === 'userId' && columnAggregationType === 'TERMS') {
        return this.$identityService.getIdentityByProviderIdAndRemoteId(this.selectedIdentity.providerId, this.selectedIdentity.remoteId, 'all')
          .then(identity => {
            const user = identity.profile;
            if (user) {
              const item = Object.assign({}, templateItem);
              item[`column${columnIndex}`] = {
                key: user && user.id,
                value: user && user.id,
                identity: user && user.profile,
              };
              this.items.push(item);
            }
          });
      } else if (this.selectedIdentity && columnIndex === 0 && columnAggregationField === 'spaceId' && columnAggregationType === 'TERMS') {
        return this.$identityService.getIdentityByProviderIdAndRemoteId(this.selectedIdentity.providerId, this.selectedIdentity.remoteId)
          .then(identity => {
            const space = identity && identity.space;
            if (space) {
              const item = Object.assign({}, templateItem);
              item[`column${columnIndex}`] = {
                key: space && space.id,
                value: space && space.id,
                identity: space,
              };
              this.items.push(item);
            }
          });
      } else if (!column.userField && !column.spaceField && column.valueAggregation && column.valueAggregation.aggregation) {
        const params = {
          lang: eXo.env.portal.language && eXo.env.portal.language.replace('_','-'),
          column: columnIndex,
          limit: String(limit || 0),
          periodType: this.period.period || '',
          min: this.period.min,
          max: this.period.max + 60000,
          timeZone: this.$analyticsUtils.USER_TIMEZONE_ID,
        };
        if (sort) {
          params.sort = sort;
        } else if (this.mainFieldName) {
          params.fieldFilter = this.mainFieldName;
          params.fieldValues = this.mainFieldValues.join(',');
        }
        params.timeZone = this.$analyticsUtils.USER_TIMEZONE_ID;
        return fetch(this.retrieveTableDataUrl, {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
          body: $.param(params),
        })
          .then((resp) => {
            if (resp && resp.ok) {
              return resp.json();
            } else {
              throw new Error('Error getting analytics with settings:', this.settings);
            }
          })
          .then((data) => {
            if (limit) { // First retrieved column, coul be main or sorted column
              if (data && data.items && data.items.length) {
                if (this.sortBy) {
                  this.items = [];
                }
                data.items.forEach((columnItem, index) => {
                  const item = (this.items.length <= index || !this.items.length) && Object.assign({}, templateItem) || this.items[index][`column${columnIndex}`];
                  item[`column${columnIndex}`] = columnItem;
                  // Set list of main column values
                  item['column0'] = {
                    key: columnItem.key,
                    value: columnItem.key,
                    identity: null, // will be retrieved from corresponding component
                  };
                  if (this.items.length <= index) {
                    this.items.push(item);
                  }
                });
              }
            } else {
              this.items.forEach((item, index) => {
                const columnItem = data && data.items && data.items.find(item => item.key === this.mainFieldValues[index]);
                item[`column${columnIndex}`] = columnItem;
              });
            }
          })
          .catch((e) => {
            console.error('fetch analytics - error', e);
            this.error = 'Error getting analytics';
            this.items.forEach(item => {
              item[`column${columnIndex}`] = {value: 'errorRetrievingData'};
            });
          })
          .finally(() => column.loading = false);
      } else {
        this.items.forEach(item => {
          if (!item[`column${columnIndex}`]) {
            item[`column${columnIndex}`] = {
              key: item['column0'] && item['column0'].key,
            };
          } else if (item[`column${columnIndex}`].value === 'loadingData') {
            item[`column${columnIndex}`].value = null;
          }
        });
        return Promise.resolve();
      }
    },
  },
};
</script>
