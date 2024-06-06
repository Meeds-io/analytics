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
    <v-divider class="my-4" />
    <h3>{{ $t('analytics.limitFilters') }}</h3>
    <v-switch
      v-model="limitResults"
      :label="$t('analytics.limitResults')"
      class="my-auto text-no-wrap" />
    <div class="d-flex flex-row">
      <template v-if="limitResults && percentageLimit">
        <input
          v-model="percentage"
          type="number"
          min="0"
          max="100"
          step="0.1"
          class="ignore-vuetify-classes inputSmall my-auto">
        <span class="my-auto ml-1">%</span>
        <span class="my-auto ml-1">{{ $t('analytics.ofField') }}</span>
        <analytics-field-selection
          v-model="field"
          :fields-mappings="fieldsMappings"
          aggregation
          class="ml-1" />
      </template>
    </div>
    <v-divider class="my-4" />
    <template v-if="limitResults && percentageLimit">
      <h3>{{ $t('analytics.limitComputingRule') }}</h3>
      <analytics-y-axis-form
        :fields-mappings="fieldsMappings"
        :y-axis-aggregation="yAxisAggregation" />
      <v-divider class="my-4" />
      <analytics-search-filter-form
        :fields-mappings="fieldsMappings"
        :filters="filters">
        <h3>{{ $t('analytics.limitComputingFilters') }}</h3>
      </analytics-search-filter-form>
    </template>
  </div>
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
    fieldsMappings: {
      type: Array,
      default: function() {
        return [];
      },
    },
  },
  data: () => ({
    initialized: false,
    limitResults: false,
    percentage: 0,
    field: null,
    yAxisAggregation: null,
    filters: null,
  }),
  computed: {
    percentageLimit() {
      return this.settings && this.settings.percentageLimit;
    },
  },
  watch: {
    field() {
      if (!this.initialized) {
        return;
      }
      if (this.percentageLimit) {
        this.percentageLimit.field = this.field;
      }
    },
    percentage() {
      if (!this.initialized) {
        return;
      }
      if (this.percentageLimit) {
        this.percentageLimit.percentage = this.percentage;
      }
    },
    limitResults() {
      if (!this.initialized) {
        return;
      }
      if (this.limitResults) {
        if (this.percentageLimit) {
          return;
        }
        this.filters = [];
        this.yAxisAggregation = {
          type: 'COUNT',
        };
        this.field = null;
        this.percentage = 0;
        this.settings.percentageLimit = {
          aggregation: {
            yAxisAggregation: this.yAxisAggregation,
            filters: this.filters,
          },
          percentage: this.percentage,
          field: this.field,
        };
      } else {
        this.settings.percentageLimit = null;
      }
    },
  },
  created() {
    if (this.percentageLimit && this.percentageLimit.aggregation) {
      this.percentage = this.percentageLimit.percentage || 0;
      this.field = this.percentageLimit.field || null;
      this.filters = this.percentageLimit.aggregation.filters || [];
      this.yAxisAggregation = this.percentageLimit.aggregation.yAxisAggregation || {
        type: 'COUNT',
      };
      this.limitResults = true;
    }
    this.$nextTick().then(() => this.initialized = true);
  },
};
</script>