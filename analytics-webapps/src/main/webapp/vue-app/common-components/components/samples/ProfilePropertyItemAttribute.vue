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
  <div>
    <v-row>
      <v-col
        cols="6"
        class="d-flex flex-column justify-center">
        <analytics-profile-property-item-attribute-label
          :property="attrKey"
          :settings.sync="settings" />
      </v-col>
      <v-col
        cols="6"
        class="text--secondary">
        <analytics-profile-property-item-attribute-value
          :settings="settings"
          :property-key="attrKey"
          :property-value="attrValue" />
      </v-col>
    </v-row>
  </div>
</template>
<script>

export default {
  data() {
    return {
      lang: eXo.env.portal.language,
      translatedValue: null,
      settings: {}
    };
  },
  props: {
    attrKey: {
      type: Object,
      default: null,
    },
    attrValue: {
      type: Object,
      default: null,
    }
  },
  computed: {
    properties() {
      return JSON.parse(this.attrValue);
    }
  },
  created() {
    if (this.dropdownList) {
      this.getDropdownTranslation();
    }
  },
  methods: {
    getDropdownTranslation() {
      (async () => {
        if (this.multiValued) {
          this.translatedValue = await this.$analyticsUtils.getMultiValuePropertyOptionTranslations(this.attrValue, this.lang);
        } else {
          this.translatedValue = await this.$analyticsUtils.getPropertyOptionTranslatedValue(this.attrValue, this.lang);
        }
      })();
    }
  }
};
</script>
