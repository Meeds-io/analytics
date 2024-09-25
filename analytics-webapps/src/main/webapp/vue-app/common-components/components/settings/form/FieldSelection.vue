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
  <div :id="id">
    <div>{{ label }}</div>
    <v-autocomplete
      ref="fieldSelectAutoComplete"
      v-model="value"
      :items="fields"
      :placeholder="placeholder"
      :value-comparator="selectedValueComparator"
      :return-object="false"
      :item-value="aggregation ? 'aggregationFieldName' : 'searchFieldName'"
      :menu-props="menuProps"
      item-text="label"
      class="operatorInput pa-0"
      outlined
      persistent-hint
      chips
      dense
      flat
      @change="updateData">
      <template #selection="data">
        <v-chip
          v-bind="data.attrs"
          :input-value="data.selected"
          :title="data.item && data.item.label || data.item"
          @click="data.select">
          {{ data.item && data.item.label || data.item }}
        </v-chip>
      </template>
      <template #item="data">
        <v-list-item-content v-text="data.item.label" />
      </template>
    </v-autocomplete>
  </div>
</template>

<script>
export default {
  props: {
    fieldsMappings: {
      type: Array,
      default: function() {
        return [];
      },
    },
    aggregation: {
      type: Boolean,
      default: function() {
        return false;
      },
    },
    numeric: {
      type: Boolean,
      default: function() {
        return false;
      },
    },
    value: {
      type: String,
      default: function() {
        return null;
      },
    },
    label: {
      type: String,
      default: function() {
        return null;
      },
    },
    placeholder: {
      type: String,
      default: function() {
        return null;
      },
    },
    attach: {
      type: Boolean,
      default: false,
    },
  },
  data () {
    return {
      id: `FieldSelection${parseInt(Math.random() * 10000)
        .toString()
        .toString()}`,
    };
  },
  computed: {
    menuProps() {
      return this.attach && 'maxHeight = 100' || '';
    },
    fieldNames() {
      this.fieldsMappings.forEach(fieldMapping => {
        if (!fieldMapping.label) {
          const label = fieldMapping.name;
          const labelPart = label.indexOf('.') >= 0 ? label.substring(label.lastIndexOf('.') + 1) : label;
          const fieldLabelI18NKey = `analytics.field.label.${labelPart}`;
          const fieldLabelI18NValue = this.$t(fieldLabelI18NKey);
          fieldMapping.label = fieldLabelI18NValue === fieldLabelI18NKey ? `${this.$t('analytics.field.label.default')} ${label}` : fieldLabelI18NValue;
        }
      });
      return this.fieldsMappings;
    },
    fields() {
      return this.fieldNames.filter(field => (!this.aggregation || field.aggregation) && (!this.numeric || field.numeric || field.date))
        .sort((a, b) => a?.label?.toLowerCase?.()?.localeCompare?.(b?.label?.toLowerCase?.()));
    },
  },
  mounted() {
    $(`#${this.id} input[type="text"]`).on('blur', () => {
      // A hack to close on select
      // See https://www.reddit.com/r/vuetifyjs/comments/819h8u/how_to_close_a_multiple_autocomplete_vselect/
      this.$refs.fieldSelectAutoComplete.isFocused = false;
    });
  },
  methods: {
    updateData() {
      this.$emit('input', this.value);
      this.$emit('change', this.value);
      this.$forceUpdate();
    },
    selectedValueComparator(item1, item2){
      const item1Value = (item1 && item1.value) || item1;
      const item2Value = (item2 && item2.value) || item2;
      return item1Value === item2Value;
    },
  },
};
</script>