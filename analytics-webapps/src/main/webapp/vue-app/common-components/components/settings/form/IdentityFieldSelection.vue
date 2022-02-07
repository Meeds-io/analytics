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
  <div :id="id">
    <v-autocomplete
      ref="fieldSelectAutoComplete"
      v-model="value"
      :items="items"
      :label="label"
      :value-comparator="selectedValueComparator"
      :return-object="false"
      menu-props="closeOnClick, maxHeight = 100"
      item-text="label"
      item-value="name"
      return-value
      class="pt-0 pb-3"
      filled
      attach
      persistent-hint
      dense
      chips
      @input="updateData" />
  </div>
</template>

<script>
export default {
  props: {
    value: {
      type: String,
      default: function() {
        return null;
      },
    },
    items: {
      type: Array,
      default: function() {
        return [];
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
  },
  data () {
    return {
      id: `FieldSelection${parseInt(Math.random() * 10000)
        .toString()
        .toString()}`,
    };
  },
  mounted() {
    $(`#${this.id} input`).on('blur', () => {
      // A hack to close on select
      // See https://www.reddit.com/r/vuetifyjs/comments/819h8u/how_to_close_a_multiple_autocomplete_vselect/
      this.$refs.fieldSelectAutoComplete.isFocused = false;
    });
  },
  methods: {
    updateData(){
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