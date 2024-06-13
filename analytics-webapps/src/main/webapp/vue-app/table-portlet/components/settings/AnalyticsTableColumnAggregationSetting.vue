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
  <div>
    <h4>{{ $t('analytics.computingRule') }}</h4>
    <v-layout no-wrap>
      <v-flex class="my-auto px-2">
        <v-radio-group v-model="aggregationType">
          <v-radio
            v-for="aggregationType in aggregationTypes"
            :key="aggregationType.value"
            :label="aggregationType.text"
            :value="aggregationType.value" />
        </v-radio-group>
      </v-flex>
      <v-flex
        v-show="!isAggregationCount"
        class="my-auto px-2">
        <analytics-field-selection
          v-model="columnAggregation.aggregation.field"
          :fields-mappings="fieldsMappings"
          :placeholder="numericAggregationField ? $t('analytics.numericAggregationField') : $t('analytics.distinctAggregationField')"
          :numeric="numericAggregationField"
          attach
          aggregation
          @change="$emit('change')" />
        <v-flex v-if="isTermsAggregation" class="px-2 mt-2 d-flex flex-row">
          <v-switch
            v-model="limitResults"
            :label="$t('analytics.limitResults')"
            class="my-auto text-no-wrap" />
          <input
            v-if="limitResults"
            v-model="columnAggregation.aggregation.limit"
            type="number"
            class="ignore-vuetify-classes inputSmall mt-1 ml-3">
          <select
            v-model="columnAggregation.aggregation.sortDirection"
            class="ignore-vuetify-classes ml-2 mt-1 width-auto">
            <option value="desc">{{ $t('analytics.descending') }}</option>
            <option value="asc">{{ $t('analytics.ascending') }}</option>
          </select>
        </v-flex>
      </v-flex>
    </v-layout>
    <v-switch
      v-model="columnAggregation.periodIndependent"
      :label="$t('analytics.periodIndependent')"
      class="my-2 text-no-wrap" />
    <analytics-search-filter-form
      :fields-mappings="fieldsMappings"
      :filters="columnAggregation.filters"
      attach />
  </div>
</template>
<script>
export default {
  props: {
    columnAggregation: {
      type: Object,
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
    aggregationType: 'MAX',
    limitResults: false,
    initialized: false,
  }),
  computed: {
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
    isAggregationCount() {
      return this.aggregationType === 'COUNT';
    },
    isTermsAggregation() {
      return this.aggregationType === 'TERMS';
    },
    numericAggregationField() {
      return this.aggregationType !== 'CARDINALITY' && this.aggregationType !== 'TERMS';
    },
  },
  watch: {
    limitResults() {
      if (!this.initialized) {
        return;
      }
      if (this.limitResults) {
        this.columnAggregation.aggregation.limit = 1;
      } else {
        this.columnAggregation.aggregation.limit = 0;
      }
    },
    isAggregationCount() {
      if (!this.initialized) {
        return;
      }
      if (this.columnAggregation.aggregation) {
        this.columnAggregation.aggregation.field = null;
      }
    },
    aggregationType() {
      if (!this.initialized) {
        return;
      }
      if (this.columnAggregation.aggregation) {
        this.columnAggregation.aggregation.type = this.aggregationType;
      } else {
        this.columnAggregation.aggregation = {
          type: this.aggregationType,
        };
      }
      this.$emit('change');
    },
  },
  created() {
    if (this.columnAggregation && this.columnAggregation.aggregation && this.columnAggregation.aggregation.type) {
      this.aggregationType = this.columnAggregation.aggregation.type;
    } else {
      this.aggregationType = 'COUNT';
    }
    this.limitResults = this.columnAggregation.aggregation && this.columnAggregation.aggregation.limit || 0;
    this.$nextTick().then(() => this.initialized = true);
  },
  methods: {
    selectedValueComparator(item1, item2){
      const item1Value = (item1 && item1.field) || item1;
      const item2Value = (item2 && item2.field) || item2;
      return item1Value === item2Value;
    },
  },
};
</script>