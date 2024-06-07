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
  <v-expansion-panel class="border-box-sizing">
    <v-expansion-panel-header>
      <v-row no-gutters class="sampleItemHeader">
        <v-col cols="4" class="text-truncate">
          <analytics-profile-chip v-if="userId" :profile-id="userId" />
        </v-col>
        <v-col
          cols="4"
          :title="operationLabel"
          class="text-truncate">
          <strong v-if="chartData.operation">{{ operationLabel }}</strong>
        </v-col>
        <v-col cols="4" class="text-right">
          <date-format :value="chartDataTime" :format="dateFormat" />
        </v-col>
      </v-row>
    </v-expansion-panel-header>
    <v-expansion-panel-content>
      <template v-if="chartDataProps">
        <analytics-sample-item-attribute
          v-for="attrKey in chartDataProps"
          :key="attrKey"
          :attr-key="attrKey"
          :attr-value="chartData[attrKey]"
          :sample-item-extensions="sampleItemExtensions"
          class="ma-2 no-gutters" />
      </template>
      <template v-if="chartDataParameters">
        <analytics-sample-item-attribute
          v-for="attrKey in chartDataParameters"
          :key="attrKey"
          :attr-key="attrKey"
          :attr-value="chartData.parameters[attrKey]"
          :sample-item-extensions="sampleItemExtensions"
          class="ma-2 no-gutters" />
      </template>
    </v-expansion-panel-content>
  </v-expansion-panel>
</template>
<script>
export default {
  props: {
    chartData: {
      type: Object,
      default: function() {
        return null;
      },
    },
    sampleItemExtensions: {
      type: Object,
      default: function() {
        return {};
      },
    },
  },
  data: () => ({
    dateFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: 'numeric',
      second: 'numeric',
    },
  }),
  computed: {
    chartDataTime() {
      return this.chartData && this.chartData.timestamp && new Date(this.chartData.timestamp);
    },
    chartDataProps() {
      const chartDataProps = this.chartData && Object.keys(this.chartData).sort();
      return chartDataProps.filter(item => item !== 'parameters' && this.chartData[item]);
    },
    chartDataParameters() {
      return this.chartData && this.chartData.parameters && Object.keys(this.chartData.parameters).sort();
    },
    userId() {
      return this.chartData.userId || this.chartData.modifierSocialId;
    },
    operationLabel() {
      const operationLabelI18NValue = `analytics.${this.chartData.operation}`;
      return this.$te(operationLabelI18NValue) ? this.$t(operationLabelI18NValue) : this.chartData.operation;
    },
  },
};
</script>
