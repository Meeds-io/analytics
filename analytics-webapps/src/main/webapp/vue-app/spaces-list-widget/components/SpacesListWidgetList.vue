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
  <v-list v-if="hasSpaces" class="pa-0">
    <div v-if="labelKey" class="d-flex text-body align-center justify-center px-0">
      <span class="pe-2">{{ label }}</span>
      <v-divider class="flex-grow-1" />
    </div>
    <spaces-list-widget-item
      v-for="item in list"
      :key="itemId(item)"
      :space-id="itemId(item)"
      :provided-space="itemSpace(item)" />
  </v-list>
</template>
<script>
export default {
  props: {
    list: {
      type: Array,
      default: null,
    },
    labelKey: {
      type: String,
      default: null,
    },
  },
  computed: {
    label() {
      return this.labelKey?.includes?.('_alt') ? this.$t('analytics.field.alternative', {0: this.$t(this.labelKey?.replace?.(/_alt\d*$/, '')?.replace?.('.keyword', ''))}) : this.$t(this.labelKey);
    },
    hasSpaces() {
      return this.list?.length;
    },
  },
  methods: {
    // The analytics sections hand this list space ids; the profile mode hands
    // it the space objects its endpoint already resolved, since a per-space
    // read could be refused for a listed space the viewer is not a member of
    itemId(item) {
      return typeof item === 'object' ? String(item.id) : item;
    },
    itemSpace(item) {
      return typeof item === 'object' ? item : null;
    },
  },
};
</script>
