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
  <v-flex class="d-flex flex-column">
    <category-suggester
      v-model="categoryId"
      class="ma-0 pa-0"
      label=""
      access-permission
      single />
    <div v-if="multipleOperator && categories?.length" class="d-flex flex-column">
      <div
        v-for="(c, index) in categories"
        :key="c.id">
        <v-chip
          :title="c.name"
          color="primary"
          close
          class="mt-2"
          @click:close="remove(index)">
          <v-icon v-text="c.icon" class="me-2" />
          <span class="text-truncate">
            {{ c.name }}
          </span>
        </v-chip>
      </div>
    </div>
  </v-flex>
</template>
<script>
export default {
  props: {
    filter: {
      type: Object,
      default: function() {
        return null;
      },
    },
  },
  data: () => ({
    categoryId: null,
    categoryIds: null,
    categories: null,
  }),
  computed: {
    operatorType() {
      return this.filter && this.filter.type;
    },
    multipleOperator() {
      return this.operatorType === 'IN_SET' || this.operatorType === 'NOT_IN_SET';
    },
  },
  watch: {
    multipleOperator() {
      this.selectValue(this.categoryId);
    },
    categoryId() {
      this.selectValue(this.categoryId);
    },
  },
  created() {
    if (this.multipleOperator) {
      this.categoryIds = this.filter.valueString && this.filter.valueString.split(',') || [];
    }
    this.selectValue();
  },
  methods: {
    async selectValue(value) {
      if (this.multipleOperator) {
        if (value && !this.categoryIds.includes(value)) {
          this.categoryIds.push(value);
        }
        if (this.categoryIds.length !== this.categories?.length) {
          this.categories = await Promise.all(this.categoryIds.map(id => this.$categoryService.getCategory(id)));
        }
        this.categoryId = null;
        this.filter.valueString = this.categoryIds.join(',');
      } else {
        if (this.categoryId !== value) {
          this.categoryId = value;
        }
        this.filter.valueString = `${this.categoryId}`;
      }
    },
    remove(index) {
      if (index >= 0) {
        this.categories.splice(index, 1);
        this.categoryIds.splice(index, 1);
        this.filter.valueString = this.categoryIds.join(',');
      }
    },
  },
};
</script>