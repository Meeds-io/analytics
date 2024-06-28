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
  <v-flex class="d-flex flex-column">
    <analytics-text-value-suggester
      ref="valueSuggester"
      v-model="selectedValue"
      :filter="filter"
      :labels="suggesterLabels"
      class="analytics-suggester"
      sugester-class="my-0"
      @input="selectValue" />
    <div v-if="values && multipleOperator" class="d-flex flex-column">
      <div
        v-for="value in values"
        :key="value">
        <v-chip
          :title="value"
          color="primary"
          close
          class="identitySuggesterItem mt-2"
          @click:close="remove(value)">
          <span class="text-truncate">
            {{ value.label }}
          </span>
        </v-chip>
      </div>
    </div>
  </v-flex>
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
    suggesterLabels: {
      type: Array,
      default: function() {
        return [];
      },
    },
  },
  data: () => ({
    selectedValue: null,
    values: [],
  }),
  computed: {
    operatorType() {
      return this.filter && this.filter.type;
    },
    multipleOperator() {
      return this.operatorType === 'IN_SET' || this.operatorType === 'NOT_IN_SET';
    },
    valueIds() {
      return this.values.map(value => value.id);
    },
  },
  created() {
    if (this.filter.valueString) {
      const values = this.filter.valueString.split(',');
      const selectedValues = [];
      values.forEach(value => {
        if (value) {
          selectedValues.push({
            value: value,
            label: this.computeI18NLabel(value),
          });
        }
      });
      if (this.multipleOperator) {
        this.values = selectedValues;
      } else {
        this.selectedValue = selectedValues && selectedValues[0] || null;
      }
    }
  },
  methods: {
    computeI18NLabel(value) {
      const key = `analytics.${value}`;
      const i18NValue = this.$t(key);
      return i18NValue === key ? value : i18NValue;
    },
    selectValue(value) {
      if (this.multipleOperator) {
        const selectedValue = value && (value.length && value[0].value || value.value);
        if (!selectedValue) {
          return;
        } else if (this.valueIds.includes(selectedValue)) {
          this.selectedValue = null;
          this.$refs.valueSuggester.clear();
          return;
        }
      } else {
        this.filter.valueString = '';
      }
      if (value) {
        const values = this.multipleOperator && this.filter.valueString && this.filter.valueString.split(',') || [];
        const selectedValue = value && value.value || value;
        const selectedValues = Array.isArray(selectedValue) && selectedValue || [selectedValue];
        values.push(...selectedValues);
        this.filter.valueString = values.join(',');
        if (this.multipleOperator) {
          values.forEach(value => {
            if (value && !this.values.find(item => item.value === value)) {
              this.values.push({
                value: value,
                label: this.computeI18NLabel(value),
              });
            }
          });
        }
      }
      if (this.multipleOperator) {
        this.selectedValue = null;
        this.$refs.valueSuggester.clear();
      }
    },
    remove(value) {
      if (value && this.values.indexOf(value) >= 0) {
        this.values.splice(this.values.indexOf(value), 1);
        this.filter.valueString = this.values.map(value => value.value).join(',');
      }
    },
  },
};
</script>