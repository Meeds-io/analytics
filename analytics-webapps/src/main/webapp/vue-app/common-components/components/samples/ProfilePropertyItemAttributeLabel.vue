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
  <v-chip
    v-if="chip"
    v-bind="chipAttrs"
    :input-value="selected"
    :title="propertyLabel"
    @click="$emit('select')">
    {{ propertyLabel }}
  </v-chip>
  <v-list-item-content
    v-else-if="filterSelection"
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
    },
    chip: {
      type: Boolean,
      default: false
    },
    selected: {
      type: String,
      default: null
    },
    chipAttrs: {
      type: Object,
      default: null
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
    propertyNameKey() {
      return this.propertyName?.replace?.('_alt', '');
    },
    hasLabel() {
      return this.$te(`analytics.field.label.${this.propertyNameKey}`);
    },
    propertyLabel() {
      return this.translatedLabel
          || (this.hasLabel && this.propertyName?.includes?.('_alt') && this.$t('analytics.field.alternative', {0: this.$t(`analytics.field.label.${this.propertyNameKey}`)}))
          || (this.hasLabel && this.$t(`analytics.field.label.${this.propertyNameKey}`))
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
