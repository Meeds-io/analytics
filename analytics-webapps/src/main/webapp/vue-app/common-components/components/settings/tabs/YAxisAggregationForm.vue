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
    <label
      v-show="!yAxisAggregationCount"
      class="text mb-1 my-4"
      :for="`analyticsYAxisFieldInput${uid}`">{{ yAxisAggregationCardinality || yAxisAggregationGroupBy ? $t('analytics.distinctAggregationField') : $t('analytics.numericAggregationField') }}</label>
    <div
      v-show="!yAxisAggregationCount"
      class="width-auto flex-grow-1 mt-1 mb-4">
      <analytics-field-selection
        :id="`analyticsYAxisFieldInput${uid}`"
        v-model="yAxisAggregation.field"
        :fields-mappings="fieldsMappings"
        :placeholder="yAxisAggregationCardinality || yAxisAggregationGroupBy ? $t('analytics.distinctAggregationField') : $t('analytics.numericAggregationField')"
        :numeric="!yAxisAggregationCardinality && !yAxisAggregationGroupBy"
        aggregation />
    </div>
    <template v-if="yAxisAggregationGroupBy">
      <label class="text mb-1 my-4" :for="`analyticsYAxisThresholdInput${uid}`">{{ $t('analytics.threshold') }}</label>
      <div class="width-auto flex-grow-1 mt-1 mb-4">
        <v-text-field
          :id="`analyticsYAxisThresholdInput${uid}`"
          :value="yAxisAggregation.minDocCount"
          :error="!validThreshold"
          type="number"
          min="1"
          step="1"
          outlined
          dense
          hide-details
          @input="updateMinDocCount" />
        <div
          v-if="!validThreshold"
          class="text-subtitle error--text mt-1">
          {{ $t('analytics.threshold.minValueError') }}
        </div>
      </div>
    </template>
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
    showGroupBy: {
      type: Boolean,
      default: false,
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
      const types = [
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
      if (this.showGroupBy) {
        types.push({
          text: this.$t('analytics.groupByThreshold'),
          value: 'GROUP_BY',
        });
      }
      return types;
    },
    yAxisAggregationCount() {
      return this.aggregationType === 'COUNT';
    },
    yAxisAggregationCardinality() {
      return this.aggregationType === 'CARDINALITY';
    },
    yAxisAggregationGroupBy() {
      return this.aggregationType === 'GROUP_BY';
    },
    validThreshold() {
      return !this.yAxisAggregationGroupBy
        || this.$analyticsUtils.isValidThreshold(this.yAxisAggregation && this.yAxisAggregation.minDocCount);
    },
  },
  watch: {
    yAxisAggregationCount() {
      this.yAxisAggregation.field = null;
    },
    aggregationType() {
      this.yAxisAggregation.type = this.aggregationType;
      if (this.yAxisAggregationGroupBy) {
        this.initThreshold();
      }
    },
  },
  created() {
    if (this.yAxisAggregation.type) {
      this.aggregationType = this.yAxisAggregation.type;
    } else {
      this.aggregationType = 'COUNT';
    }
    this.initThreshold();
  },
  methods: {
    // Seeds a default for a threshold the user has not typed yet. An invalid value the
    // user *did* type is never rewritten here: it stays on screen and blocks the save
    // (validThreshold above, and the drawer's disabled save CTA).
    // $set keeps the key reactive so the parent drawer sees the edits.
    initThreshold() {
      if (!this.yAxisAggregation) {
        return;
      }
      const threshold = this.yAxisAggregation.minDocCount;
      const value = this.$analyticsUtils.isValidThreshold(threshold) ? Number(threshold) : 1;
      this.$set(this.yAxisAggregation, 'minDocCount', value); // NOSONAR mutating the shared settings object passed by the parent is this form's established pattern (see yAxisAggregation.field above)
    },
    updateMinDocCount(value) {
      // Keep what the user typed, an emptied field included: coercing it here would
      // display one threshold and save another.
      const threshold = value === '' || value === null ? null : Number(value);
      this.$set(this.yAxisAggregation, 'minDocCount', threshold); // NOSONAR same established pattern as above
    },
  },
};
</script>
