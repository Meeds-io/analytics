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
  <analytics-user-field-filter
    v-if="isUserProperty"
    :filter="filter"
    :suggester-labels="suggesterLabels"
    :username-provided="true" />
  <analytics-text-value-filter
    v-else
    :filter="filter"
    :suggester-labels="suggesterLabels"
    :is-profile-property-option="isDropdownList" />
</template>

<script>
export default {
  data() {
    return {
      setting: null,
      selectedOption: null,
    };
  },
  props: {
    filter: {
      type: Object,
      default: function() {
        return null;
      },
    },
    suggesterLabels: {
      type: Array,
      default: function() {
        return [];
      }
    }
  },
  computed: {
    propertyName() {
      return this.filter.field.split('.')[1];
    },
    isUserProperty() {
      return this.setting?.propertyType === 'user';
    },
    isDropdownList() {
      return this.setting?.dropdownList;
    }
  },
  created() {
    this.getPropertySetting();
  },
  methods: {
    async getPropertySetting() {
      this.setting = await this.$analyticsUtils.getProfilePropertySetting(this.propertyName);
    }
  }
};
</script>
