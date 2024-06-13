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
    <v-flex
      wrap
      xs12
      d-flex
      flex-column>
      <v-text-field
        v-model="settings.title"
        :label="$t('analytics.tableTitle')"
        outlined
        required />
      <h3>{{ $t('analytics.mainColumn') }}</h3>
      <v-text-field
        v-model="settings.mainColumn.title"
        :label="$t('analytics.tableMainColumnTitle')"
        required />
      <analytics-field-selection
        v-model="settings.mainColumn.valueAggregation.aggregation.field"
        :fields-mappings="fieldsMappings"
        :label="$t('analytics.fieldName')"
        aggregation />
    </v-flex>
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
      type: Object,
      default: function() {
        return null;
      },
    },
  },
  computed: {
    columns() {
      return this.settings && this.settings.columns || [];
    },
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