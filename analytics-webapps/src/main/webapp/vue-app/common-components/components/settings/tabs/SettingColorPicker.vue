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
  <v-dialog
    ref="dialog"
    v-model="modal"
    color="white"
    width="290px"
    content-class="overflow-hidden">
    <template #activator="{ on }">
      <div class="themeColor">
        <div
          :style="{ backgroundColor: value}"
          class="colorSelected"
          v-on="on">
        </div>
        <div class="themeDetails">
          <label
            :title="$t('analytics.label.clickToChange')"
            :style="`background-color: ${value};`"
            class="pa-3 box-border-sizing border-radius white--text"
            v-on="on">{{ value }}</label>
        </div>
      </div>
    </template>
    <v-color-picker
      v-model="value"
      :swatches="swatches"
      show-swatches
      mode="hexa"
      dot-size="25"
      @update:color="$emit('updated', value)" />
    <v-row class="mx-0 white">
      <v-col class="center pt-0">
        <v-btn
          text
          color="primary"
          @click="cancel">
          Cancel
        </v-btn>
      </v-col>
      <v-col class="center pt-0">
        <v-btn
          text
          color="primary"
          @click="save">
          OK
        </v-btn>
      </v-col>
    </v-row>
  </v-dialog>
</template>
<script>
export default {
  props: {
    label: {
      type: String,
      default: function() {
        return null;
      },
    },
    value: {
      type: String,
      default: function() {
        return null;
      },
    },
  },
  data: () => ({
    modal: false,
    originalValue: null,
    swatches: [
      ['#ffaacc', '#f97575', '#98cc81'],
      ['#4273c8', '#cea6ac', '#bc99e7'],
      ['#9ee4f5', '#774ea9', '#ffa500'],
      ['#bed67e', '#0E100F', '#319ab3'],
    ],
  }),
  watch: {
    modal() {
      if (this.modal) {
        this.originalValue = this.value;
      }
    }
  },
  methods: {
    cancel() {
      this.value = this.originalValue;
      this.modal = false;
    },
    save() {
      this.$emit('input', this.value);
      this.modal = false;
    }
  }
};
</script>
