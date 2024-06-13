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
  <v-layout>
    <v-flex xs10>
      <analytics-field-selection
        v-model="aggregation.field"
        :fields-mappings="fieldsMappings"
        :label="$t('analytics.fieldName')"
        aggregation />
      <v-flex class="px-2 mt-2 d-flex flex-row">
        <v-switch
          v-model="limitResults"
          :label="$t('analytics.limitResults')"
          class="my-auto text-no-wrap" />
        <input
          v-if="limitResults"
          v-model="aggregation.limit"
          type="number"
          class="ignore-vuetify-classes inputSmall mt-1 ml-3">
        <select
          v-model="aggregation.sortDirection"
          class="ignore-vuetify-classes ml-2 mt-1 width-auto">
          <option value="desc">{{ $t('analytics.descending') }}</option>
          <option value="asc">{{ $t('analytics.ascending') }}</option>
        </select>
      </v-flex>
    </v-flex>
    <v-flex
      v-if="displayDelete"
      class="my-auto"
      xs1>
      <v-btn icon @click="$emit('delete')">
        <v-icon>fa-minus-circle</v-icon>
      </v-btn>
    </v-flex>
    <v-flex
      v-if="displayAdd"
      class="my-auto"
      xs1>
      <v-btn icon @click="$emit('add')">
        <v-icon>fa-plus-circle</v-icon>
      </v-btn>
    </v-flex>
  </v-layout>
</template>

<script>
export default {
  props: {
    aggregation: {
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
    displayAdd: {
      type: Boolean,
      default: false,
    },
    displayDelete: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    limitResults: false,
  }),
  watch: {
    limitResults() {
      if (!this.limitResults && this.aggregation) {
        this.aggregation.limit = 0;
      }
    },
  },
  mounted() {
    this.limitResults = this.aggregation && this.aggregation.limit > 0 || false;
  },
};
</script>