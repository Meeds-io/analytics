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
  <v-list-group active-class="primary white--text border-radius border-color mb-0" dark>
    <template #activator>
      <v-list-item-title v-if="columnTitle">{{ $t(columnTitle) }}</v-list-item-title>
      <v-list-item-title v-else class="font-italic subtitle-1 text-sub-title">{{ $t('analytics.columnTitlePlaceholder') }}</v-list-item-title>
    </template>
    <div class="border-radius border-color pa-2">
      <template v-if="canEnableIdentityFields">
        <v-switch
          v-model="useIdentityField"
          :label="useIdentityFieldLabel"
          class="my-auto text-no-wrap" />
        <template v-if="useIdentityField">
          <analytics-identity-field-selection
            v-if="useUserField"
            v-model="column.userField"
            :items="userFields"
            :label="$t('analytics.userField')" />
          <analytics-identity-field-selection
            v-else-if="useSpaceField"
            v-model="column.spaceField"
            :items="spaceFields"
            :label="$t('analytics.spaceField')" />
        </template>
      </template>
      <template v-if="!canEnableIdentityFields || !useIdentityField">
        <v-text-field
          v-model="column.title"
          :label="$t('analytics.columnTitle')"
          outlined
          required />
        <v-switch
          v-show="!isTermsAggregation && !isPeriodIndependent"
          v-model="column.previousPeriod"
          :label="$t('analytics.compareWithPreviousPeriod')"
          class="my-2 text-no-wrap" />
        <analytics-table-column-aggregation-setting
          :column-aggregation="column.valueAggregation"
          :fields-mappings="fieldsMappings"
          @change="updateColumnDataType('aggregation')" />
        <template v-if="!isTermsAggregation">
          <v-switch
            v-model="computeThreshold"
            :label="$t('analytics.definePercentageThreshold')"
            class="my-2 text-no-wrap" />
          <analytics-table-column-aggregation-setting
            v-if="displayThresholdComuting"
            :column-aggregation="column.thresholdAggregation"
            :fields-mappings="fieldsMappings"
            class="ps-10 ml-2" />
        </template>
      </template>
      <v-btn
        color="error"
        class="pa-0"
        outlined
        @click="$emit('delete')">
        <i class="uiIconTrash pb-1 pe-2"></i>
        {{ $t('analytics.removeColumnSetting') }}
      </v-btn>
    </div>
  </v-list-group>
</template>
<script>
export default {
  props: {
    mainColumn: {
      type: Object,
      default: function() {
        return null;
      },
    },
    column: {
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
    fieldsMappings: {
      type: Array,
      default: function() {
        return null;
      },
    },
  },
  data: () => ({
    useIdentityField: false,
    computeThreshold: false,
    aggregationType: 'MAX',
    limitResults: false,
  }),
  computed: {
    columnTitle() {
      return this.column && this.column.title;
    },
    canEnableIdentityFields() {
      return this.useUserField || this.useSpaceField;
    },
    mainColumnField() {
      return this.mainColumn && this.mainColumn.valueAggregation
        && this.mainColumn.valueAggregation.aggregation
        && this.mainColumn.valueAggregation.aggregation.field;
    },
    userField() {
      return this.column.userField;
    },
    spaceField() {
      return this.column.spaceField;
    },
    useUserField() {
      return this.mainColumnField === 'userId';
    },
    useSpaceField() {
      return this.mainColumnField === 'spaceId';
    },
    useIdentityFieldLabel() {
      if (this.useUserField) {
        return this.$t('analytics.displayUserField');
      } else if (this.useSpaceField) {
        return this.$t('analytics.displaySpaceField');
      }
      return null;
    },
    displayThresholdComuting() {
      return this.computeThreshold && this.column.thresholdAggregation;
    },
    aggregationTypes() {
      return [
        {
          text: this.$t('analytics.perCustomField'),
          value: 'TERMS',
        },
        {
          text: this.$t('analytics.count'),
          value: 'COUNT',
        },
        {
          text: this.$t('analytics.cardinality'),
          value: 'CARDINALITY',
        },
        {
          text: this.$t('analytics.sum'),
          value: 'SUM',
        },
        {
          text: this.$t('analytics.avg'),
          value: 'AVG',
        },
        {
          text: this.$t('analytics.max'),
          value: 'MAX',
        },
        {
          text: this.$t('analytics.min'),
          value: 'MIN',
        },
      ];
    },
    isTermsAggregation() {
      return this.aggregationType === 'TERMS';
    },
    isPeriodIndependent() {
      return this.column && this.column.valueAggregation && this.column.valueAggregation.periodIndependent;
    },
    numericAggregationField() {
      return this.aggregationType !== 'CARDINALITY' && this.aggregationType !== 'TERMS';
    },
  },
  watch: {
    isPeriodIndependent() {
      if (this.isPeriodIndependent) {
        this.column.previousPeriod = false;
      }
    },
    isTermsAggregation() {
      if (this.isTermsAggregation) {
        this.computeThreshold = false;
      }
    },
    canEnableIdentityFields() {
      if (!this.canEnableIdentityFields && !this.useIdentityField) {
        this.column.userField = null;
        this.column.spaceField = null;
      }
    },
    computeThreshold() {
      if (!this.computeThreshold) {
        this.column.thresholdAggregation = null;
      } else if (!this.column.thresholdAggregation) {
        this.column.thresholdAggregation = {
          aggregation: {},
          filters: [],
        };
      }
    },
    spaceField() {
      if (this.spaceField) {
        this.column.valueAggregation = null;
        this.userField = null;
        this.computeThreshold = false;
        this.updateColumnDataType('space');
      } else if (!this.column.valueAggregation) {
        this.column.valueAggregation = {
          aggregation: {},
          filters: [],
        };
      }
    },
    userField() {
      if (this.userField) {
        this.column.valueAggregation = null;
        this.spaceField = null;
        this.computeThreshold = false;
        this.updateColumnDataType('user');
      } else if (!this.column.valueAggregation) {
        this.column.valueAggregation = {
          aggregation: {},
          filters: [],
        };
      }
    },
  },
  created() {
    this.useIdentityField = (this.column.userField && this.useUserField) || (this.column.spaceField && this.useSpaceField);
    if (!this.useIdentityField) {
      this.column.userField = null;
      this.column.spaceField = null;
    }
    this.computeThreshold = this.column.thresholdAggregation && this.column.thresholdAggregation.aggregation && this.column.thresholdAggregation.aggregation.type && true || false;
  },
  methods: {
    updateColumnDataType(field) {
      if (field === 'user' && this.column.userField) {
        this.column.spaceField = null;
        if (this.column.valueAggregation && this.column.valueAggregation.aggregation) {
          this.column.valueAggregation.aggregation.field = null;
        }
        const columnMaping = this.userFields.find(mapping => mapping && mapping.name === this.column.userField);
        this.column.dataType = columnMaping && columnMaping.type;
        this.column.sortable = false;
      } if (field === 'space' && this.column.spaceField) {
        this.column.userField = null;
        if (this.column.valueAggregation && this.column.valueAggregation.aggregation) {
          this.column.valueAggregation.aggregation.field = null;
        }
        const columnMaping = this.spaceFields.find(mapping => mapping && mapping.name === this.column.spaceField);
        this.column.dataType = columnMaping && columnMaping.type;
        this.column.sortable = false;
      } else if (this.column.valueAggregation && this.column.valueAggregation.aggregation && this.column.valueAggregation.aggregation.type) {
        this.column.sortable = this.column.valueAggregation.aggregation.type !== 'TERMS';
        this.column.dataType = null;

        if (this.column.valueAggregation.aggregation.field) {
          const columnMaping = this.fieldsMappings.find(mapping => mapping && mapping.name === this.column.valueAggregation.aggregation.field);
          this.column.dataType = columnMaping && columnMaping.type;
        } else if (this.column.valueAggregation.aggregation.type === 'COUNT') {
          this.column.dataType = 'long';
        }

        if (this.column.dataType) {
          this.column.userField = null;
          this.column.spaceField = null;
        }
      }
    },
  },
};
</script>