<!--
  This file is part of the Meeds project (https://meeds.io/).
  Copyright (C) 2022 Meeds Association
  contact@meeds.io
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
  <exo-drawer
    ref="tableSettingDrawer"
    :drawer-width="drawerWidth"
    class="analytics-table-settings"
    allow-expand
    right>
    <template slot="title">
      {{ $t('analytics.settings') }}
    </template>
    <template slot="content">
      <v-card-text v-if="tableSettings">
        <v-subheader class="px-0">
          <h4 class="mt-0">{{ $t('analytics.limit') }}</h4>
        </v-subheader>
        <v-text-field
          v-model="tableSettings.pageSize"
          :label="$t('analytics.limit')"
          :placeholder="$t('analytics.limit')"
          class="pt-2"
          type="number"
          outlined
          required />
        <v-subheader class="px-0">
          <h4 class="mt-0">{{ $t('analytics.mainColumn') }}</h4>
        </v-subheader>
        <v-text-field
          v-model="tableSettings.mainColumn.title"
          :label="$t('analytics.tableMainColumnTitle')"
          :placeholder="$t('analytics.tableMainColumnTitlePlaceholder')"
          class="pt-2"
          outlined
          required />
        <analytics-field-selection
          v-model="tableSettings.mainColumn.valueAggregation.aggregation.field"
          :fields-mappings="fieldsMappings"
          :label="$t('analytics.fieldName')"
          attach
          aggregation
          @change="updateMainColumnDataType()" />
        <v-list nav>
          <v-subheader class="px-0">
            <h4>{{ $t('analytics.tableColumns') }}</h4>
            <v-btn
              color="primary"
              icon
              @click="addColumn">
              <v-icon>fa-plus-circle</v-icon>
            </v-btn>
          </v-subheader>
          <v-list-item-group v-model="selectedColumn">
            <analytics-table-column-setting
              v-for="(column, index) in columns"
              :key="index"
              :fields-mappings="fieldsMappings"
              :main-column="mainColumn"
              :column="column"
              :user-fields="userFields"
              :space-fields="spaceFields"
              class="mb-0"
              @delete="deleteColumn(index)" />
          </v-list-item-group>
        </v-list>
      </v-card-text>
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <button class="btn ignore-vuetify-classes me-1" @click="$refs.tableSettingDrawer.close()">
          {{ $t('analytics.close') }}
        </button>
        <button class="btn btn-primary ignore-vuetify-classes  ml-1" @click="save">
          {{ $t('analytics.save') }}
        </button>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  props: {
    retrieveMappingsUrl: {
      type: String,
      default: function() {
        return null;
      },
    },
    settings: {
      type: Object,
      default: function() {
        return null;
      },
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
  },
  data() {
    return {
      tableSettings: null,
      fieldsMappings: [],
      dialog: false,
      selectedColumn: 1,
      drawerWidth: '650px',
      tab: 0,
    };
  },
  computed: {
    columns() {
      return this.tableSettings && this.tableSettings.columns || [];
    },
    mainColumn() {
      return this.tableSettings && this.tableSettings.mainColumn;
    },
  },
  mounted() {
    this.init();
  },
  methods: {
    init() {
      this.loading = true;

      return fetch(this.retrieveMappingsUrl, {
        method: 'GET',
        credentials: 'include',
        headers: {
          Accept: 'application/json',
        },
      })
        .then((resp) => {
          if (resp && resp.ok) {
            return resp.json();
          } else {
            throw new Error('Error getting analytics fields mappings:');
          }
        })
        .then((fieldsMappings) => {
          this.fieldsMappings = fieldsMappings;
          return this.$nextTick();
        })
        .then(() => {
          if (this.$refs) {
            Object.keys(this.$refs).forEach(refKey => {
              const component = this.$refs[refKey];
              if (component && component.init) {
                component.init();
              }
            });
          }
        })
        .catch((e) => {
          console.error('fetch analytics - error', e);
          this.error = 'Error getting analytics';
        })
        .finally(() => this.loading = false);
    },
    addColumn() {
      this.columns.push({
        valueAggregation: {
          aggregation: {
            type: 'COUNT',
          },
          filters: [],
        },
      });
    },
    deleteColumn(index) {
      this.columns.splice(index, 1);
      this.selectedColumn = 0;
    },
    open() {
      const settings = this.settings || {};
      const tableSettings = {
        pageSize: settings.pageSize || 20,
        title: settings.title || '',
        mainColumn: {
          title: '',
          valueAggregation: {
            aggregation: {},
            filters: [],
          },
        },
        columns: [],
      };
      if (settings.mainColumn) {
        if (settings.mainColumn.title) {
          tableSettings.mainColumn.title = settings.mainColumn.title;
        }
        if (settings.mainColumn.valueAggregation && settings.mainColumn.valueAggregation.aggregation) {
          tableSettings.mainColumn.valueAggregation.aggregation = {
            field: settings.mainColumn.valueAggregation.aggregation.field || null,
            type: settings.mainColumn.type || 'TERMS',
            dataType: settings.mainColumn.dataType || 'text',
          };
        }
        if (settings.sortBy) {
          tableSettings.sortBy = settings.sortBy;
        }
        if (settings.sortDirection) {
          tableSettings.sortDirection = settings.sortDirection;
        }
        if (settings.mainColumn.valueAggregation && settings.mainColumn.valueAggregation.filters && settings.mainColumn.valueAggregation.filters.length) {
          tableSettings.mainColumn.valueAggregation.filters = settings.mainColumn.valueAggregation.filters;
        }
      }
      if (settings.columns && settings.columns.length) {
        settings.columns.forEach(column => {
          const columnSettings = {
            title: column.title || '',
            previousPeriod: column.previousPeriod || false,
            userField: column.userField || '',
            spaceField: column.spaceField || '',
            valueAggregation: {
              aggregation: column && column.valueAggregation && column.valueAggregation.aggregation || {},
              filters: column && column.valueAggregation && column.valueAggregation.filters || [],
              periodIndependent: column && column.valueAggregation && column.valueAggregation.periodIndependent || false,
            },
            sortable: column.sortable || false,
            dataType: column.dataType || 'text',
          };
          if (!columnSettings.userField && !columnSettings.spaceField) {
            if (column.valueAggregation) {
              columnSettings.valueAggregation = column.valueAggregation;
            }
            if (column.thresholdAggregation) {
              columnSettings.thresholdAggregation = column.thresholdAggregation;
            }
          }
          tableSettings.columns.push(columnSettings);
        });
      }
      this.tableSettings = tableSettings;
      this.selectedColumn = 0;
      this.$refs.tableSettingDrawer.open();
    },
    updateMainColumnDataType() {
      const mainColumn = this.tableSettings.mainColumn;
      const mainColumnValueAggregation = mainColumn && mainColumn.valueAggregation;
      const mainColumnAggregation = mainColumnValueAggregation && mainColumnValueAggregation.aggregation;
      if (mainColumnAggregation) {
        mainColumnAggregation.type = 'TERMS';
        mainColumn.dataType = 'text';
        if (mainColumnAggregation.field) {
          const columnMaping = this.fieldsMappings.find(mapping => mapping && mapping.name === mainColumnAggregation.field);
          mainColumn.dataType = columnMaping && columnMaping.type;
        }

        mainColumn.sortable = false;
        mainColumn.userField = null;
        mainColumn.spaceField = null;

        if (mainColumn.dataType === 'long' || mainColumnAggregation.field === 'userId' || mainColumnAggregation.field === 'spaceId') {
          mainColumnValueAggregation.filters = [{
            field: mainColumnAggregation.field,
            type: 'GREATER',
            valueString: '1'
          }];
        }
      }
    },
    save() {
      this.$emit('save', this.tableSettings);
      this.$refs.tableSettingDrawer.close();
    },
  },
};
</script>
