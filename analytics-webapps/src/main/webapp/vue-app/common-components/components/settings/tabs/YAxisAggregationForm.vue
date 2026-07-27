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
    <label class="text-header me-1 my-4" :for="`analyticsYAxisDataInput${uid}`">{{ $t('analytics.data') }}</label>
    <div class="width-auto flex-grow-1 mt-1 mb-4">
      <v-select
        :id="`analyticsYAxisDataInput${uid}`"
        v-model="aggregationType"
        :items="aggregationTypes"
        item-text="text"
        item-value="value"
        outlined
        dense
        hide-details
        chips />
    </div>
    <div
      v-show="!yAxisAggregationCount"
      class="width-auto flex-grow-1 mt-1 mb-4">
      <analytics-field-selection
        v-model="yAxisAggregation.field"
        :fields-mappings="fieldsMappings"
        :placeholder="yAxisAggregationCardinality ? $t('analytics.distinctAggregationField') : $t('analytics.numericAggregationField')"
        :numeric="!yAxisAggregationCardinality"
        aggregation />
    </div>
  </div>
</template>

<script>
export default {
  props: {
    yAxisAggregation: {
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
    type: {
      type: String,
      default: function (){
        return null;
      }
    },
  },
  data: () => ({
    aggregationType: 'MAX',
  }),
  computed: {
    uid() {
      return this._uid;
    },
    aggregationTypes() {
      return [
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
    yAxisAggregationCount() {
      return this.aggregationType === 'COUNT';
    },
    yAxisAggregationCardinality() {
      return this.aggregationType === 'CARDINALITY';
    },
  },
  watch: {
    yAxisAggregationCount() {
      this.yAxisAggregation.field = null;
    },
    aggregationType() {
      this.yAxisAggregation.type = this.aggregationType;
    },
  },
  created() {
    if (this.yAxisAggregation.type) {
      this.aggregationType = this.yAxisAggregation.type;
    } else {
      this.aggregationType = 'COUNT';
    }
  },
};
</script>