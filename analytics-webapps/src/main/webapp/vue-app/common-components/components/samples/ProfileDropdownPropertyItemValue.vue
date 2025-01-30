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
  <span>
    {{ optionLabel }}
  </span>
</template>

<script>


export default {
  data() {
    return {
      lang: eXo.env.portal.language,
      translatedValue: null
    };
  },
  props: {
    propertyOptionValue: {
      type: String,
      default: null
    },
    propertyName: {
      type: String,
      default: null
    }
  },
  computed: {
    optionLabel() {
      return this.translatedValue || this.propertyOptionValue;
    },
    option() {
      return this.propertySetting?.propertyOptions.find(option => `${option.id}` === `${this.propertyOptionValue}`);
    }
  },
  created() {
    this.getPropertyOptionLabel();
  },
  methods: {
    async getPropertyOptionLabel() {
      this.translatedValue =  await this.$analyticsUtils.getPropertyOptionTranslatedValue(this.propertyOptionValue, this.lang);
    },
  }
};
</script>
