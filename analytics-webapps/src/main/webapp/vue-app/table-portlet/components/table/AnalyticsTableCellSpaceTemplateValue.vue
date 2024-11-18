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
  <div v-if="spaceTemplateNames" class="d-flex flex-column">
    <div
      v-for="(name, index) in spaceTemplateNames"
      :key="name"
      class="text-truncate">
      <template v-if="name">
        {{ name || '-' }}
        <v-chip v-if="spaceTemplateIds[index]">{{ spaceTemplateIds[index] }}</v-chip>
      </template>
    </div>
  </div>
  <div v-else>-</div>
</template>
<script>
export default {
  props: {
    value: {
      type: Object,
      default: function() {
        return null;
      },
    },
    item: {
      type: Object,
      default: function() {
        return null;
      },
    },
    labels: {
      type: Object,
      default: function() {
        return null;
      },
    },
  },
  data: () => ({
    loading: true,
    error: false,
    spaceTemplateIds: null,
    spaceTemplateNames: null,
  }),
  created() {
    let value = typeof this.value === 'object' ? this.value : JSON.parse(this.value);
    if ((!(typeof value === 'object') || !value?.length) && value && Number.isFinite(Number(value))) {
      value = [value];
    }
    if (value?.length) {
      this.spaceTemplateIds = value;
      this.spaceTemplateNames = [];
      this.spaceTemplateIds.forEach(async v => {
        if (!v || !Number(v) || !Number.isFinite(Number(v))) {
          this.spaceTemplateNames.push(null);
        } else {
          const translations = await fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/social/translations/spaceTemplate/${v}/name`, {
            method: 'GET',
            credentials: 'include',
          }).then(resp => resp?.json?.());
          this.spaceTemplateNames.push(translations?.[eXo.env.portal.language]
            || translations?.[eXo.env.portal.defaultLanguage]
            || translations?.['en']);
        }
      });
    }
  },
};
</script>
