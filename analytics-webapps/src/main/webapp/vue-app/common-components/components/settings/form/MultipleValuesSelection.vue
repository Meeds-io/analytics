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
  <div>
    <v-btn
      link
      text
      class="primary--text"
      @click="$refs.multipleValuesDrawer.open()">
      {{ $t('analytics.openItemsList') }}
    </v-btn>
    <exo-drawer
      ref="multipleValuesDrawer"
      drawer-width="650px"
      right
      @closed="$emit('closed')">
      <template slot="content">
        <v-flex class="mx-0 drawerHeader flex-grow-0">
          <v-list-item
            class="pr-0"
            :ripple="false"
            :selectable="false"
            inactive>
            <v-list-item-action class="drawerIcons align-end d-flex flex-row">
              <v-btn icon>
                <v-icon @click="$refs.multipleValuesDrawer.close()">mdi-arrow-left</v-icon>
              </v-btn>
            </v-list-item-action>
            <v-list-item-content class="drawerTitle align-start text-header-title text-truncate">
              {{ fieldLabel }}
            </v-list-item-content>
          </v-list-item>
        </v-flex>
        <v-divider class="my-0" />
        <v-flex class="pa-5">
          <analytics-space-field-filter
            v-if="filterField === 'spaceId'"
            :filter="filter"
            :suggester-labels="suggesterLabels" />
          <analytics-user-field-filter
            v-else-if="filterField === 'userId'"
            :filter="filter"
            :suggester-labels="suggesterLabels" />
          <analytics-text-value-filter
            v-else-if="aggregation"
            :filter="filter"
            :suggester-labels="suggesterLabels" />
          <input
            v-else
            v-model="filter.valueString"
            type="text"
            :placeholder="placeholder"
            class="ignore-vuetify-classes width-auto pa-0 my-auto"
            required>
        </v-flex>
      </template>
    </exo-drawer>
  </div>
</template>

<script>
export default {
  props: {
    fieldLabel: {
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
    suggesterLabels: {
      type: Object,
      default: function() {
        return null;
      },
    },
    placeholder: {
      type: String,
      default: null,
    },
    aggregation: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    filterField() {
      return this.filter && this.filter.field;
    },
  },
};
</script>