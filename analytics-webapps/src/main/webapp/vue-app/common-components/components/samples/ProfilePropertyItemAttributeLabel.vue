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
  <v-list-item-content
    v-if="filterSelection"
    v-text="propertyLabel" />
  <span v-else>
    {{ propertyLabel }}
  </span>
</template>

<script>

export default {
  data() {
    return {
      lang: eXo.env.portal.language,
      translatedLabel: null,
    };
  },
  props: {
    property: {
      type: String,
      default: null
    },
    settings: {
      type: Object,
      default: () => {
        return {};
      }
    },
    filterSelection: {
      type: Boolean,
      default: false
    }
  },
  watch: {
    settings() {
      this.$emit('update:settings', this.settings);
    }
  },
  computed: {
    propertyName() {
      return this.property?.split('.')?.[1];
    },
    hasLabel() {
      return this.$te(`analytics.field.label.${this.propertyName}`);
    },
    propertyLabel() {
      return this.hasLabel && this.$t(`analytics.field.label.${this.propertyName}`)
          || this.translatedLabel
          || this.propertyName;
    }
  },
  created() {
    this.fetchPropertyLabel();
  },
  methods: {
    async fetchPropertyLabel() {
      const setting = await this.$analyticsUtils.getProfilePropertySetting(this.propertyName);
      if (setting) {
        this.settings = {...this.settings, [setting.propertyName]: setting};
        this.translatedLabel = await this.$analyticsUtils.getProfilePropertyLabel(setting.id, this.lang);
      }
    }
  }
};
</script>
