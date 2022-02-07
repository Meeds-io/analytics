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
        <template>
          <v-flex
            class="my-auto px-2"
            xs4>
            <v-radio-group v-model="dateInterval">
              <v-radio
                v-for="dateField in dateFields"
                :key="dateField.value"
                :label="dateField.text"
                :value="dateField.value" />
              <v-radio :label="$t('analytics.perCustomField')" value="custom" />
            </v-radio-group>
          </v-flex>
          <v-flex
            v-if="!dateAggregationType"
            class="my-auto px-2"
            xs8>
            <analytics-x-axis-aggregation-field
              v-for="(aggregation, index) in xAxisAggregations"
              :key="index"
              :aggregation="aggregation"
              :fields-mappings="fieldsMappings"
              :display-add="(index +1) === xAxisAggregations.length"
              :display-delete="xAxisAggregations.length > 1"
              @delete="deleteAggregation(index)"
              @add="addAggregation" />
          </v-flex>
        </template>
      </v-layout>
    </v-card-text>
  </v-form>
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
    dateAggregationType: true,
    dateInterval: 'custom',
    dateAggregation: {
      type: 'DATE',
      field: 'timestamp',
      sortDirection: 'asc',
      interval: 'day',
    },
    fieldAggregation: {
      type: 'TERMS',
      field: 'module',
      sortDirection: 'desc',
      limit: 0,
    },
  }),
  computed: {
    dateFields() {
      return [
        {
          text: this.$t('analytics.perDay'),
          value: 'day',
        },
        {
          text: this.$t('analytics.perWeek'),
          value: 'week',
        },
        {
          text: this.$t('analytics.perMonth'),
          value: 'month',
        },
        {
          text: this.$t('analytics.perQuarter'),
          value: 'quarter',
        },
        {
          text: this.$t('analytics.perYear'),
          value: 'year',
        },
      ];
    } ,
    xAxisAggregations() {
      return this.settings && this.settings.xAxisAggregations;
    },
    xAxisAggregation() {
      return this.hasAggregations && this.settings.xAxisAggregations[0] || null;
    },
    hasAggregations() {
      return this.xAxisAggregations && this.xAxisAggregations.length;
    }
  },
  watch: {
    dateInterval(newVal, oldVal) {
      if (oldVal !== newVal) {
        this.dateAggregationType = this.dateInterval !== 'custom';
        this.changeAggregationType();
      }
    }
  },
  mounted() {
    if (!this.xAxisAggregations || !this.xAxisAggregations.length) {
      this.dateInterval = 'day';
    } else {
      this.dateAggregationType = this.xAxisAggregations && this.xAxisAggregations.length === 1 && this.xAxisAggregations[0].type === 'DATE';
      if (this.dateAggregationType) {
        this.dateInterval = this.xAxisAggregations[0].interval;
      }
    }
  },
  methods: {
    changeAggregationType() {
      if (this.dateAggregationType) {
        const dateAggregation = Object.assign({}, this.dateAggregation);
        dateAggregation.interval = this.dateInterval;
        this.settings.xAxisAggregations.splice(0, this.settings.xAxisAggregations.length, dateAggregation);
      } else {
        this.settings.xAxisAggregations.splice(0, this.settings.xAxisAggregations.length, Object.assign({}, this.fieldAggregation));
      }
      this.$forceUpdate();
    },
    deleteAggregation(aggregationIndex) {
      this.xAxisAggregations.splice(aggregationIndex, 1);
    },
    addAggregation() {
      this.xAxisAggregations.push({
        type: 'TERMS',
        sortDirection: 'desc',
        limit: 0,
      });
      this.$forceUpdate();
    },
  },
};
</script>