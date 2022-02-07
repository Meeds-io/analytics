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
  <v-layout class="analytics-field-filter">
    <v-flex xs3 class="mb-auto">
      <analytics-field-selection
        v-model="filter.field"
        :fields-mappings="fieldsMappings"
        :placeholder="$t('analytics.fieldNamePlaceholder')"
        :attach="attach" />
    </v-flex>
    <v-flex xs4 class="d-flex mb-auto content-box-sizing">
      <select
        v-model="filter.type"
        :placeholder="$t('analytics.operatorPlaceholder')"
        :title="searchFilterTypeLabel"
        class="operatorInput border-color my-auto mx-1 text-truncate ignore-vuetify-classes"
        @change="filterTypeChanged">
        <option
          v-for="searchFilterType in searchFilterTypes"
          :key="searchFilterType.value"
          :value="searchFilterType.value"
          :title="searchFilterType.text"
          class="text-truncate">
          {{ searchFilterType.text }}
        </option>
      </select>
    </v-flex>
    <v-flex xs4 class="mb-auto">
      <template v-if="isMultipleValuesSelection">
        <analytics-multiple-values-selection
          :filter="filter"
          :suggester-labels="suggesterLabels"
          :field-label="selectedFieldLabel"
          :aggregation="isSelectedFieldAggregation"
          :placeholder="valuesComparatorPlaceholder" />
      </template>
      <template v-else-if="isSingleValueSelection">
        <analytics-space-field-filter
          v-if="filter.field === 'spaceId'"
          :filter="filter"
          :suggester-labels="suggesterLabels" />
        <analytics-user-field-filter
          v-else-if="filter.field === 'userId'"
          :filter="filter"
          :suggester-labels="suggesterLabels" />
        <analytics-text-value-filter
          v-else-if="isSelectedFieldAggregation"
          :filter="filter"
          :suggester-labels="suggesterLabels" />
        <input
          v-else
          v-model="filter.valueString"
          type="text"
          :placeholder="valuesComparatorPlaceholder"
          class="ignore-vuetify-classes width-auto pa-0 my-auto"
          required>
      </template>
      <input
        v-else-if="filter.type === 'LESS' || filter.type === 'GREATER'"
        v-model.number="filter.valueString"
        type="number"
        :placeholder="$t('analytics.numberValuePlaceholder')"
        class="ignore-vuetify-classes width-auto pa-0 my-auto"
        required>
      <template v-else-if="filter.type === 'RANGE' && filter.range">
        <input
          v-model.number="filter.range.min"
          type="number"
          :placeholder="$t('analytics.minPlaceholder')"
          class="ignore-vuetify-classes width-auto pa-0 my-auto"
          required>
        <input
          v-model.number="filter.range.max"
          type="number"
          :placeholder="$t('analytics.maxPlaceholder')"
          class="ignore-vuetify-classes pa-0 width-auto my-auto"
          required>
      </template>
    </v-flex>
    <div xs1 class="mx-auto">
      <v-btn icon @click="$emit('delete')">
        <v-icon>fa-minus-circle</v-icon>
      </v-btn>
    </div>
  </v-layout>
</template>

<script>
export default {
  props: {
    filter: {
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
    attach: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    users: [],
    spaces: [],
    searchOptions: {
      currentUser: '',
    },
  }),
  computed: {
    suggesterLabels() {
      return {
        searchPlaceholder: this.$t('analytics.searchPlaceholder'),
        placeholder: this.$t('analytics.searchPlaceholder'),
        noDataLabel: this.$t('analytics.noDataLabel'),
      };
    },
    selectedFieldMapping() {
      if (this.filter && this.filter.field) {
        return this.fieldsMappings.find(mapping => mapping.searchFieldName === this.filter.field);
      }
      return null;
    },
    isSelectedFieldAggregation() {
      return this.selectedFieldMapping && this.selectedFieldMapping.aggregation;
    },
    selectedFieldLabel() {
      return this.selectedFieldMapping && this.selectedFieldMapping.label || '';
    },
    searchFilterTypes() {
      return [
        {
          text: this.$t('analytics.equals'),
          value: 'EQUAL',
          placeholder: this.$t('analytics.fieldValuePlaceholder'),
        },
        {
          text: this.$t('analytics.notEquals'),
          value: 'NOT_EQUAL',
          placeholder: this.$t('analytics.fieldValuePlaceholder'),
        },
        {
          text: this.$t('analytics.inValues'),
          value: 'IN_SET',
          placeholder: this.$t('analytics.setPlaceholder'),
        },
        {
          text: this.$t('analytics.notInValues'),
          value: 'NOT_IN_SET',
          placeholder: this.$t('analytics.setPlaceholder'),
        },
        {
          text: this.$t('analytics.inRange'),
          value: 'RANGE',
          placeholder: this.$t('analytics.rangePlaceholder'),
        },
        {
          text: this.$t('analytics.less'),
          value: 'LESS',
          placeholder: this.$t('analytics.lessPlaceholder'),
        },
        {
          text: this.$t('analytics.greater'),
          value: 'GREATER',
          placeholder: this.$t('analytics.greaterPlaceholder'),
        },
        {
          text: this.$t('analytics.isNull'),
          value: 'IS_NULL',
          placeholder: this.$t('analytics.isEmptyPlaceholder'),
        },
        {
          text: this.$t('analytics.isNotNull'),
          value: 'NOT_NULL',
          placeholder: this.$t('analytics.isNotNullPlaceholder'),
        },
      ];
    },
    filterField() {
      return this.filter && this.filter.field;
    },
    isIdentitySuggester() {
      return this.filterField === 'userId' || this.filterField === 'spaceId';
    },
    searchFilterTypesObject() {
      const searchFilterTypesObject = {};
      this.searchFilterTypes.forEach(filterType => searchFilterTypesObject[filterType.value] = filterType.text);
      return searchFilterTypesObject;
    },
    operatorType() {
      return this.filter && this.filter.type;
    },
    isMultipleValuesSelection() {
      return this.operatorType === 'IN_SET'
        || this.operatorType === 'NOT_IN_SET';
    },
    isSingleValueSelection() {
      return this.operatorType === 'EQUAL'
        || this.operatorType === 'NOT_EQUAL';
    },
    valuesComparatorPlaceholder() {
      return ((this.operatorType === 'EQUAL' || this.operatorType === 'NOT_EQUAL') && this.$t('analytics.valuePlaceholder'))
        || ((this.operatorType === 'IN_SET' || this.operatorType === 'NOT_IN_SET') && this.$t('analytics.setPlaceholder'));
    },
    multipleOperator() {
      return this.operatorType === 'IN_SET' || this.operatorType === 'NOT_IN_SET';
    },
    searchFilterTypeLabel() {
      return this.operatorType && this.searchFilterTypesObject[this.operatorType] || '';
    },
  },
  watch: {
    filterField() {
      this.filterTypeChanged();
    },
  },
  methods: {
    filterTypeChanged() {
      this.filter.valueString = '';
      this.filter.users = null;
      this.filter.spaces = null;
      this.users = [];
      this.spaces = [];
      if (this.operatorType === 'RANGE') {
        this.filter.range = {};
      } else {
        this.filter.range = null;
      }
    },
  },
};
</script>