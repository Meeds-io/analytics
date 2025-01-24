<!--
  This file is part of the Meeds project (https://meeds.io/).

  Copyright (C) 2025 Meeds Association contact@meeds.io

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
  <span v-if="isUsertype">
    <user-avatar
      v-for="value in propertyValues"
      :key="value"
      :profile-id="value"
      size="24"
      :class="{'mb-1': isMultiValued}"
      popover
      link-style />
  </span>
  <span v-else-if="isDropdownList">
    <analytics.profile-dropdown-property-item-value
      v-for="value in propertyValues"
      :key="value"
      :property-name="propertyName"
      :property-option-value="value" />
  </span>
  <span v-else>
    {{ propertyValue }}
  </span>
</template>

<script>
export default {
  props: {
    propertyValue: {
      type: String,
      default: null
    },
    propertyKey: {
      type: String,
      default: null
    },
    settings: {
      type: Map,
      default: null
    }
  },
  computed: {
    setting() {
      return this.settings?.[this.propertyName];
    },
    propertyName() {
      return this.propertyKey.split('.')[1];
    },
    isMultiValued() {
      return this.propertyValue?.split(',')?.length > 1;
    },
    propertyValues() {
      return this.parsePropertyValue(this.propertyValue);
    },
    isUsertype() {
      return this.setting?.propertyType === 'user';
    },
    isDropdownList() {
      return this.setting?.dropdownList;
    }
  },
  methods: {
    parsePropertyValue(value) {
      if (!value || !(this.isUsertype || this.isDropdownList)) {
        return [value];
      }
      try {
        const parsedValue = JSON.parse(value);
        return Array.isArray(parsedValue) ? parsedValue : [parsedValue];
      } catch {
        return [value];
      }
    }
  }
};
</script>
