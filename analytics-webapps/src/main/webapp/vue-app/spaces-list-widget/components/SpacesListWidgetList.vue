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
      <span class="pe-2">{{ $t(labelKey) }}</span>
      <v-divider class="flex-grow-1" />
    </div>
    <spaces-list-widget-item
      v-for="id in list"
      :key="id"
      :space-id="id" />
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
    hasSpaces() {
      return this.list?.length;
    },
  },
};
</script>
