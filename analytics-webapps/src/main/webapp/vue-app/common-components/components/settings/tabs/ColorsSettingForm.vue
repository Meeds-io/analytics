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
        v-for="(color, index) in chartColors"
        :key="index"
        class="mx-auto px-3 my-3"
        xs4>
        <analytics-setting-color-picker
          v-model="colors[index]"
          @input="setColor($event, index)" />
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
  data: () => ({
    DEFAULT_COLORS: [
      '#319ab3',
      '#f97575',
      '#98cc81',
      '#4273c8',
      '#cea6ac',
      '#bc99e7',
      '#9ee4f5',
      '#774ea9',
      '#ffa500',
      '#bed67e',
      '#0E100F',
      '#ffaacc',
    ],
    colors: [],
  }),
  computed: {
    multipleCharts() {
      return this.settings && (this.settings.multipleChartsField || this.settings.chartType === 'pie');
    },
    chartColors() {
      if (this.multipleCharts) {
        return this.colors  || [];
      } else {
        return this.colors && this.colors.slice(0, 1)  || [];
      }
    },
  },
  methods: {
    init() {
      this.colors = this.settings
        && this.settings.colors
        && this.settings.colors.length
        && this.settings.colors.slice()
        || this.DEFAULT_COLORS;
    },
    setColor(color, index) {
      this.colors.splice(index, 1, color);
      this.settings.colors = this.colors;
    },
  },
};
</script>