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
  <v-flex :id="id">
    <v-autocomplete
      ref="selectAutoComplete"
      v-model="value"
      :label="labels.label"
      :placeholder="labels.placeholder"
      :loading="loadingSuggestions > 0"
      :required="required && !value"
      :items="filteredItems"
      :search-input.sync="searchTerm"
      :filter="filterIgnoredItems"
      :hide-no-data="hideNoData"
      :class="autocompleteClass"
      :prepend-inner-icon="prependInnerIcon"
      append-icon=""
      menu-props="closeOnClick, maxHeight = 100"
      class="identitySuggester identitySuggesterInputStyle my-0"
      content-class="identitySuggesterContent"
      width="100%"
      max-width="100%"
      item-text="label"
      item-value="value"
      return-object
      persistent-hint
      hide-selected
      chips
      dense
      flat
      attach
      @update:search-input="searchTerm = $event"
      @focus="search">
      <template slot="no-data">
        <v-list-item class="pa-0">
          <v-list-item-title
            v-if="displaySearchPlaceHolder"
            :style="menuItemStyle"
            class="px-2">
            {{ labels.searchPlaceholder }}
          </v-list-item-title>
          <v-list-item-title
            v-else-if="labels.noDataLabel"
            :style="menuItemStyle"
            class="px-2">
            {{ labels.noDataLabel }}
          </v-list-item-title>
        </v-list-item>
      </template>
      <template slot="selection" slot-scope="{item, selected}">
        <v-chip
          v-if="item.value"
          :input-value="selected"
          :close="!disabled"
          class="identitySuggesterItem ml-0"
          @click:close="remove(item)">
          <span class="text-truncate">
            {{ item.label }}
          </span>
        </v-chip>
      </template>
      <template slot="item" slot-scope="data">
        <v-list-item-title
          :style="menuItemStyle"
          class="text-truncate"
          v-text="data.item.label" />
        <v-list-item-action class="ma-0 pe-2 text-no-wrap">
          <v-list-item-action-text>
            ({{ $t('analytics.itemsCount', {0: data.item.count || 0}) }})
          </v-list-item-action-text>
        </v-list-item-action>
      </template>
    </v-autocomplete>
  </v-flex>
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
    filter: {
      type: Object,
      default: function() {
        return null;
      },
    },
    labels: {
      type: Object,
      default: () => ({
        label: '',
        placeholder: '',
        searchPlaceholder: '',
        noDataLabel: '',
      }),
    },
  },
  data() {
    return {
      id: `AutoComplete${parseInt(Math.random() * 10000)
        .toString()
        .toString()}`,
      previousSearchTerm: null,
      searchTerm: null,
      searchStarted: null,
      items: [],
      limit: 20,
      pageSize: 20,
      searched: false,
      loadingSuggestions: 0,
    };
  },
  computed: {
    prependInnerIcon() {
      return this.filterStyle && 'fa-filter' || '';
    },
    filterField() {
      return this.filter && this.filter.field;
    },
    autocompleteClass() {
      return this.required && !this.value && 'required-field invalid' || this.required && 'required-field' || '';
    },
    displaySearchPlaceHolder() {
      return this.labels.searchPlaceholder && (!this.searchStarted || !this.value);
    },
    hideNoData() {
      return !this.labels.noDataLabel && !this.labels.searchPlaceholder;
    },
    menuItemStyle() {
      return this.width && `width:${this.width}px;max-width:${this.width}px;min-width:${this.width}px;` || '';
    },
    filteredItems() {
      if (this.searchTerm) {
        const searchTerm = this.searchTerm.toLowerCase();
        return this.items.filter(item => item.label.toLowerCase().indexOf(searchTerm) >= 0);
      } else {
        return this.items;
      }
    },
  },
  watch: {
    filterField() {
      this.searched = false;
      this.value = null;
      this.limit = this.pageSize;
      this.items = [];
      this.search();
    },
    loadingSuggestions() {
      if (this.loadingSuggestions > 0 && !this.searchStarted) {
        this.searchStarted = true;
      }
    },
    searchTerm(value) {
      this.search(value);
    },
    value() {
      this.emitSelectedValue(this.value);
      this.init();
    },
  },
  mounted() {
    $(`#${this.id} input`).on('blur', () => {
      // A hack to close on select
      // See https://www.reddit.com/r/vuetifyjs/comments/819h8u/how_to_close_a_multiple_autocomplete_vselect/
      this.$refs.selectAutoComplete.isFocused = false;
    });
    this.init();
    if (!this.filter.valueString) {
      this.search();
    }
  },
  methods: {
    init() {
      const values = this.value && ((this.value.value && [this.value]) || (this.value.split && this.value.split(',')) || (this.value.length && this.value)) || [];
      if (values && values.length) {
        values.forEach(value => {
          if (value) {
            this.items.push({
              value: value.value || value,
              count: value.count || 0,
              label: value.label || this.computeI18NLabel(value),
            });
          }
        });
      }
    },
    search(text) {
      if (!this.filterField) {
        this.items = [];
        return;
      }
      if (this.searched && (this.items.length < this.limit || this.filteredItems.length >= this.pageSize)) {
        return;
      }
      if (this.searched) {
        this.limit += this.pageSize;
      }
      window.setTimeout(() => {
        if (!this.previousSearchTerm || this.previousSearchTerm === this.searchTerm) {
          this.loadingSuggestions = 1;

          return fetch(this.$root.retrieveFieldValuesUrl, {
            method: 'POST',
            credentials: 'include',
            headers: {
              'Content-Type': 'application/x-www-form-urlencoded',
              'pragma': 'no-cache',
              'cache-control': 'no-cache',
            },
            body: $.param({
              field: this.filter.field,
              limit: this.limit,
            }),
          }).then((resp) => {
            if (resp && resp.ok) {
              return resp.json();
            } else {
              throw new Error('Error getting field values with name:', this.filter.field);
            }
          }).then((valueItems) => {
            if (valueItems && valueItems.length) {
              valueItems.forEach(valueItem => {
                if (!this.items.find(item => item.value === valueItem.value)) {
                  this.items.push({
                    value: valueItem.value,
                    count: valueItem.count || 0,
                    label: this.computeI18NLabel(valueItem.value),
                  });
                }
              });
            }
            this.searched = true;
            return this.search(text);
          }).finally(() => {
            this.searched = true;
            this.loadingSuggestions--;
          });
        }
        this.previousSearchTerm = this.searchTerm;
      }, 400);
    },
    computeI18NLabel(value) {
      const key = `analytics.${value}`;
      const i18NValue = this.$t(key);
      return i18NValue === key ? value : i18NValue;
    },
    emitSelectedValue(value) {
      this.$emit('input', value);
      this.searchTerm = null;
    },
    canAddItem(item) {
      return !item || !item.value || this.ignoreItems.indexOf(item.value) < 0;
    },
    filterIgnoredItems(item, queryText, itemText) {
      if (queryText && itemText.toLowerCase().indexOf(queryText.toLowerCase()) < 0) {
        return false;
      }
      if (this.ignoreItems && this.ignoreItems.length) {
        return this.canAddItem(item);
      }
      return true;
    },
    focus() {
      this.$refs.selectAutoComplete.focus();
    },
    clear() {
      this.value = null;
      this.searchTerm = null;
      this.$refs.selectAutoComplete.reset();
    },
    remove(item) {
      if (this.value) {
        if (this.value.splice) {
          const index = this.value.findIndex(val => val.value === item.value);
          if (index >= 0){
            this.value.splice(index, 1);
          }
        } else {
          this.value = null;
        }
      }
      this.$emit('removeValue',item);
    },
  },
};
</script>
