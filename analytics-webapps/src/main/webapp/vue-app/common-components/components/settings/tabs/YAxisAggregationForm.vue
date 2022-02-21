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
  <v-form
    ref="form"
    class="aggregationForm"
    @submit="
      $event.preventDefault();
      $event.stopPropagation();
    ">
    <v-card-text>
      <v-layout wrap>
        <v-flex class="my-auto px-2" xs6>
          <v-radio-group v-model="aggregationType">
            <v-radio
              v-for="aggregationType in aggregationTypes"
              :key="aggregationType.value"
              :label="aggregationType.text"
              :value="aggregationType.value" />
          </v-radio-group>
        </v-flex>
        <v-flex
          v-show="!yAxisAggregationCount"
          class="my-auto px-2"
          xs6>
          <analytics-field-selection
            v-model="yAxisAggregation.field"
            :fields-mappings="fieldsMappings"
            :placeholder="yAxisAggregationCardinality ? $t('analytics.distinctAggregationField') : $t('analytics.numericAggregationField')"
            :numeric="!yAxisAggregationCardinality"
            aggregation />
        </v-flex>
      </v-layout>
    </v-card-text>
  </v-form>
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