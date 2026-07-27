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
    <v-switch
      v-model="multipleCharts"
      :label="$t('analytics.multipleCharts')"
      class="width-auto flex-grow-1 mt-1 mb-4"
      required />
    <div
      v-if="multipleCharts"
      class="width-auto flex-grow-1 mt-1 mb-4">
      <analytics-field-selection
        v-model="settings.multipleChartsField"
        :fields-mappings="fieldsMappings"
        :label="$t('analytics.fieldComparator')"
        aggregation />
    </div>
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
    multipleCharts: false,
  }),
  computed: {
    yAxisAggregation() {
      return this.settings && this.settings.yAxisAggregation;
    },
  },
  watch: {
    settings() {
      this.multipleCharts = this.settings && this.settings.multipleChartsField;
    },
    multipleCharts() {
      if (!this.multipleCharts) {
        this.settings.multipleChartsField = null;
      }
    },
  },
  mounted() {
    this.multipleCharts = this.settings && this.settings.multipleChartsField;
  },
};
</script>