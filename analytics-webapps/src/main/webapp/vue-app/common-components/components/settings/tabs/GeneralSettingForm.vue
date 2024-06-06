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
  <v-form
    ref="form"
    class="generalSettingForm"
    @submit="
      $event.preventDefault();
      $event.stopPropagation();
    ">
    <v-layout wrap xs12>
      <v-flex
        class="my-auto px-2"
        md6
        xs12
        pt-1>
        <v-text-field
          v-model="settings.title"
          :label="$t('analytics.chartTitle')"
          required />
      </v-flex>
      <v-flex
        class="my-auto px-2"
        md6
        xs12
        pb-4>
        <v-select
          v-model="settings.chartType"
          :items="chartTypes"
          :value-comparator="selectedValueComparator"
          item-text="text"
          item-value="value"
          :label="$t('analytics.chartType')"
          class="operatorInput"
          persistent-hint
          chips
          @change="$emit('type-changed')" />
      </v-flex>
    </v-layout>
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
  },
  computed: {
    isPercentageBar() {
      return this.settings.chartType === 'percentageBar' || this.settings.chartType === 'percentage';
    },
    chartTypes(){
      if (this.isPercentageBar){
        return [
          {
            text: 'Percentage Bar',
            value: 'percentageBar',
          },
          {
            text: 'Percentage',
            value: 'percentage',
          }
        ];
      } else {
        return [
          {
            text: this.$t('analytics.bar'),
            value: 'bar',
          },
          {
            text: this.$t('analytics.line'),
            value: 'line',
          },
          {
            text: this.$t('analytics.pie'),
            value: 'pie',
          },
        ];
      }

    }
  },
  methods: {
    selectedValueComparator(item1, item2){
      const item1Value = (item1 && item1.value) || item1;
      const item2Value = (item2 && item2.value) || item2;
      return item1Value === item2Value;
    },
  },
};
</script>