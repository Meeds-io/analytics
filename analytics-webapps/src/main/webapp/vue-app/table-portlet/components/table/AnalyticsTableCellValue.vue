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
  <span v-if="!value" :class="signClass">
    -
  </span>
  <extension-registry-component
    v-else-if="cellValueExtension"
    :component="extendedCellValueComponent"
    :params="extendedCellValueComponentParams" />
  <date-format
    v-else-if="dataType === 'date'"
    :value="dateValue"
    :format="fullDateFormat"
    class="text-no-wrap" />
  <span
    v-else-if="percentage"
    :class="signClass"
    class="text-no-wrap">
    <v-tooltip v-if="previousValue" bottom>
      <template #activator="{ on, attrs }">
        <span
          v-bind="attrs"
          v-on="on">
          {{ sign || '' }}{{ valueNumberAbs || '' }}%
        </span>
      </template>
      <span>{{ $t('analytics.previousPeriodValue', {0: previousValue}) }}</span>
    </v-tooltip>
    <v-tooltip v-else-if="threshold" bottom>
      <template #activator="{ on, attrs }">
        <span
          :class="signClass"
          v-bind="attrs"
          v-on="on">
          {{ sign || '' }}{{ valueNumberAbs || '' }}%
        </span>
      </template>
      <span>{{ $t('analytics.percentOfValue', {0: originalValue, 1: threshold}) }}</span>
    </v-tooltip>
    <span v-else :class="signClass">{{ sign || '' }}{{ valueNumberAbs || '' }}%</span>
  </span>
  <span v-else-if="dataType === 'long' || dataType === 'double'" class="text-no-wrap">
    {{ valueNumber }}
  </span>
  <span v-else class="text-no-wrap">
    {{ i18NValue }}
  </span>
</template>

<script>
export default {
  props: {
    value: {
      type: Object,
      default: function() {
        return null;
      },
    },
    previousValue: {
      type: Object,
      default: function() {
        return null;
      },
    },
    originalValue: {
      type: Object,
      default: function() {
        return null;
      },
    },
    threshold: {
      type: Object,
      default: function() {
        return null;
      },
    },
    field: {
      type: Object,
      default: function() {
        return null;
      },
    },
    aggregationType: {
      type: Object,
      default: function() {
        return null;
      },
    },
    dataType: {
      type: Object,
      default: function() {
        return null;
      },
    },
    percentage: {
      type: Boolean,
      default: false,
    },
    compare: {
      type: Boolean,
      default: false,
    },
    item: {
      type: Object,
      default: function() {
        return null;
      },
    },
    column: {
      type: Object,
      default: () => null
    },
    labels: {
      type: Object,
      default: function() {
        return null;
      },
    },
    cellValueExtension: {
      type: Object,
      default: function() {
        return null;
      },
    },
  },
  data: () => ({
    fullDateFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    },
  }),
  computed: {
    i18NValue() {
      const key = `analytics.${this.value}`;
      const i18NValue = this.$t(key);
      return i18NValue === key ? this.value : i18NValue;
    },
    dateValue() {
      if (this.value && !Number.isNaN(Number(this.value))) {
        return new Date(Number(this.value));
      }
      return null;
    },
    valueNumber() {
      const cellItemValueNumber = this.value && Number(this.value) || 0;
      return cellItemValueNumber && this.$analyticsUtils.toFixed(cellItemValueNumber) || 0;
    },
    valueNumberAbs() {
      return Math.abs(this.valueNumber);
    },
    extendedCellValueComponent() {
      return this.cellValueExtension && {
        componentName: 'cell-value',
        componentOptions: {
          vueComponent: this.cellValueExtension.vueComponent,
        },
      } || null;
    },
    extendedCellValueComponentParams() {
      return this.cellValueExtension && {
        column: this.column,
        item: this.item,
        value: this.value,
        labels: this.labels,
      } || null;
    },
    sign() {
      if (!this.compare) {
        return '';
      } else if (this.valueNumber > 0) {
        return '+';
      } else {
        return '-';
      }
    },
    signClass() {
      if (!this.compare) {
        return '';
      } else if (this.valueNumber >= 0) {
        return 'success--text';
      } else {
        return 'error--text';
      }
    },
  },
};
</script>