<!--
  This file is part of the Meeds project (https://meeds.io/).

  Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io

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
  <div v-if="isMultipleValues" class="d-flex flex-column">
    <analytics-category-sample-item-attribute
      v-for="id in values"
      :key="id"
      :attr-key="attrKey"
      :attr-value="id"
      :options="options"
      class="mb-2" />
  </div>
  <v-chip v-else>
    <div class="text-truncate">
      <v-icon
        v-if="icon"
        v-text="icon"
        class="me-2" />
      <div v-if="deleted" class="error--text d-flex">
        {{ $t('analytics.deletedCategory') }}
        <v-chip>{{ attrValue }}</v-chip>
      </div>
      <span v-else class="text-truncate">
        {{ name }}
      </span>
    </div>
  </v-chip>
</template>
<script>
export default {
  props: {
    attrKey: {
      type: Object,
      default: null,
    },
    attrValue: {
      type: Object,
      default: null,
    },
    options: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    name: null,
    icon: null,
    deleted: false,
    values: null,
  }),
  computed: {
    isMultipleValues() {
      return this.values?.length;
    },
  },
  created() {
    this.values = JSON.parse(this.attrValue);
    if (!this.isMultipleValues) {
      this.init();
    }
  },
  methods: {
    async init() {
      try {
        const translations = await fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/social/translations/category/${this.attrValue}/name`, {
          method: 'GET',
          credentials: 'include',
        }).then(resp => resp?.json?.());
        this.name = translations?.[eXo.env.portal.language]
          || translations?.[eXo.env.portal.defaultLanguage]
          || translations?.['en'];
        const category = await this.getCategory(this.attrValue);
        if (category) {
          this.icon = category.icon;
        } else {
          this.deleted = true;
        }
      } catch (e) {
        this.deleted = true;
      }
    },
    getCategory(id) {
      return fetch(`/social/rest/categories/${id}`, {
        method: 'GET',
        credentials: 'include',
      }).then(resp => {
        if (resp?.ok) {
          return resp.json();
        } else {
          throw new Error('Error when retrieving the category');
        }
      });
    },
  }
};
</script>