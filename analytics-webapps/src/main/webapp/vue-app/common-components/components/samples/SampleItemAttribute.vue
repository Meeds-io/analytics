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
  <v-row>
    <v-col>{{ keyLabel }}</v-col>
    <v-col v-if="sampleItemExtension">
      <extension-registry-component
        :component="extendedSampleItemComponent"
        :params="extendedSampleItemComponentParams" />
    </v-col>
    <v-col v-else class="text--secondary">
      {{ valueLabel }}
    </v-col>
  </v-row>
</template>
<script>
export default {
  props: {
    attrKey: {
      type: Object,
      default: function() {
        return null;
      },
    },
    attrValue: {
      type: Object,
      default: function() {
        return null;
      },
    },
    sampleItemExtensions: {
      type: Object,
      default: function() {
        return {};
      },
    },
  },
  computed: {
    keyLabel() {
      const fieldLabelI18NKey = `analytics.field.label.${this.attrKey}`;
      return this.$te(fieldLabelI18NKey) ? this.$t(fieldLabelI18NKey) : this.attrKey;
    },
    valueLabel() {
      const fieldLabelI18NValue = `analytics.${this.attrValue}`;
      return this.$te(fieldLabelI18NValue) ? this.$t(fieldLabelI18NValue) : this.attrValue;
    },
    sampleItemExtension() {
      if (this.sampleItemExtensions) {
        return Object.values(this.sampleItemExtensions)
          .sort((ext1, ext2) => (ext1.rank || 0) - (ext2.rank || 0))
          .find(extension => extension.match && extension.match(this.attrKey)) || null;
      }
      return null;
    },
    extendedSampleItemComponent() {
      return this.sampleItemExtension && {
        componentName: 'sample-item',
        componentOptions: {
          vueComponent: this.sampleItemExtension?.vueComponent,
        },
      } || null;
    },
    extendedSampleItemComponentParams() {
      return this.sampleItemExtension && {
        attrKey: this.attrKey,
        attrValue: this.attrValue,
        options: this.sampleItemExtension?.options,
      } || null;
    },
  },
};
</script>